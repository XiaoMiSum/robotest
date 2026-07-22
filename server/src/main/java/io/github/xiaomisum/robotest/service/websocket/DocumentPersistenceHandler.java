package io.github.xiaomisum.robotest.service.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.repository.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentPersistenceHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentPersistenceHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void persist(String docId, String message, Session session) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.has("type") ? root.get("type").asText() : null;

            if (Constants.WebSocket.MSG_UPDATE_LAYOUT.equals(type)) {
                persistLayout(docId, root);
            } else {
                persistNodeUpdate(docId, root);
            }
        } catch (Exception e) {
            log.error("Persist error for doc {}: {}", docId, e.getMessage(), e);
            sendError(session, Constants.WebSocket.ERROR_PERSIST_FAILED, "持久化失败: " + e.getMessage());
        }
    }

    private void sendError(Session session, String code, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String errorJson = objectMapper.writeValueAsString(
                    Map.of("type", Constants.WebSocket.MSG_TYPE_ERROR, "code", code, "message", message));
            session.getBasicRemote().sendText(errorJson);
        } catch (IOException e) {
            log.warn("Failed to send error to client: {}", e.getMessage());
        }
    }

    private void persistLayout(String docId, JsonNode message) {
        JsonNode payload = message.has("payload") ? message.get("payload") : null;
        if (payload == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> layoutMap = objectMapper.convertValue(payload, Map.class);

        TestCaseDocumentLayout existing = testCaseDocumentLayoutMapper.selectOne(
                new LambdaQueryWrapper<TestCaseDocumentLayout>()
                        .eq(TestCaseDocumentLayout::getDocumentId, docId));

        if (existing != null) {
            existing.setLayoutJson(layoutMap);
            testCaseDocumentLayoutMapper.updateById(existing);
        } else {
            TestCaseDocumentLayout layout = new TestCaseDocumentLayout();
            layout.setDocumentId(docId);
            layout.setLayoutJson(layoutMap);
            testCaseDocumentLayoutMapper.insert(layout);
        }
    }

    private void persistNodeUpdate(String docId, JsonNode message) {
        JsonNode payload = message.has("payload") ? message.get("payload") : null;
        if (payload == null) {
            return;
        }

        String op = payload.has("op") ? payload.get("op").asText() : null;
        JsonNode data = payload.has("data") ? payload.get("data") : null;

        if (!StringUtils.hasText(op) || data == null) {
            return;
        }

        switch (op) {
            case Constants.WebSocket.MSG_ADD_NODE -> handleAddNode(docId, data);
            case Constants.WebSocket.MSG_UPDATE_ATTRS -> handleUpdateAttrs(data);
            case Constants.WebSocket.MSG_DELETE_NODE -> handleDeleteNode(data);
            case Constants.WebSocket.MSG_MOVE_NODE -> handleMoveNode(data);
            default -> log.debug("Unknown op: {}", op);
        }
    }

    private void handleAddNode(String docId, JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        if (!StringUtils.hasText(nodeId)) {
            return;
        }

        TestCaseNode existing = testCaseNodeMapper.selectById(UUID.fromString(nodeId));
        if (existing != null) {
            return;
        }

        TestCaseNode node = new TestCaseNode();
        node.setId(UUID.fromString(nodeId));
        node.setDocumentId(docId);
        node.setParentId(data.has("parentId") ? data.get("parentId").asText(null) : null);
        node.setType(data.has("type") ? data.get("type").asText(Constants.NodeType.NORMAL) : Constants.NodeType.NORMAL);
        node.setTitle(data.has("title") ? data.get("title").asText("") : "");
        node.setPriority(data.has("priority") ? data.get("priority").asText(null) : null);
        node.setSortOrder(data.has("sortOrder") ? data.get("sortOrder").asInt(0) : 0);
        node.setVersion(1);
        testCaseNodeMapper.insert(node);
    }

    private void handleUpdateAttrs(JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        if (!StringUtils.hasText(nodeId)) {
            return;
        }

        TestCaseNode node = testCaseNodeMapper.selectById(UUID.fromString(nodeId));
        if (node == null) {
            return;
        }

        int currentVersion = node.getVersion() != null ? node.getVersion() : 0;
        int newVersion = currentVersion + 1;

        LambdaUpdateWrapper<TestCaseNode> updateWrapper = new LambdaUpdateWrapper<TestCaseNode>()
                .eq(TestCaseNode::getId, UUID.fromString(nodeId))
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, newVersion);

        if (data.has("title")) {
            updateWrapper.set(TestCaseNode::getTitle, data.get("title").asText());
        }
        if (data.has("type")) {
            updateWrapper.set(TestCaseNode::getType, data.get("type").asText());
        }
        if (data.has("priority")) {
            updateWrapper.set(TestCaseNode::getPriority, data.get("priority").asText(null));
        }
        if (data.has("sortOrder")) {
            updateWrapper.set(TestCaseNode::getSortOrder, data.get("sortOrder").asInt());
        }

        int rows = testCaseNodeMapper.update(null, updateWrapper);
        if (rows == 0) {
            log.warn("Optimistic lock conflict for node {}, expected version {}", nodeId, currentVersion);
        }
    }

    private void handleDeleteNode(JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        if (!StringUtils.hasText(nodeId)) {
            return;
        }
        List<String> toDelete = new ArrayList<>();
        collectDescendants(nodeId, toDelete);
        toDelete.add(nodeId);
        testCaseNodeMapper.deleteBatchIds(toDelete.stream().map(UUID::fromString).collect(Collectors.toList()));
    }

    private void collectDescendants(String parentId, List<String> result) {
        List<TestCaseNode> children = testCaseNodeMapper.selectList(
                new LambdaQueryWrapper<TestCaseNode>()
                        .eq(TestCaseNode::getParentId, UUID.fromString(parentId)));
        for (TestCaseNode child : children) {
            result.add(child.getId().toString());
            collectDescendants(child.getId().toString(), result);
        }
    }

    private void handleMoveNode(JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        String newParentId = data.has("parentId") ? data.get("parentId").asText(null) : null;
        Integer sortOrder = data.has("sortOrder") ? data.get("sortOrder").asInt() : null;

        if (!StringUtils.hasText(nodeId)) {
            return;
        }

        TestCaseNode node = testCaseNodeMapper.selectById(UUID.fromString(nodeId));
        if (node == null) {
            return;
        }

        int currentVersion = node.getVersion() != null ? node.getVersion() : 0;
        int newVersion = currentVersion + 1;

        LambdaUpdateWrapper<TestCaseNode> updateWrapper = new LambdaUpdateWrapper<TestCaseNode>()
                .eq(TestCaseNode::getId, UUID.fromString(nodeId))
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, newVersion);

        if (newParentId != null) {
            updateWrapper.set(TestCaseNode::getParentId, UUID.fromString(newParentId));
        }
        if (sortOrder != null) {
            updateWrapper.set(TestCaseNode::getSortOrder, sortOrder);
        }

        int rows = testCaseNodeMapper.update(null, updateWrapper);
        if (rows == 0) {
            log.warn("Optimistic lock conflict on move for node {}, expected version {}", nodeId, currentVersion);
        }
    }
}
