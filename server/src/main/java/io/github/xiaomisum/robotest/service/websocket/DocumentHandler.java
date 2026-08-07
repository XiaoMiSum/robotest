package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import xyz.migoo.framework.websocket.core.MiGooWebSocketHandler;
import xyz.migoo.framework.websocket.core.WebSocketSessionManager;

import java.net.URI;
import java.util.UUID;

/**
 * 文档协作 WebSocket 处理器
 * <p>
 * 基于框架 {@link MiGooWebSocketHandler} 扩展，处理文档级别的实时协作。
 * 客户端通过 {@code /ws/documents/{docId}?token=xxx} 连接，框架自动完成 Token 认证。
 * <p>
 * 本处理器负责：
 * <ul>
 *   <li>从连接 URI 路径中提取文档 ID，加入对应房间</li>
 *   <li>二进制帧（y-websocket 的 Yjs sync/awareness 协议）：转发给同房间其他用户</li>
 *   <li>文本帧（JSON 操作协议）：广播给同房间其他用户并委托 {@link DocumentPersistenceHandler} 持久化</li>
 * </ul>
 */
@Slf4j
@Component
public class DocumentHandler extends MiGooWebSocketHandler {

    private static final String ATTR_DOC_ID = "docId";
    private static final String PREFIX = "/ws/documents/";

    /**
     * 空 awareness 帧（messageAwareness + 长度 1 + 0 个状态变更）。
     * y-websocket 客户端超过 30 秒收不到任何入站消息会判定连接假死并断开重连，
     * 而本服务端二进制帧仅转发给房间内其他成员，单人编辑时客户端永远收不到消息，
     * 造成每 ~30 秒一轮的断连重连循环。客户端每 ≤15 秒主动发送 awareness 心跳，
     * 借此向发送者回一个客户端解码零副作用的合法帧即可保活。
     */
    private static final byte[] EMPTY_AWARENESS_FRAME = {0x01, 0x01, 0x00};

    private final DocumentPersistenceHandler persistenceHandler;
    private final ProjectAccessGuard projectAccessGuard;

    public DocumentHandler(WebSocketSessionManager sessionManager,
                           DocumentPersistenceHandler persistenceHandler,
                           ProjectAccessGuard projectAccessGuard) {
        super(sessionManager);
        this.persistenceHandler = persistenceHandler;
        this.projectAccessGuard = projectAccessGuard;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        super.afterConnectionEstablished(session);

        String docId = extractDocId(session);
        if (docId == null) {
            log.warn("[afterConnectionEstablished][会话ID({}) 无法从 URI 提取 docId，拒绝加入房间]",
                    session.getId());
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing docId in URI path"));
            } catch (Exception e) {
                log.error("[afterConnectionEstablished][关闭会话失败]", e);
            }
            return;
        }

        // 文档级鉴权：非项目工作空间成员禁止建立协作连接（判定口径与 REST 守卫一致）
        if (!projectAccessGuard.isDocumentMember(UUID.fromString(docId), getUserId(session))) {
            log.warn("[afterConnectionEstablished][用户({}) 无文档 {} 访问权限，拒绝加入房间]",
                    getUserId(session), docId);
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("No permission to access document"));
            } catch (Exception e) {
                log.error("[afterConnectionEstablished][关闭会话失败]", e);
            }
            return;
        }

        session.getAttributes().put(ATTR_DOC_ID, docId);
        joinRoom(session, docId);
        log.info("[afterConnectionEstablished][用户({}) 加入文档房间 {}]", getUserId(session), docId);
    }

    /**
     * y-websocket 客户端全部走二进制帧，服务端不解码 Yjs 内容：
     * 冲突由客户端 CRDT 合并，纯转发即可让 SyncStep1/SyncStep2 在成员间收敛
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String docId = (String) session.getAttributes().get(ATTR_DOC_ID);
        if (docId == null) {
            log.warn("[handleBinaryMessage][会话ID({}) 不在任何房间中]", session.getId());
            return;
        }

        String userId = getUserId(session);
        if (userId != null) {
            sendBinaryToRoomExcept(docId, userId, message.getPayload().array());
        }
        // 回帧保活，见 EMPTY_AWARENESS_FRAME 说明
        sendBinaryMessage(session, EMPTY_AWARENESS_FRAME);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String docId = (String) session.getAttributes().get(ATTR_DOC_ID);
        if (docId == null) {
            log.warn("[handleTextMessage][会话ID({}) 不在任何房间中]", session.getId());
            return;
        }

        // 广播给房间内其他用户（排除发送者）
        String userId = getUserId(session);
        if (userId != null) {
            sendToRoomExcept(docId, userId, message.getPayload());
        }

        // 委托持久化
        try {
            UUID docIdUuid = UUID.fromString(docId);
            persistenceHandler.persist(docIdUuid, message.getPayload(), session);
        } catch (Exception e) {
            log.error("[handleTextMessage][文档 {} 持久化失败: {}]", docId, e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
        String docId = (String) session.getAttributes().get(ATTR_DOC_ID);
        if (docId != null) {
            leaveRoom(session, docId);
            log.info("[afterConnectionClosed][用户({}) 离开文档房间 {}]", getUserId(session), docId);
        }
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String docId = (String) session.getAttributes().get(ATTR_DOC_ID);
        log.error("[handleTransportError][用户({}) 文档 {} 传输错误: {}]",
                getUserId(session), docId, exception.getMessage());
        if (docId != null) {
            leaveRoom(session, docId);
        }
        super.handleTransportError(session, exception);
    }

    /**
     * 从 WebSocket 连接 URI 路径中提取 docId
     * <p>
     * URL 格式：/ws/documents/{docId}?token=xxx
     */
    private String extractDocId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(PREFIX)) {
            return null;
        }
        String docId = path.substring(PREFIX.length());
        // 去除可能的尾部斜杠
        if (docId.endsWith("/")) {
            docId = docId.substring(0, docId.length() - 1);
        }
        return docId.isEmpty() ? null : docId;
    }
}
