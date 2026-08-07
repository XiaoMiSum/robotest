package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import io.github.xiaomisum.robotest.service.project.RequirementService;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 需求上下文组装（4.3 遗漏点 / 4.5 回归 / 4.7 生成共用）：
 * 前缀块 + 需求条目（标题定界，按选取顺序）+ 临时文本，统一预算口径与截断语义。
 */
@Slf4j
@Component
public class AiRequirementContextAssembler {

    /** 需求上下文总预算（token），4.3 / 4.5 / 4.7 一致 */
    public static final int CONTEXT_TOKEN_BUDGET = 12_000;

    /** 单条需求条目内容截断预算（token） */
    public static final int ITEM_TOKEN_BUDGET = 8_000;

    @Resource
    private RequirementService requirementService;

    /** 组装结果：拼接文本 + 截断/丢弃提示 */
    public record RequirementContext(String data, List<String> warnings) {
    }

    /**
     * 组装需求上下文：单条目内容截断至 {@link #ITEM_TOKEN_BUDGET}；
     * 总预算超限时按选取顺序保留、丢弃后续并在 warning 提示；临时文本超限截断装入剩余预算。
     *
     * @param prefixBlock 业务前缀块（如【变更模块】/【需求关键词】），无前缀传 null
     */
    public RequirementContext assemble(UUID projectId, List<UUID> requirementIds, String extraText,
                                       String prefixBlock) {
        StringBuilder data = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        int used = 0;
        if (prefixBlock != null && !prefixBlock.isBlank()) {
            data.append(prefixBlock);
            used += PromptAssembler.estimateTokens(prefixBlock);
        }
        for (RequirementPoolItem item : requirementService.requireByIds(projectId, requirementIds)) {
            String content = AiTextUtils.truncateToTokenBudget(item.getContent(), ITEM_TOKEN_BUDGET);
            String block = "【需求条目】" + item.getTitle() + "\n" + content + "\n";
            int tokens = PromptAssembler.estimateTokens(block);
            if (used + tokens > CONTEXT_TOKEN_BUDGET) {
                warnings.add("需求上下文超出预算，已按选取顺序丢弃后续需求条目");
                break;
            }
            data.append(block);
            used += tokens;
        }
        if (extraText != null && !extraText.isBlank()) {
            String block = "【需求文本】\n" + extraText + "\n";
            int tokens = PromptAssembler.estimateTokens(block);
            if (used + tokens > CONTEXT_TOKEN_BUDGET) {
                // 主输入超预算时截断装入剩余预算，避免整体输入预算失守
                data.append("【需求文本】\n")
                        .append(AiTextUtils.truncateToTokenBudget(extraText, CONTEXT_TOKEN_BUDGET - used))
                        .append('\n');
                warnings.add("需求文本超出上下文预算，已截断");
            } else {
                data.append(block);
            }
        }
        return new RequirementContext(data.toString(), warnings);
    }

    /** saveAsRequirement 预保存：非空时先独立保存需求池条目，失败仅记日志不阻断主流程；返回是否保存成功 */
    public boolean trySaveRequirement(UUID projectId, UUID userId, String title, String content) {
        try {
            RequirementCreateReqDTO saveDTO = new RequirementCreateReqDTO();
            saveDTO.setTitle(title);
            saveDTO.setContent(content);
            requirementService.create(projectId, userId, saveDTO);
            return true;
        } catch (Exception e) {
            log.warn("[AI] 另存需求池条目失败，不阻断主流程: {}", e.getMessage());
            return false;
        }
    }
}
