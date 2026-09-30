package io.github.xiaomisum.robotest.service.ai.assistant;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写工具定义测试：验证 create_bug / create_plan_draft / create_document / create_module
 * 四项写工具的 AiToolDefinition 元数据正确（名称、读/写属性、参数 Schema）。
 */
class WriteToolDefinitionTest {

    @Test
    void createBug_definition() {
        CreateBugTool tool = new CreateBugTool();
        AiToolDefinition def = tool.definition();

        assertEquals("create_bug", def.name());
        assertFalse(def.readOnly());
        Map<String, Object> properties = (Map<String, Object>) def.paramsSchema().get("properties");
        assertTrue(properties.containsKey("projectId"));
        assertTrue(properties.containsKey("title"));
        assertTrue(properties.containsKey("severity"));
        assertTrue(properties.containsKey("priority"));
    }

    @Test
    void createPlanDraft_definition() {
        CreatePlanDraftTool tool = new CreatePlanDraftTool();
        AiToolDefinition def = tool.definition();

        assertEquals("create_plan_draft", def.name());
        assertFalse(def.readOnly());
        Map<String, Object> properties = (Map<String, Object>) def.paramsSchema().get("properties");
        assertTrue(properties.containsKey("projectId"));
        assertTrue(properties.containsKey("name"));
    }

    @Test
    void createDocument_definition() {
        CreateDocumentTool tool = new CreateDocumentTool();
        AiToolDefinition def = tool.definition();

        assertEquals("create_document", def.name());
        assertFalse(def.readOnly());
        Map<String, Object> properties = (Map<String, Object>) def.paramsSchema().get("properties");
        assertTrue(properties.containsKey("projectId"));
        assertTrue(properties.containsKey("documentName"));
        assertTrue(properties.containsKey("moduleName"));
        assertTrue(properties.containsKey("caseNodes"));

        // caseNodes 为 array 类型，items 为 object
        Map<?, ?> caseNodesSchema = (Map<?, ?>) properties.get("caseNodes");
        assertEquals("array", caseNodesSchema.get("type"));
        Map<?, ?> items = (Map<?, ?>) caseNodesSchema.get("items");
        assertNotNull(items);
        assertEquals("object", items.get("type"));
        Map<?, ?> itemProperties = (Map<?, ?>) items.get("properties");
        assertTrue(itemProperties.containsKey("title"));
        assertTrue(itemProperties.containsKey("priority"));
    }

    @Test
    void createModule_definition() {
        CreateModuleTool tool = new CreateModuleTool();
        AiToolDefinition def = tool.definition();

        assertEquals("create_module", def.name());
        assertFalse(def.readOnly());
        Map<String, Object> properties = (Map<String, Object>) def.paramsSchema().get("properties");
        assertTrue(properties.containsKey("projectId"));
        assertTrue(properties.containsKey("moduleName"));
        assertTrue(properties.containsKey("parentModuleName"));
    }
}
