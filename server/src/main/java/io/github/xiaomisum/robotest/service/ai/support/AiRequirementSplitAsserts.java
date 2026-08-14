package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiRequirementSplitRespDTO;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator.OutputValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 需求文档拆分的自定义结构断言（详细设计 3.2.3）——modules 数量与条数上限、字段超长宽容规整。
 *
 * <p>
 * 断言前先执行宽容规整（module/title 超长截断计入 warnings），与 stripNoise/extractJson
 * 同层处理，不触发校验失败与带错重试；结构级上限（modules/items 空、超量）违规抛
 * {@link OutputValidationException}，整体返回 error 帧、不产出部分结果。
 * </p>
 */
public final class AiRequirementSplitAsserts {

    public static final int MAX_MODULES = 50;
    public static final int MODULE_MAX_LENGTH = 100;
    public static final int MAX_ITEMS_PER_MODULE = 50;
    public static final int TITLE_MAX_LENGTH = 200;

    private AiRequirementSplitAsserts() {
    }

    /**
     * 规整 + 结构断言，返回 warnings；违规抛 OutputValidationException（整体 error 帧）
     */
    public static List<String> normalizeAndAssertModules(List<AiRequirementSplitRespDTO.Module> modules) {
        List<String> warnings = new ArrayList<>();
        if (modules == null || modules.isEmpty()) {
            throw new OutputValidationException("modules 不能为空");
        }
        if (modules.size() > MAX_MODULES) {
            throw new OutputValidationException("modules 数量不得超过 " + MAX_MODULES);
        }
        for (AiRequirementSplitRespDTO.Module module : modules) {
            String moduleName = module.getModule();
            if (moduleName != null && moduleName.length() > MODULE_MAX_LENGTH) {
                module.setModule(moduleName.substring(0, MODULE_MAX_LENGTH));
                warnings.add("模块名超长已截断：" + moduleName.substring(0, Math.min(20, MODULE_MAX_LENGTH)) + "…");
            }
            List<AiRequirementSplitRespDTO.Item> items = module.getItems();
            if (items == null || items.isEmpty()) {
                throw new OutputValidationException("模块「" + moduleName + "」的 items 不能为空");
            }
            if (items.size() > MAX_ITEMS_PER_MODULE) {
                throw new OutputValidationException("单模块 items 数量不得超过 " + MAX_ITEMS_PER_MODULE);
            }
            for (AiRequirementSplitRespDTO.Item item : items) {
                String title = item.getTitle();
                if (title != null && title.length() > TITLE_MAX_LENGTH) {
                    item.setTitle(title.substring(0, TITLE_MAX_LENGTH));
                    warnings.add("需求点标题超长已截断：" + title.substring(0, Math.min(20, TITLE_MAX_LENGTH)) + "…");
                }
            }
        }
        return warnings;
    }
}
