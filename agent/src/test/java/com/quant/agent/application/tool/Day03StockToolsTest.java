package com.quant.agent.application.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 03 工具调用测试。
 *
 * <p>验证 @Tool 方法被正确调用，返回预期结果。
 * 不调用真实 LLM，直接测试工具方法本身。
 */
class Day03StockToolsTest {

    private final StockTools stockTools = new StockTools();

    @Test
    void shouldReturnStockPrice() {
        String result = stockTools.getStockPrice("600519");

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("1500.0"));
        System.out.println("getStockPrice 返回: " + result);
    }

    @Test
    void shouldReturnFundamental() {
        String result = stockTools.getFundamental("600519");

        assertNotNull(result);
        assertTrue(result.contains("600519"));
        assertTrue(result.contains("30.5"));
        System.out.println("getFundamental 返回: " + result);
    }
}
