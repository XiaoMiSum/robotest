package io.github.xiaomisum.robotest.framework.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * API 测试执行引擎线程池（基础设施详细设计 6.3）。
 * 拒绝策略默认 Abort，由服务层捕获后转换为 API_EXECUTOR_BUSY 业务异常。
 */
@Configuration
@EnableConfigurationProperties(ApiTestProperties.class)
public class ApiTestExecutorConfig {

    @Bean("apiTestExecutor")
    public ThreadPoolTaskExecutor apiTestExecutor(ApiTestProperties properties) {
        ApiTestProperties.Executor conf = properties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(conf.getMaxConcurrency());
        executor.setMaxPoolSize(conf.getMaxConcurrency());
        executor.setQueueCapacity(conf.getQueueCapacity());
        executor.setThreadNamePrefix(conf.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    /** 调试记录异步落库线程池：与执行引擎隔离，避免持久化排队影响执行并发 */
    @Bean("apiDebugPersistExecutor")
    public ThreadPoolTaskExecutor apiDebugPersistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("api-debug-persist-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
