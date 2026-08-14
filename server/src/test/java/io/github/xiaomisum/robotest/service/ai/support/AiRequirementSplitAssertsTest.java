package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiRequirementSplitRespDTO;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator.OutputValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiRequirementSplitAsserts 单元测试（US-AI-019，3.2.3）：
 * modules/items 数量上限与超长字段宽容规整。
 */
class AiRequirementSplitAssertsTest {

    private AiRequirementSplitRespDTO.Module module(String name, int itemCount) {
        AiRequirementSplitRespDTO.Module m = new AiRequirementSplitRespDTO.Module();
        m.setModule(name);
        List<AiRequirementSplitRespDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            AiRequirementSplitRespDTO.Item item = new AiRequirementSplitRespDTO.Item();
            item.setTitle("需求点 " + i);
            item.setContent("需求内容 " + i);
            items.add(item);
        }
        m.setItems(items);
        return m;
    }

    @Test
    void nullModules_throws() {
        assertThrows(OutputValidationException.class,
                () -> AiRequirementSplitAsserts.normalizeAndAssertModules(null));
    }

    @Test
    void emptyModules_throws() {
        assertThrows(OutputValidationException.class,
                () -> AiRequirementSplitAsserts.normalizeAndAssertModules(List.of()));
    }

    @Test
    void overMaxModules_throws() {
        List<AiRequirementSplitRespDTO.Module> modules = new ArrayList<>();
        for (int i = 0; i < AiRequirementSplitAsserts.MAX_MODULES + 1; i++) {
            modules.add(module("模块" + i, 1));
        }
        assertThrows(OutputValidationException.class,
                () -> AiRequirementSplitAsserts.normalizeAndAssertModules(modules));
    }

    @Test
    void emptyItems_throws() {
        AiRequirementSplitRespDTO.Module m = new AiRequirementSplitRespDTO.Module();
        m.setModule("用户管理");
        m.setItems(List.of());
        assertThrows(OutputValidationException.class,
                () -> AiRequirementSplitAsserts.normalizeAndAssertModules(List.of(m)));
    }

    @Test
    void overMaxItemsPerModule_throws() {
        List<AiRequirementSplitRespDTO.Module> modules = List.of(
                module("用户管理", AiRequirementSplitAsserts.MAX_ITEMS_PER_MODULE + 1));
        assertThrows(OutputValidationException.class,
                () -> AiRequirementSplitAsserts.normalizeAndAssertModules(modules));
    }

    @Test
    void overlongModuleName_truncatedWithWarning() {
        String name = "模".repeat(AiRequirementSplitAsserts.MODULE_MAX_LENGTH + 5);
        List<AiRequirementSplitRespDTO.Module> modules = List.of(module(name, 1));
        List<String> warnings = AiRequirementSplitAsserts.normalizeAndAssertModules(modules);
        assertEquals(AiRequirementSplitAsserts.MODULE_MAX_LENGTH, modules.get(0).getModule().length());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("模块名超长已截断")));
    }

    @Test
    void overlongTitle_truncatedWithWarning() {
        AiRequirementSplitRespDTO.Item item = new AiRequirementSplitRespDTO.Item();
        item.setTitle("长".repeat(AiRequirementSplitAsserts.TITLE_MAX_LENGTH + 10));
        item.setContent("内容");
        AiRequirementSplitRespDTO.Module m = new AiRequirementSplitRespDTO.Module();
        m.setModule("用户管理");
        m.setItems(List.of(item));
        List<String> warnings = AiRequirementSplitAsserts.normalizeAndAssertModules(List.of(m));
        assertEquals(AiRequirementSplitAsserts.TITLE_MAX_LENGTH, m.getItems().get(0).getTitle().length());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("需求点标题超长已截断")));
    }

    @Test
    void validStructure_noWarnings() {
        List<AiRequirementSplitRespDTO.Module> modules = List.of(module("用户管理", 2), module("订单", 1));
        List<String> warnings = AiRequirementSplitAsserts.normalizeAndAssertModules(modules);
        assertTrue(warnings.isEmpty());
    }
}
