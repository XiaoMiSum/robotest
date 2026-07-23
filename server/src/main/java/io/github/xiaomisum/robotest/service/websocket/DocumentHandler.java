package io.github.xiaomisum.robotest.service.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import xyz.migoo.framework.websocket.core.MiGooWebSocketHandler;
import xyz.migoo.framework.websocket.core.WebSocketSessionManager;

import java.net.URI;

/**
 * 文档协作 WebSocket 处理器
 * <p>
 * 基于框架 {@link MiGooWebSocketHandler} 扩展，处理文档级别的实时协作。
 * 客户端通过 {@code /ws/documents/{docId}?token=xxx} 连接，框架自动完成 Token 认证。
 * <p>
 * 本处理器负责：
 * <ul>
 *   <li>从连接 URI 路径中提取文档 ID，加入对应房间</li>
 *   <li>将消息广播给同一房间内的其他用户（排除发送者）</li>
 *   <li>将消息委托给 {@link DocumentPersistenceHandler} 进行持久化</li>
 * </ul>
 */
@Slf4j
@Component
public class DocumentHandler extends MiGooWebSocketHandler {

    private static final String ATTR_DOC_ID = "docId";
    private static final String PREFIX = "/ws/documents/";

    private final DocumentPersistenceHandler persistenceHandler;

    public DocumentHandler(WebSocketSessionManager sessionManager,
                           DocumentPersistenceHandler persistenceHandler) {
        super(sessionManager);
        this.persistenceHandler = persistenceHandler;
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

        session.getAttributes().put(ATTR_DOC_ID, docId);
        joinRoom(session, docId);
        log.info("[afterConnectionEstablished][用户({}) 加入文档房间 {}]", getUserId(session), docId);
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
            persistenceHandler.persist(docId, message.getPayload(), session);
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
