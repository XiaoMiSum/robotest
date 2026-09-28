package io.github.xiaomisum.robotest.framework.store;

import lombok.extern.slf4j.Slf4j;
import xyz.migoo.framework.web.core.store.StateStore;

import java.time.Duration;

/**
 * 失败开放的 StateStore 装饰器（安全规范 6.1）。
 *
 * <p>限流计数、登录失败锁定与 Token 撤销检查共用该口径：底层存储不可用时一律放行，
 * 避免缓存故障阻断认证链路；每次故障记录 WARN 以便告警。</p>
 */
@Slf4j
public class FailOpenStateStore implements StateStore {

    private final StateStore delegate;

    public FailOpenStateStore(StateStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public long increment(String key, Duration ttl) {
        try {
            return delegate.increment(key, ttl);
        } catch (Exception e) {
            log.warn("[StateStore] increment 失败，按失败开放处理 key({}) err({})", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public long get(String key) {
        try {
            return delegate.get(key);
        } catch (Exception e) {
            log.warn("[StateStore] get 失败，按失败开放处理 key({}) err({})", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public void put(String key, long value, Duration ttl) {
        try {
            delegate.put(key, value, ttl);
        } catch (Exception e) {
            log.warn("[StateStore] put 失败，按失败开放处理 key({}) err({})", key, e.getMessage());
        }
    }

    @Override
    public boolean setIfAbsent(String key, Duration ttl) {
        try {
            return delegate.setIfAbsent(key, ttl);
        } catch (Exception e) {
            log.warn("[StateStore] setIfAbsent 失败，按失败开放处理 key({}) err({})", key, e.getMessage());
            return true;
        }
    }

    @Override
    public void delete(String key) {
        try {
            delegate.delete(key);
        } catch (Exception e) {
            log.warn("[StateStore] delete 失败，按失败开放处理 key({}) err({})", key, e.getMessage());
        }
    }
}
