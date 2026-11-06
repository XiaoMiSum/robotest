package io.github.xiaomisum.robotest.service.ai.task.core;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预算护栏单测（总册 4.2）：签发仅可下调（1000018126）、四维耗尽判定（1000018127 口径）（C8）。
 */
class BudgetGuardTest {

    private static final int CODE_BUDGET_INVALID = 1000018126;

    private final BudgetGuard guard = new BudgetGuard();

    private static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("maxSteps", 40);
        defaults.put("maxDurationSeconds", 600);
        defaults.put("maxTokens", 200000);
        defaults.put("maxCost", 5);
        return defaults;
    }

    @Test
    void sign_withoutOverride_returnsTypeDefaultsCopy() {
        Map<String, Object> signed = guard.sign(defaults(), null);
        assertEquals(defaults(), signed);

        signed.put("maxSteps", 1);
        assertEquals(40, defaults().get("maxSteps"), "签发结果不得回写类型默认值");
    }

    @Test
    void sign_lowerOverride_appliesPerDimension() {
        Map<String, Object> signed = guard.sign(defaults(), Map.of("maxSteps", 10));
        assertEquals(10, signed.get("maxSteps"));
        assertEquals(600, signed.get("maxDurationSeconds"));
        assertEquals(200000, signed.get("maxTokens"));
        assertEquals(5, signed.get("maxCost"));
    }

    @Test
    void sign_raiseUnknownOrInvalid_rejectedWith126() {
        assertBudgetInvalid(Map.of("maxSteps", 80), "调高超上限");
        assertBudgetInvalid(Map.of("unknownDimension", 1), "未知维度");
        assertBudgetInvalid(Map.of("maxSteps", 0), "非正数");
        assertBudgetInvalid(Map.of("maxSteps", "many"), "非数值");
    }

    @Test
    void exceeded_belowAllDimensions_false() {
        BudgetGuard.Consumed consumed = BudgetGuard.Consumed.of(5, 100, 1000, 500, new BigDecimal("0.1"));
        assertFalse(guard.exceeded(defaults(), consumed));
    }

    @Test
    void exceeded_eachDimensionAtLimit_true() {
        assertTrue(guard.exceeded(defaults(), BudgetGuard.Consumed.of(40, 0, 0, 0, BigDecimal.ZERO)));
        assertTrue(guard.exceeded(defaults(), BudgetGuard.Consumed.of(0, 600, 0, 0, BigDecimal.ZERO)));
        assertTrue(guard.exceeded(defaults(), BudgetGuard.Consumed.of(0, 0, 100000, 100000, BigDecimal.ZERO)));
        assertTrue(guard.exceeded(defaults(), BudgetGuard.Consumed.of(0, 0, 0, 0, new BigDecimal("5"))));
    }

    @Test
    void consumed_of_sumsTokensInOut() {
        BudgetGuard.Consumed consumed = BudgetGuard.Consumed.of(1, 2, 30, 12, BigDecimal.ZERO);
        assertEquals(42, consumed.tokens());
    }

    private void assertBudgetInvalid(Map<String, Object> override, String scenario) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> guard.sign(defaults(), override), scenario);
        assertEquals(CODE_BUDGET_INVALID, exception.getCode(), scenario);
    }
}
