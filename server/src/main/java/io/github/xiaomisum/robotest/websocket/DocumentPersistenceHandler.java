package io.github.xiaomisum.robotest.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaomisum.robotest.model.entity.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

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
    public void persist(String docId, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.has("type") ? root.get("type").asText() : null;

            if ("update_layout".equals(type)) {
                persistLayout(docId, root);
            } else {
                persistNodeUpdate(docId, root);
            }
        } catch (Exception e) {
            log.error("Persist error for doc {}: {}", docId, e.getMessage(), e);
        }
    }

    private void persistLayout(String docId, JsonNode message) {
        JsonNode payload = message.has("payload") ? message.get("payload") : null;
        if (payload == null) {
            return;
        }

        String layoutJson = payload.toString();

        TestCaseDocumentLayout existing = testCaseDocumentLayoutMapper.selectOne(
                new LambdaQueryWrapper<TestCaseDocumentLayout>()
                        .eq(TestCaseDocumentLayout::getDocumentId, docId));

        if (existing != null) {
            existing.setLayoutJson(layoutJson);
            testCaseDocumentLayoutMapper.updateById(existing);
        } else {
            TestCaseDocumentLayout layout = new TestCaseDocumentLayout();
            layout.setId(UUID.randomUUID().toString());
            layout.setDocumentId(docId);
            layout.setLayoutJson(layoutJson);
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
            case "add_node" -> handleAddNode(docId, data);
            case "update_attrs" -> handleUpdateAttrs(data);
            case "delete_node" -> handleDeleteNode(data);
            case "move_node" -> handleMoveNode(data);
            default -> log.debug("Unknown op: {}", op);
        }
    }

    private void handleAddNode(String docId, JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        if (!StringUtils.hasText(nodeId)) {
            return;
        }

        TestCaseNode existing = testCaseNodeMapper.selectById(nodeId);
        if (existing != null) {
            return;
        }

        TestCaseNode node = new TestCaseNode();
        node.setId(nodeId);
        node.setDocumentId(docId);
        node.setParentId(data.has("parentId") ? data.get("parentId").asText(null) : null);
        node.setType(data.has("type") ? data.get("type").asText("normal") : "normal");
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

        TestCaseNode node = testCaseNodeMapper.selectById(nodeId);
        if (node == null) {
            return;
        }

        if (data.has("title")) {
            node.setTitle(data.get("title").asText());
        }
        if (data.has("type")) {
            node.setType(data.get("type").asText());
        }
        if (data.has("priority")) {
            node.setPriority(data.get("priority").asText(null));
        }
        if (data.has("sortOrder")) {
            node.setSortOrder(data.get("sortOrder").asInt());
        }
        node.setVersion(node.getVersion() + 1);
        testCaseNodeMapper.updateById(node);
    }

    private void handleDeleteNode(JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        if (!StringUtils.hasText(nodeId)) {
            return;
        }
        testCaseNodeMapper.deleteById(nodeId);
    }

    private void handleMoveNode(JsonNode data) {
        String nodeId = data.has("id") ? data.get("id").asText() : null;
        String newParentId = data.has("parentId") ? data.get("parentId").asText(null) : null;
        Integer sortOrder = data.has("sortOrder") ? data.get("sortOrder").asInt() : null;

        if (!StringUtils.hasText(nodeId)) {
            return;
        }

        TestCaseNode node = testCaseNodeMapper.selectById(nodeId);
        if (node == null) {
            return;
        }

        if (newParentId != null) {
            node.setParentId(newParentId);
        }
        if (sortOrder != null) {
            node.setSortOrder(sortOrder);
        }
        node.setVersion(node.getVersion() + 1);
        testCaseNodeMapper.updateById(node);
    }
}
