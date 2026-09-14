package com.quant.agent.application.tool;

import com.quant.agent.infrastructure.tool.MarketDataGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Day 03 工具调用测试（Day 8 升级：注入 mock Gateway）。
 *
 * <p>验证 @Tool 方法正确委托给 MarketDataGateway。
 * 不调用真实 LLM / 外部接口，Gateway 用 mock 隔离。
 *
 * <p>Day 8 改动：StockTools 构造器改为注入 MarketDataGateway，
 * 所以测试需要 mock Gateway 并验证委托关系。
 */
class Day03StockToolsTest {

    private final MarketDataGateway gateway = mock(MarketDataGateway.class);
    private final StockTools stockTools = new StockTools(gateway);

    /**
     * 正常路径：getStockPrice 委托给 gateway.getPrice，返回结果。
     */
    @Test
    void shouldDelegateGetPriceToGateway() {
        when(gateway.getPrice("600519")).thenReturn("{\"price\":1500.0}");

        String result = stockTools.getStockPrice("600519");

        assertNotNull(result);
        assertEquals("{\"price\":1500.0}", result);

        // 验证委托关系：gateway.getPrice 被调了 1 次
        verify(gateway, times(1)).getPrice("600519");
    }

    /**
     * 正常路径：getFundamental 委托给 gateway.getFundamental，返回结果。
     */
    @Test
    void shouldDelegateGetFundamentalToGateway() {
        when(gateway.getFundamental("600519")).thenReturn("{\"pe\":30.5}");

        String result = stockTools.getFundamental("600519");

        assertNotNull(result);
        assertEquals("{\"pe\":30.5}", result);

        verify(gateway, times(1)).getFundamental("600519");
    }

    /**
     * Day 8 新增：gateway 抛异常时，tool 降级返回错误 JSON（不抛异常）。
     */
    @Test
    void shouldReturnErrorJsonWhenGatewayThrows() {
        when(gateway.getPrice("600519")).thenThrow(new RuntimeException("网络超时"));

        String result = assertDoesNotThrow(() -> stockTools.getStockPrice("600519"));

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("degraded"), "异常时应返回 degraded=true");
    }
}
