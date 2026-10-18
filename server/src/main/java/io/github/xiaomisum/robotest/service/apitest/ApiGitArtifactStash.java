package io.github.xiaomisum.robotest.service.apitest;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结果制品内存暂存（GitLab 集成详细设计 4.2 第 4 步）：CI 推送的 zip 从接收（先于回报）
 * 到回收（completion / 轮询触发）之间落在此处，两个时点间隔仅数秒。
 * <p>
 * 刻意不做持久化——进程重启即丢失，缺失按 4.6 降级口径整批失败留痕（详设 4.2 第 4 步）；
 * TTL 与容量上限只是内存护栏，覆盖「批次永不终态」的异常残留，不承载业务语义。
 */
@Component
public class ApiGitArtifactStash {

    /** 残留清理时限：正常窗口为秒级，超过即视为异常残留（批次未被回收终态化） */
    private static final long TTL_MILLIS = 30 * 60 * 1000L;
    /** 容量上限：防极端并发批次撑爆堆，溢出淘汰最旧条目（本就已难回收） */
    private static final int MAX_ENTRIES = 64;

    private record Entry(byte[] zip, long createdAt) {
    }

    private final ConcurrentHashMap<String, Entry> stash = new ConcurrentHashMap<>();

    /** 暂存（同批次后到覆盖，详设 4.6 接口表幂等口径） */
    public void put(String batchId, byte[] zip) {
        purgeExpired();
        stash.put(batchId, new Entry(zip, System.currentTimeMillis()));
        while (stash.size() > MAX_ENTRIES) {
            stash.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().createdAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(stash::remove);
        }
    }

    /** 读取（不移除）：回收可能推迟，消费完成后由 {@link #evict} 显式清除 */
    public byte[] get(String batchId) {
        Entry entry = stash.get(batchId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt() > TTL_MILLIS) {
            stash.remove(batchId);
            return null;
        }
        return entry.zip();
    }

    /** 批次终态化后清除暂存（降级与成功回收两条路径都必须调用） */
    public void evict(String batchId) {
        stash.remove(batchId);
    }

    private void purgeExpired() {
        long deadline = System.currentTimeMillis() - TTL_MILLIS;
        stash.entrySet().removeIf(e -> e.getValue().createdAt() < deadline);
    }
}
