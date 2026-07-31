package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.service.ai.AiOutputValidator.OutputValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiNodeTreeAssertsTest {

    private AiNodeTreeDTO node(String type, String title, String priority, AiNodeTreeDTO... children) {
        AiNodeTreeDTO dto = new AiNodeTreeDTO();
        dto.setType(type);
        dto.setTitle(title);
        dto.setPriority(priority);
        dto.setChildren(List.of(children));
        return dto;
    }

    private AiNodeTreeDTO validCase(String title) {
        return node("case", title, "P1",
                node("precondition", "已登录", null),
                node("step", "点击提交", null),
                node("expected", "提交成功", null));
    }

    @Test
    void validTree_passesWithoutWarnings() {
        List<AiNodeTreeDTO> nodes = List.of(node("normal", "登录模块", null, validCase("邮箱登录成功")));
        assertTrue(AiNodeTreeAsserts.normalizeAndAssertTree(nodes).isEmpty());
    }

    @Test
    void nullChildren_normalizedToEmpty() {
        AiNodeTreeDTO dto = new AiNodeTreeDTO();
        dto.setType("case");
        dto.setTitle("无子节点用例");
        AiNodeTreeAsserts.normalizeAndAssertTree(List.of(dto));
        assertTrue(dto.getChildren().isEmpty());
    }

    @Test
    void overlongTitle_truncatedWithWarning_notFailure() {
        AiNodeTreeDTO dto = node("case", "超".repeat(250), "P2");
        List<String> warnings = AiNodeTreeAsserts.normalizeAndAssertTree(List.of(dto));
        assertEquals(AiNodeTreeAsserts.TITLE_MAX_LENGTH, dto.getTitle().length());
        assertEquals(1, warnings.size());
    }

    @Test
    void emptyNodes_fails() {
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(List.of()));
    }

    @Test
    void priorityOnNonCaseNode_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("normal", "模块", "P1"));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void invalidPriorityValue_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("case", "用例", "P9"));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void invalidType_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("group", "非法类型", null));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void stepOutsideCase_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("step", "游离步骤", null));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void stepWithChildren_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("case", "用例", null,
                node("step", "步骤", null, node("expected", "预期", null))));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void caseNestedInCase_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("case", "外层用例", null, validCase("内层用例")));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void normalUnderCase_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("case", "用例", null, node("normal", "分组", null)));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void stepUnderNormal_fails() {
        List<AiNodeTreeDTO> nodes = List.of(node("normal", "分组", null, node("step", "步骤", null)));
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void depthExceedsLimit_fails() {
        // normal 六层嵌套超出 MAX_DEPTH=5
        AiNodeTreeDTO leaf = node("normal", "L6", null);
        AiNodeTreeDTO root = leaf;
        for (int i = 5; i >= 1; i--) {
            root = node("normal", "L" + i, null, root);
        }
        List<AiNodeTreeDTO> nodes = List.of(root);
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }

    @Test
    void nodeCountExceedsLimit_fails() {
        List<AiNodeTreeDTO> nodes = new ArrayList<>();
        for (int i = 0; i < AiNodeTreeAsserts.MAX_NODE_COUNT + 1; i++) {
            nodes.add(node("case", "用例" + i, null));
        }
        assertThrows(OutputValidationException.class,
                () -> AiNodeTreeAsserts.normalizeAndAssertTree(nodes));
    }
}
