package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.service.ai.AiOutputValidator.OutputValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 生成用例树的自定义结构断言（详细设计 2.2）——用例子树生成、步骤补全、文本导入共用。
 *
 * <p>
 * 断言前先执行宽容规整（title 超长截断计入 warnings，children 为 null 归一为空），
 * 与 stripNoise/extractJson 同层处理，不触发校验失败与带错重试。
 * </p>
 */
public final class AiNodeTreeAsserts {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int MAX_DEPTH = 5;
    public static final int MAX_NODE_COUNT = 200;

    private static final Set<String> VALID_TYPES = Set.of(
            Constants.NodeType.CASE, Constants.NodeType.NORMAL, Constants.NodeType.PRECONDITION,
            Constants.NodeType.STEP, Constants.NodeType.EXPECTED);
    private static final Set<String> VALID_PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    /** case 的直接子节点只允许用例明细三件套（隐含 case 不嵌套 case） */
    private static final Set<String> CASE_CHILD_TYPES = Set.of(
            Constants.NodeType.PRECONDITION, Constants.NodeType.STEP, Constants.NodeType.EXPECTED);
    /** normal 可嵌套 normal / case */
    private static final Set<String> NORMAL_CHILD_TYPES = Set.of(
            Constants.NodeType.NORMAL, Constants.NodeType.CASE);

    private AiNodeTreeAsserts() {
    }

    /**
     * 完整树模式（生成子树/文本导入）：规整 + 结构断言，返回 warnings；违规抛 OutputValidationException
     */
    public static List<String> normalizeAndAssertTree(List<AiNodeTreeDTO> nodes) {
        List<String> warnings = normalize(nodes);
        if (nodes == null || nodes.isEmpty()) {
            throw new OutputValidationException("nodes 不能为空");
        }
        if (countNodes(nodes) > MAX_NODE_COUNT) {
            throw new OutputValidationException("单次生成节点总数不得超过 " + MAX_NODE_COUNT);
        }
        // 顶层节点挂载于目标节点下，允许 case / normal（用例明细三件套必须挂在 case 下）
        for (AiNodeTreeDTO node : nodes) {
            assertNode(node, null, 1);
        }
        return warnings;
    }

    /** 递归校验类型枚举、priority 归属、父子合法性与深度 */
    private static void assertNode(AiNodeTreeDTO node, String parentType, int depth) {
        if (depth > MAX_DEPTH) {
            throw new OutputValidationException("树深度不得超过 " + MAX_DEPTH);
        }
        String type = node.getType();
        if (!VALID_TYPES.contains(type)) {
            throw new OutputValidationException("非法节点类型：" + type);
        }
        if (node.getPriority() != null) {
            if (!Constants.NodeType.CASE.equals(type)) {
                throw new OutputValidationException("priority 仅允许出现在 case 节点");
            }
            if (!VALID_PRIORITIES.contains(node.getPriority())) {
                throw new OutputValidationException("非法优先级：" + node.getPriority());
            }
        }
        switch (type) {
            case Constants.NodeType.PRECONDITION, Constants.NodeType.STEP, Constants.NodeType.EXPECTED -> {
                if (!Constants.NodeType.CASE.equals(parentType)) {
                    throw new OutputValidationException(type + " 节点只能是 case 的直接子节点");
                }
                if (!node.getChildren().isEmpty()) {
                    throw new OutputValidationException(type + " 节点不得有子节点");
                }
            }
            case Constants.NodeType.CASE -> {
                for (AiNodeTreeDTO child : node.getChildren()) {
                    if (!CASE_CHILD_TYPES.contains(child.getType())) {
                        throw new OutputValidationException("case 的子节点只允许 precondition/step/expected");
                    }
                }
            }
            default -> {
                for (AiNodeTreeDTO child : node.getChildren()) {
                    if (!NORMAL_CHILD_TYPES.contains(child.getType())) {
                        throw new OutputValidationException("normal 的子节点只允许 normal/case");
                    }
                }
            }
        }
        for (AiNodeTreeDTO child : node.getChildren()) {
            assertNode(child, type, depth + 1);
        }
    }

    /** 宽容规整：children 为 null 归一为空列表；title 超长截断并产出 warning */
    private static List<String> normalize(List<AiNodeTreeDTO> nodes) {
        List<String> warnings = new ArrayList<>();
        if (nodes == null) {
            return warnings;
        }
        for (AiNodeTreeDTO node : nodes) {
            if (node.getChildren() == null) {
                node.setChildren(List.of());
            }
            String title = node.getTitle();
            if (title != null && title.length() > TITLE_MAX_LENGTH) {
                node.setTitle(title.substring(0, TITLE_MAX_LENGTH));
                warnings.add("节点标题超长已截断：" + node.getTitle().substring(0, Math.min(20, TITLE_MAX_LENGTH)) + "…");
            }
            warnings.addAll(normalize(node.getChildren()));
        }
        return warnings;
    }

    private static int countNodes(List<AiNodeTreeDTO> nodes) {
        int count = 0;
        for (AiNodeTreeDTO node : nodes) {
            count += 1 + countNodes(node.getChildren());
        }
        return count;
    }
}
