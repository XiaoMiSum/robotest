package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 结果制品内存暂存（详设 4.2 第 4 步）：秒级窗口的 put/get/evict 与后到覆盖语义；
 * TTL 与容量上限为内存护栏，不做时间相关断言（实现依赖系统时钟，行为由回收侧 evict 保证）。
 */
class ApiGitArtifactStashTest {

    private static final String BATCH = "2f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70";

    private final ApiGitArtifactStash stash = new ApiGitArtifactStash();

    @Test
    void putThenGetReturnsBytesAndEvictClears() {
        byte[] zip = {'P', 'K', 3, 4};

        stash.put(BATCH, zip);

        assertArrayEquals(zip, stash.get(BATCH));
        stash.evict(BATCH);
        assertNull(stash.get(BATCH));
    }

    @Test
    void laterPutOverwritesEarlierOne() {
        stash.put(BATCH, new byte[]{'P', 'K', 1});
        byte[] later = {'P', 'K', 2};
        stash.put(BATCH, later);

        assertArrayEquals(later, stash.get(BATCH));
    }

    @Test
    void unknownBatchReturnsNull() {
        assertNull(stash.get(BATCH));
        stash.evict(BATCH);
    }
}
