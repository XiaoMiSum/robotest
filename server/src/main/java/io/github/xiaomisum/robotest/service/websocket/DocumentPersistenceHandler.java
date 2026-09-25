package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentAddNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentDeleteNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentMoveNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentUpdateAttrsReqDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("all")
public class DocumentPersistenceHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentPersistenceHandler.class);

    /**
     * WS 会话属性中用户 ID 的 key，由框架 {@code WebSocketAuthInterceptor} 握手时写入。
     * persist 为 @Async，SecurityContext 不会随线程池传递，故从会话属性取当前用户。
     */
    private static final String ATTR_USER_ID = "USER_ID";

    private static final String ERROR_CODE_PERMISSION_DENIED = "PERMISSION_DENIED";

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void persist(UUID docId, String message, WebSocketSession session) {
        try {
            JsonNode root = JsonUtils.toJSON(message);
            String type = root.path("type").asString();
            JsonNode payload = root.path("payload");

            // 后端兜底（US-AI-013 3.5.3）：仅具有编辑权限的用户可编辑。
            // WS 是长连接，权限可能在连接期间被撤销，故每次写操作前都重查一次（委托统一守卫）。
            String userId = session != null ? (String) session.getAttributes().get(ATTR_USER_ID) : null;
            if (!projectAccessGuard.hasDocumentEditPermission(docId, userId)) {
                log.warn("[persist][用户({}) 对文档({}) 无 case:edit 权限，拒绝持久化]", userId, docId);
                sendPermissionDenied(session);
                return;
            }

            switch (type) {
                case Constants.WebSocket.MSG_UPDATE_LAYOUT -> {
                    Map<String, Object> layout = JsonUtils.convert(payload, Map.class);
                    persistLayout(docId, layout);
                }
                case Constants.WebSocket.MSG_ADD_NODE ->
                        handleAddNode(docId, JsonUtils.toObject(payload.get("data"), DocumentAddNodeReqDTO.class));
                case Constants.WebSocket.MSG_UPDATE_ATTRS ->
                        handleUpdateAttrs(JsonUtils.toObject(payload.get("data"), DocumentUpdateAttrsReqDTO.class));
                case Constants.WebSocket.MSG_DELETE_NODE ->
                        handleDeleteNode(JsonUtils.toObject(payload.get("data"), DocumentDeleteNodeReqDTO.class));
                case Constants.WebSocket.MSG_MOVE_NODE ->
                        handleMoveNode(JsonUtils.toObject(payload.get("data"), DocumentMoveNodeReqDTO.class));
                default -> log.debug("Unknown type: {}", type);
            }
        } catch (Exception e) {
            log.error("Persist error for doc {}: {}", docId, e.getMessage(), e);
            sendError(session, "PERSIST_FAILED", "持久化失败: " + e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String code, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String errorJson = JsonUtils.toJsonString(
                    Map.of("type", Constants.WebSocket.MSG_TYPE_ERROR, "code", code, "message", message));
            synchronized (session) {
                session.sendMessage(new TextMessage(errorJson));
            }
        } catch (IOException e) {
            log.warn("Failed to send error to client: {}", e.getMessage());
        }
    }

    /**
     * 向发送者回写权限拒绝帧。转发前校验失败（SEC-004）与持久化拒绝共用同一错误帧，
     * 保证客户端对"被拒绝"的感知一致（错误码局部字符串、统一信封迁移见 78 号 DOC-005）。
     */
    public void sendPermissionDenied(WebSocketSession session) {
        sendError(session, ERROR_CODE_PERMISSION_DENIED, "无文档编辑权限");
    }

    private void persistLayout(UUID docId, Map<String, Object> layout) {
        testCaseDocumentMapper.updateLayout(docId, layout);
    }

    private void handleAddNode(UUID docId, DocumentAddNodeReqDTO data) {
        if (!StringUtils.hasText(data.getId())) {
            return;
        }

        UUID nodeId = UUID.fromString(data.getId());
        if (testCaseNodeMapper.selectById(nodeId) != null) {
            return;
        }

        TestCaseNode node = new TestCaseNode();
        node.setId(nodeId);
        node.setDocumentId(docId);
        node.setParentId(StringUtils.hasText(data.getParentId()) ? UUID.fromString(data.getParentId()) : null);
        node.setType(StringUtils.hasText(data.getType()) ? data.getType() : Constants.NodeType.NORMAL);
        node.setTitle(data.getTitle() != null ? data.getTitle() : "");
        node.setPriority(data.getPriority());
        node.setSortOrder(data.getSortOrder() != null ? data.getSortOrder() : 0);
        node.setAiGenerated(Boolean.TRUE.equals(data.getAiGenerated()));
        node.setVersion(1);
        testCaseNodeMapper.insert(node);
    }

    private void handleUpdateAttrs(DocumentUpdateAttrsReqDTO data) {
        if (!StringUtils.hasText(data.getId())) {
            return;
        }

        UUID nodeId = UUID.fromString(data.getId());
        TestCaseNode node = testCaseNodeMapper.selectById(nodeId);
        if (node == null) {
            return;
        }

        int currentVersion = node.getVersion() != null ? node.getVersion() : 0;

        int rows = testCaseNodeMapper.updateAttrsWithVersion(nodeId, currentVersion,
                data.getTitle(), data.getType(), data.getPriority(), data.getSortOrder(), data.getAiGenerated());
        if (rows == 0) {
            log.warn("Optimistic lock conflict for node {}, expected version {}", data.getId(), currentVersion);
        }
    }

    private void handleDeleteNode(DocumentDeleteNodeReqDTO data) {
        if (!StringUtils.hasText(data.getId())) {
            return;
        }
        List<String> toDelete = new ArrayList<>();
        collectDescendants(data.getId(), toDelete);
        toDelete.add(data.getId());
        testCaseNodeMapper.deleteByNodeIds(toDelete.stream().map(UUID::fromString).collect(Collectors.toList()));
    }

    private void collectDescendants(String parentId, List<String> result) {
        List<TestCaseNode> children = testCaseNodeMapper.listByParentId(UUID.fromString(parentId));
        for (TestCaseNode child : children) {
            result.add(child.getId().toString());
            collectDescendants(child.getId().toString(), result);
        }
    }

    private void handleMoveNode(DocumentMoveNodeReqDTO data) {
        if (!StringUtils.hasText(data.getId())) {
            return;
        }

        UUID nodeId = UUID.fromString(data.getId());
        TestCaseNode node = testCaseNodeMapper.selectById(nodeId);
        if (node == null) {
            return;
        }

        int currentVersion = node.getVersion() != null ? node.getVersion() : 0;

        UUID parentId = StringUtils.hasText(data.getParentId()) ? UUID.fromString(data.getParentId()) : null;
        int rows = testCaseNodeMapper.moveNodeWithVersion(nodeId, currentVersion, parentId, data.getSortOrder());
        if (rows == 0) {
            log.warn("Optimistic lock conflict on move for node {}, expected version {}", data.getId(), currentVersion);
        }
    }
}
