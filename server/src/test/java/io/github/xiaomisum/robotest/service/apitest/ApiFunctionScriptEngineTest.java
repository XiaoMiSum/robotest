package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiFunctionScriptEngineTest {

    private final ApiFunctionScriptEngine engine = new ApiFunctionScriptEngine();

    @Test
    void evaluate_returnsScriptResult() {
        Object result = engine.evaluate("args[0].toUpperCase()", List.of("abc"), Map.of());
        assertEquals("ABC", result);
    }

    @Test
    void evaluate_noArgs_bindsEmptyList() {
        Object result = engine.evaluate("1 + 2", null, Map.of());
        assertEquals(3, ((Number) result).intValue());
    }

    @Test
    void assertCompilable_syntaxError_throwsScriptInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> engine.assertCompilable("def broken( {"));
        assertEquals(ErrorCodeConstants.API_CUSTOM_FUNCTION_SCRIPT_INVALID.code(), exception.getCode());
    }

    @Test
    void compile_blacklistedImport_rejected() {
        // 导入黑名单 + 间接导入检查：编译期即拒绝，不进入执行阶段
        ServiceException exception = assertThrows(ServiceException.class,
                () -> engine.evaluate("import java.io.File\nreturn File.separator", List.of(), Map.of()));
        assertEquals(ErrorCodeConstants.API_CUSTOM_FUNCTION_SCRIPT_INVALID.code(), exception.getCode());
    }

    @Test
    void evaluate_dangerousClass_rejectedOrFailsFast() {
        // 即使绕过导入检查直接引用 Runtime，也不得产生实际副作用
        ServiceException exception = assertThrows(ServiceException.class,
                () -> engine.evaluate("Runtime.getRuntime().exec('echo hacked')", List.of(), Map.of()));
        assertInstanceOf(ServiceException.class, exception);
    }

    @Test
    void evaluate_infiniteLoop_timesOut() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> engine.evaluate("while (true) {}", null, Map.of()));
        assertEquals(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED.code(), exception.getCode());
    }
}
