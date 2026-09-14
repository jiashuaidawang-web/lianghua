package com.quant.agent.infrastructure.tool;

import com.quant.agent.domain.tool.MarketFundamental;
import com.quant.agent.domain.tool.MarketPrice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MarketDataGateway 单元测试。
 *
 * <p>验证：限流→缓存→Adapter→降级的编排逻辑。
 * RateLimiter / Cache / Adapter 全部用 mock 隔离，只测 Gateway 的编排。
 */
class MarketDataGatewayTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final MarketDataCache cache = mock(MarketDataCache.class);
    private final EastMoneyAdapter adapter = mock(EastMoneyAdapter.class);

    private final MarketDataGateway gateway = new MarketDataGateway(rateLimiter, cache, adapter);

    // ========================================================================
    // 正常路径
    // ========================================================================

    /**
     * 限流通过 + 缓存命中 → 直接返回缓存，不调 Adapter。
     */
    @Test
    void shouldReturnCachedValueWhenCacheHit() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(cache.getString("price:600519")).thenReturn("{\"price\":1500}");

        String result = gateway.getPrice("600519");

        assertEquals("{\"price\":1500}", result);
        verify(cache).getString("price:600519");
        verify(adapter, never()).fetchPrice(any());  // 缓存命中，不调 Adapter
    }

    /**
     * 限流通过 + 缓存未命中 → 调 Adapter → 写入缓存 → 返回。
     */
    @Test
    void shouldCallAdapterOnCacheMiss() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(cache.getString("price:600519")).thenReturn(null);  // 未命中

        MarketPrice price = MarketPrice.real("600519", 1500.0, "CNY");
        when(adapter.fetchPrice("600519")).thenReturn(price);

        String result = gateway.getPrice("600519");

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("1500"));

        // 验证写入缓存
        verify(cache).putString(eq("price:600519"), anyString());
        // 验证 lastKnownGood 更新（degraded=false 时）
        verify(adapter).fetchPrice("600519");
    }

    // ========================================================================
    // 限流路径
    // ========================================================================

    /**
     * 限流拒绝 → 尝试返回缓存（即使可能过期），不调 Adapter。
     */
    @Test
    void shouldReturnCachedValueWhenRateLimited() {
        when(rateLimiter.tryAcquire()).thenReturn(false);  // 限流
        when(cache.getString("price:600519")).thenReturn("{\"price\":1500,\"degraded\":true}");

        String result = gateway.getPrice("600519");

        assertEquals("{\"price\":1500,\"degraded\":true}", result);
        verify(adapter, never()).fetchPrice(any());  // 限流时不调 Adapter
        assertEquals(1, gateway.getRateLimitedCount());
    }

    /**
     * 限流拒绝 + 无缓存 → 返回限流降级响应。
     */
    @Test
    void shouldReturnRateLimitedResponseWhenNoCache() {
        when(rateLimiter.tryAcquire()).thenReturn(false);
        when(cache.getString("price:600519")).thenReturn(null);  // 无缓存

        String result = gateway.getPrice("600519");

        assertNotNull(result);
        assertTrue(result.contains("限流") || result.contains("degraded"),
                "限流无缓存时应返回降级响应: " + result);
        verify(adapter, never()).fetchPrice(any());
    }

    // ========================================================================
    // 降级路径
    // ========================================================================

    /**
     * Adapter 抛异常 → 降级兜底（不抛异常）。
     */
    @Test
    void shouldFallbackWhenAdapterThrows() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(cache.getString("price:600519")).thenReturn(null);
        when(adapter.fetchPrice("600519")).thenThrow(new RuntimeException("网络超时"));

        // 不应抛异常
        String result = assertDoesNotThrow(() -> gateway.getPrice("600519"));

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("degraded"), "降级数据应标记 degraded=true");
        assertEquals(1, gateway.getFallbackCount());
    }

    /**
     * Adapter 返回非法数据（isValid=false）→ 降级兜底。
     */
    @Test
    void shouldFallbackWhenAdapterReturnsInvalidData() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(cache.getString("price:600519")).thenReturn(null);

        // 非法数据：price = -1（isValid = false）
        MarketPrice invalidPrice = new MarketPrice("600519", -1.0, "CNY", false);
        when(adapter.fetchPrice("600519")).thenReturn(invalidPrice);

        String result = gateway.getPrice("600519");

        assertNotNull(result);
        assertTrue(result.contains("degraded"), "非法数据应降级");
    }

    // ========================================================================
    // 基本面路径
    // ========================================================================

    /**
     * 基本面：限流通过 + 缓存未命中 → 调 Adapter → 返回。
     */
    @Test
    void shouldFetchFundamentalOnCacheMiss() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(cache.getString("fundamental:600519")).thenReturn(null);

        MarketFundamental fundamental = MarketFundamental.real("600519", 30.5, 8.2, "白酒");
        when(adapter.fetchFundamental("600519")).thenReturn(fundamental);

        String result = gateway.getFundamental("600519");

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("30.5"));
        verify(cache).putString(eq("fundamental:600519"), anyString());
    }

    // ========================================================================
    // 边界路径
    // ========================================================================

    /**
     * null symbol → 不抛异常，返回降级数据。
     */
    @Test
    void shouldHandleNullSymbol() {
        String result = assertDoesNotThrow(() -> gateway.getPrice(null));
        assertNotNull(result);
    }

    /**
     * 空字符串 symbol → 不抛异常。
     */
    @Test
    void shouldHandleBlankSymbol() {
        String result = assertDoesNotThrow(() -> gateway.getPrice("   "));
        assertNotNull(result);
    }
}
