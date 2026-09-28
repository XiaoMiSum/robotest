package io.github.xiaomisum.robotest.framework.store;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import xyz.migoo.framework.web.core.store.InMemoryStateStore;
import xyz.migoo.framework.web.core.store.RedisStateStore;
import xyz.migoo.framework.web.core.store.StateStore;

/**
 * StateStore 装配（安全规范 6.1）：Redis 可用时用 Redis 实现（多实例共享），
 * 否则回退内存实现，两者均包一层失败开放装饰器。
 *
 * <p>应用自定义 Bean 使框架 {@code RateLimitConfiguration} 与
 * {@code MiGooWebRedisStateStoreAutoConfiguration} 的默认实现按
 * {@code @ConditionalOnMissingBean} 退让；连接工厂经 {@link ObjectProvider} 延迟解析，
 * 不依赖自动配置的装配顺序。</p>
 */
@Configuration(proxyBeanMethods = false)
public class StateStoreConfiguration {

    @Bean
    public StateStore stateStore(ObjectProvider<RedisConnectionFactory> connectionFactory) {
        RedisConnectionFactory factory = connectionFactory.getIfAvailable();
        StateStore delegate = factory == null
                ? new InMemoryStateStore()
                : new RedisStateStore(factory);
        return new FailOpenStateStore(delegate);
    }
}
