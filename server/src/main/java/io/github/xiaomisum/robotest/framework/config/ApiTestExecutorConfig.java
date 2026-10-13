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

    /** GitLab 导入线程池：低频 IO 密集任务，与执行引擎隔离，单仓库并发由进度闸门约束（GitLab 集成详设 3.5） */
    @Bean("apiGitImportExecutor")
    public ThreadPoolTaskExecutor apiGitImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("api-git-import-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    /** GitLab 结果回收入线程池：制品下载 / 信封解析 / 回写为低频 IO 密集任务，与执行引擎隔离（详设 4.2 第 6 步回写异步） */
    @Bean("apiGitCollectExecutor")
    public ThreadPoolTaskExecutor apiGitCollectExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("api-git-collect-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
