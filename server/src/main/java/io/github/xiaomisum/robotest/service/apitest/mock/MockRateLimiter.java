package io.github.xiaomisum.robotest.service.apitest.mock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单路径 QPS 限流（Mock服务详细设计 3.3.1）：滑动窗口计数，超限返回 429。
 * 键为 projectId + path；qps <= 0 时关闭限流。
 */
public class MockRateLimiter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final int qps;

    public MockRateLimiter(int qps) {
        this.qps = qps;
    }

    public boolean allow(String key) {
        if (qps <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= 1000) {
                window.pollFirst();
            }
            if (window.size() >= qps) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

}
