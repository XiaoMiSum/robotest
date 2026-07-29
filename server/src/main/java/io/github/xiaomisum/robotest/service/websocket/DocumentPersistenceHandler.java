package io.github.xiaomisum.robotest.service.websocket;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentAddNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentDeleteNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentMoveNodeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentUpdateAttrsReqDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentLayoutMapper;
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
import tools.jackson.databind.ObjectMapper;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void persist(UUID docId, String message, WebSocketSession session) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.path("type").asString();
            JsonNode payload = root.path("payload");

            switch (type) {
                case Constants.WebSocket.MSG_UPDATE_LAYOUT -> {
                    Map<String, Object> layout = objectMapper.convertValue(payload, Map.class);
                    persistLayout(docId, layout);
                }
                case Constants.WebSocket.MSG_ADD_NODE ->
                        handleAddNode(docId, objectMapper.treeToValue(payload.get("data"), DocumentAddNodeReqDTO.class));
                case Constants.WebSocket.MSG_UPDATE_ATTRS ->
                        handleUpdateAttrs(objectMapper.treeToValue(payload.get("data"), DocumentUpdateAttrsReqDTO.class));
                case Constants.WebSocket.MSG_DELETE_NODE ->
                        handleDeleteNode(objectMapper.treeToValue(payload.get("data"), DocumentDeleteNodeReqDTO.class));
                case Constants.WebSocket.MSG_MOVE_NODE ->
                        handleMoveNode(objectMapper.treeToValue(payload.get("data"), DocumentMoveNodeReqDTO.class));
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
            String errorJson = objectMapper.writeValueAsString(
                    Map.of("type", Constants.WebSocket.MSG_TYPE_ERROR, "code", code, "message", message));
            synchronized (session) {
                session.sendMessage(new TextMessage(errorJson));
            }
        } catch (IOException e) {
            log.warn("Failed to send error to client: {}", e.getMessage());
        }
    }

    private void persistLayout(UUID docId, Map<String, Object> layout) {
        TestCaseDocumentLayout existing = testCaseDocumentLayoutMapper.selectOne(
                new LambdaQueryWrapperX<TestCaseDocumentLayout>()
                        .eq(TestCaseDocumentLayout::getDocumentId, docId));

        if (existing != null) {
            // 更新载体只携带布局字段，避免全列覆盖导致并发丢失更新
            TestCaseDocumentLayout update = new TestCaseDocumentLayout();
            update.setId(existing.getId());
            update.setLayoutJson(layout);
            testCaseDocumentLayoutMapper.updateById(update);
        } else {
            TestCaseDocumentLayout entity = new TestCaseDocumentLayout();
            entity.setDocumentId(docId);
            entity.setLayoutJson(layout);
            testCaseDocumentLayoutMapper.insert(entity);
        }
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
        int newVersion = currentVersion + 1;

        var updateWrapper = new LambdaUpdateWrapperX<TestCaseNode>()
                .set(TestCaseNode::getVersion, newVersion)
                .eq(TestCaseNode::getId, nodeId)
                .eq(TestCaseNode::getVersion, currentVersion);

        if (data.getTitle() != null) {
            updateWrapper.set(TestCaseNode::getTitle, data.getTitle());
        }
        if (data.getType() != null) {
            updateWrapper.set(TestCaseNode::getType, data.getType());
        }
        if (data.getPriority() != null) {
            updateWrapper.set(TestCaseNode::getPriority, data.getPriority());
        }
        if (data.getSortOrder() != null) {
            updateWrapper.set(TestCaseNode::getSortOrder, data.getSortOrder());
        }

        int rows = testCaseNodeMapper.update(null, updateWrapper);
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
        testCaseNodeMapper.delete(
                new LambdaQueryWrapperX<TestCaseNode>()
                        .in(TestCaseNode::getId, toDelete.stream().map(UUID::fromString).collect(Collectors.toList())));
    }

    private void collectDescendants(String parentId, List<String> result) {
        List<TestCaseNode> children = testCaseNodeMapper.selectList(
                new LambdaQueryWrapperX<TestCaseNode>()
                        .eq(TestCaseNode::getParentId, UUID.fromString(parentId)));
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
        int newVersion = currentVersion + 1;

        var updateWrapper = new LambdaUpdateWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getId, nodeId)
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, newVersion);

        if (StringUtils.hasText(data.getParentId())) {
            updateWrapper.set(TestCaseNode::getParentId, UUID.fromString(data.getParentId()));
        }
        if (data.getSortOrder() != null) {
            updateWrapper.set(TestCaseNode::getSortOrder, data.getSortOrder());
        }

        int rows = testCaseNodeMapper.update(null, updateWrapper);
        if (rows == 0) {
            log.warn("Optimistic lock conflict on move for node {}, expected version {}", data.getId(), currentVersion);
        }
    }
}
