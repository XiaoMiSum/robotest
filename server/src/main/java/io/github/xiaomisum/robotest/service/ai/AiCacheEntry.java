package io.github.xiaomisum.robotest.service.ai;

/**
 * 带过期时间的缓存条目（AI 模型列表 / AI 配置缓存共用）：value + 过期时刻，expired() 统一时钟判断。
 */
public record AiCacheEntry<T>(T value, long expireAt) {

    /** 是否已过期（基于系统时钟） */
    public boolean expired() {
        return System.currentTimeMillis() > expireAt;
    }
}
