package com.quant.agent.infrastructure.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarketDataCache 单元测试。
 *
 * <p>验证：TTL 过期、惰性过期、主动清理、最大条目限制、命中率统计。
 */
class MarketDataCacheTest {

    /**
     * 正常路径：put → get 命中。
     */
    @Test
    void shouldStoreAndRetrieve() {
        MarketDataCache cache = new MarketDataCache(5000L, 100, 60_000L);

        cache.putString("price:600519", "{\"price\":1500}");
        String hit = cache.getString("price:600519");

        assertNotNull(hit);
        assertEquals("{\"price\":1500}", hit);
        assertEquals(1, cache.getHits());
        assertEquals(0, cache.getMisses());
    }

    /**
     * 正常路径：未命中返回 null。
     */
    @Test
    void shouldReturnNullForMissingKey() {
        MarketDataCache cache = new MarketDataCache(5000L, 100, 60_000L);

        assertNull(cache.getString("missing"));
        assertEquals(1, cache.getMisses());
        assertEquals(0, cache.getHits());
    }

    /**
     * 核心路径：TTL 过期后返回 null（惰性过期）。
     */
    @Test
    void shouldExpireAfterTtl() throws InterruptedException {
        // TTL 100ms
        MarketDataCache cache = new MarketDataCache(100L, 100, 60_000L);

        cache.putString("key", "value");
        assertEquals("value", cache.getString("key"));  // 命中

        // 等 150ms → 过期
        Thread.sleep(150);
        assertNull(cache.getString("key"), "TTL 过期后应返回 null");
    }

    /**
     * 正常路径：指定 TTL 覆盖默认。
     */
    @Test
    void shouldSupportCustomTtl() throws InterruptedException {
        MarketDataCache cache = new MarketDataCache(5000L, 100, 60_000L);

        // 自定义 TTL 50ms
        cache.putString("fast", "value", 50L);
        assertEquals("value", cache.getString("fast"));

        Thread.sleep(80);
        assertNull(cache.getString("fast"), "自定义短 TTL 应更快过期");
    }

    /**
     * 边界路径：缓存满了不再写入新 key。
     */
    @Test
    void shouldNotExceedMaxEntries() {
        MarketDataCache cache = new MarketDataCache(5000L, 3, 60_000L);

        cache.put("k1", "v1".getBytes());
        cache.put("k2", "v2".getBytes());
        cache.put("k3", "v3".getBytes());
        cache.put("k4", "v4".getBytes());  // 超限，应被拒绝

        // k1~k3 应存在，k4 不存在
        assertNotNull(cache.getString("k1"));
        assertNotNull(cache.getString("k2"));
        assertNotNull(cache.getString("k3"));
        assertNull(cache.getString("k4"), "超限 key 应未写入");
    }

    /**
     * 正常路径：invalidate 主动移除。
     */
    @Test
    void shouldInvalidateKey() {
        MarketDataCache cache = new MarketDataCache(5000L, 100, 60_000L);

        cache.putString("key", "value");
        assertNotNull(cache.getString("key"));

        cache.invalidate("key");
        assertNull(cache.getString("key"), "invalidate 后应返回 null");
        assertTrue(cache.getEvictions() >= 1);
    }

    /**
     * 正常路径：命中率统计正确。
     */
    @Test
    void shouldCalculateHitRate() {
        MarketDataCache cache = new MarketDataCache(5000L, 100, 60_000L);

        cache.putString("k", "v");
        cache.getString("k");  // hit
        cache.getString("k");  // hit
        cache.getString("missing");  // miss

        assertEquals(2, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(2.0 / 3, cache.getHitRate(), 0.01);
    }
}
