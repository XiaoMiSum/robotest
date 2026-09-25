package io.github.xiaomisum.robotest.framework;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.apilog.core.ApiErrorLog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 错误日志输出前对查询参数与请求体递归脱敏（安全规范 6.4） */
class ApiErrorLogFrameworkServiceImplTest {

    private final ApiErrorLogFrameworkServiceImpl service = new ApiErrorLogFrameworkServiceImpl();

    @Test
    void createApiErrorLogSanitizesQueryAndBodyBeforeLogging() {
        ApiErrorLog errorLog = new ApiErrorLog();
        errorLog.setRequestMethod("POST");
        errorLog.setRequestUrl("/api/auth/login");
        errorLog.setRequestParams(
                "{\"query\":{\"access_token\":\"secret-jwt\"},"
                        + "\"body\":\"{\\\"username\\\":\\\"alice\\\",\\\"password\\\":\\\"realpw\\\"}\"}");
        errorLog.setUserIp("127.0.0.1");
        errorLog.setExceptionName("java.lang.RuntimeException");
        errorLog.setExceptionRootCauseMessage("boom");

        service.createApiErrorLog(errorLog);

        String params = errorLog.getRequestParams();
        assertFalse(params.contains("secret-jwt"));
        assertFalse(params.contains("realpw"));
        assertTrue(params.contains("alice"), "非敏感字段应保留");
        assertTrue(params.contains("***"));
    }

    @Test
    void createApiErrorLogMasksUnparsableParams() {
        ApiErrorLog errorLog = new ApiErrorLog();
        errorLog.setRequestMethod("GET");
        errorLog.setRequestUrl("/api/x");
        errorLog.setRequestParams("token=leak");

        service.createApiErrorLog(errorLog);

        assertFalse(errorLog.getRequestParams().contains("leak"));
    }
}
