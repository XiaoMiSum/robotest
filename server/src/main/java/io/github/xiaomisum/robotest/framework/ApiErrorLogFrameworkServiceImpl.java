package io.github.xiaomisum.robotest.framework;

import io.github.xiaomisum.robotest.framework.security.SensitiveDataMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.apilog.core.ApiErrorLog;
import xyz.migoo.framework.apilog.core.ApiErrorLogFrameworkService;

/**
 * 错误日志落地：输出前对查询参数与请求体递归脱敏（安全规范 6.4、实时协议详细设计 3.2）。
 */
@Slf4j
@Component
public class ApiErrorLogFrameworkServiceImpl implements ApiErrorLogFrameworkService {

    @Override
    public void createApiErrorLog(ApiErrorLog apiErrorLog) {
        apiErrorLog.setRequestParams(SensitiveDataMasker.sanitizeErrorParams(apiErrorLog.getRequestParams()));
        log.error("[ApiErrorLog] {} {} params({}) ip({}) {} : {}",
                apiErrorLog.getRequestMethod(),
                apiErrorLog.getRequestUrl(),
                apiErrorLog.getRequestParams(),
                apiErrorLog.getUserIp(),
                apiErrorLog.getExceptionName(),
                apiErrorLog.getExceptionRootCauseMessage());
    }
}
