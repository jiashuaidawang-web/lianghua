package com.quant.agent.infrastructure.tool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimiter 单元测试。
 *
 * <p>验证：限流逻辑（令牌桶）、快速拒绝（非阻塞）、并发安全。
 * 不调用任何外部 I/O。
 */
class RateLimiterTest {

    /**
     * 正常路径：初始满桶，允许突发到桶容量。
     */
    @Test
    void shouldAllowBurstUpToCapacity() {
        // 桶容量 3，每秒 1 个令牌
        RateLimiter limiter = new RateLimiter(1.0, 3);

        // 初始满桶 → 允许 3 次突发
        assertTrue(limiter.tryAcquire(), "第 1 次应允许");
        assertTrue(limiter.tryAcquire(), "第 2 次应允许");
        assertTrue(limiter.tryAcquire(), "第 3 次应允许");

        // 桶空 → 第 4 次拒绝
        assertFalse(limiter.tryAcquire(), "第 4 次应拒绝（桶空）");

        // 统计正确
        assertEquals(4, limiter.getTotalRequests());
        assertEquals(1, limiter.getTotalRejected());
    }

    /**
     * 正常路径：等待后令牌恢复，再次允许。
     */
    @Test
    void shouldRefillAfterWaiting() throws InterruptedException {
        // 每秒 10 个令牌，桶容量 1
        RateLimiter limiter = new RateLimiter(10.0, 1);

        assertTrue(limiter.tryAcquire(), "第 1 次应允许");
        assertFalse(limiter.tryAcquire(), "第 2 次应拒绝（桶空）");

        // 等 150ms → 应恢复 ~1.5 个令牌（至少 1 个）
        Thread.sleep(150);
        assertTrue(limiter.tryAcquire(), "等待后令牌应恢复");
    }

    /**
     * 边界路径：非法参数构造时抛异常。
     */
    @Test
    void shouldThrowOnInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1.0, 0));
    }

    /**
     * 边界路径：tryAcquire(多令牌) 正确消耗。
     */
    @Test
    void shouldAcquireMultiplePermits() {
        RateLimiter limiter = new RateLimiter(1.0, 5);

        assertTrue(limiter.tryAcquire(3), "消耗 3 个令牌应成功");
        assertTrue(limiter.tryAcquire(2), "再消耗 2 个应成功（正好用完）");
        assertFalse(limiter.tryAcquire(1), "桶空，应拒绝");
    }

    /**
     * 并发安全：多线程同时 tryAcquire，总通过数不超过桶容量 + 恢复量。
     */
    @Test
    void shouldHandleConcurrentRequests() throws InterruptedException {
        // 桶容量 5，每秒 10 个令牌
        RateLimiter limiter = new RateLimiter(10.0, 5);

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();  // 同时开始
                    if (limiter.tryAcquire()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 同时释放所有线程
        doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        // 20 个线程抢 5 个令牌 → 最多 5 个成功（桶初始满，瞬间完成，来不及恢复）
        assertTrue(successCount.get() <= 5,
                "并发下通过数应 <= 桶容量 5，实际=" + successCount.get());
        assertTrue(successCount.get() > 0, "至少应有部分成功");
    }
}
