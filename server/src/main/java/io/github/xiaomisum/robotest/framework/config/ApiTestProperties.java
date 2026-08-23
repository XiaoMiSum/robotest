package io.github.xiaomisum.robotest.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 测试执行引擎线程池参数（基础设施详细设计 6.3）。
 */
@Data
@ConfigurationProperties(prefix = "api-test")
public class ApiTestProperties {

    private final Executor executor = new Executor();
    private final Debug debug = new Debug();

    @Data
    public static class Executor {
        private int maxConcurrency = 5;
        private int queueCapacity = 100;
        private String threadNamePrefix = "api-test-executor-";
    }

    @Data
    public static class Debug {
        /** 调试请求默认响应超时（毫秒），请求未显式指定时使用 */
        private int defaultTimeoutMs = 30000;
        /** 每用户调试记录保留上限，超出淘汰最旧 */
        private int recordLimit = 200;
        /** 响应体落库截断上限（字符数） */
        private int maxResponseBodyChars = 1024 * 1024;
    }
}
