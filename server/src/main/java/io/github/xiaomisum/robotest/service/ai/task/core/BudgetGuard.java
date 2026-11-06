package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 预算护栏（总册 4.2）：签发 = 类型默认 + 请求下调（调高或越界 → 1000018126）；
 * 判定 = 步数 / 时长 / token / 成本四维任一耗尽即超限（终止码 1000018127）。
 * 预算在执行开始固化为快照，执行中不可调高。
 */
@Component
public class BudgetGuard {

    /** 预算四维（总册 2.6.2 快照结构） */
    static final Set<String> DIMENSIONS = Set.of(
            "maxSteps", "maxDurationSeconds", "maxTokens", "maxCost");

    /**
     * 签发预算快照：类型默认值为上限，请求仅可逐维下调；未知维度、非数值、非正数或超上限一律 1000018126。
     */
    public Map<String, Object> sign(Map<String, Object> typeDefaults, Map<String, Object> requestOverride) {
        Map<String, Object> signed = new LinkedHashMap<>(typeDefaults);
        if (requestOverride == null || requestOverride.isEmpty()) {
            return signed;
        }
        for (Map.Entry<String, Object> entry : requestOverride.entrySet()) {
            String dimension = entry.getKey();
            Object upper = typeDefaults.get(dimension);
            Double requested = asPositiveNumber(entry.getValue());
            Double limit = upper == null ? null : asPositiveNumber(upper);
            if (limit == null || requested == null || requested > limit) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_EXECUTION_BUDGET_INVALID);
            }
            signed.put(dimension, entry.getValue());
        }
        return signed;
    }

    /**
     * 四维超限判定：任一维度达到上限即视为耗尽（true → 执行与任务终止，1000018127）。
     */
    public boolean exceeded(Map<String, Object> budget, Consumed consumed) {
        return consumed.steps() >= intOf(budget.get("maxSteps"), Integer.MAX_VALUE)
                || consumed.durationSeconds() >= longOf(budget.get("maxDurationSeconds"), Long.MAX_VALUE)
                || consumed.tokens() >= longOf(budget.get("maxTokens"), Long.MAX_VALUE)
                || consumed.cost().compareTo(decimalOf(budget.get("maxCost"),
                        BigDecimal.valueOf(Double.MAX_VALUE))) >= 0;
    }

    /** 执行期消耗（cost 维度待用量归因接入后生效，S2 恒为 0） */
    public record Consumed(int steps, long durationSeconds, long tokens, BigDecimal cost) {

        public static Consumed of(int steps, long durationSeconds, long tokensIn, long tokensOut, BigDecimal cost) {
            return new Consumed(steps, durationSeconds, tokensIn + tokensOut, cost);
        }
    }

    private static Double asPositiveNumber(Object raw) {
        if (!(raw instanceof Number number)) {
            return null;
        }
        double value = number.doubleValue();
        return value > 0 ? value : null;
    }

    private static int intOf(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private static long longOf(Object raw, long fallback) {
        return raw instanceof Number number ? number.longValue() : fallback;
    }

    private static BigDecimal decimalOf(Object raw, BigDecimal fallback) {
        return raw instanceof Number number ? new BigDecimal(number.toString()) : fallback;
    }
}
