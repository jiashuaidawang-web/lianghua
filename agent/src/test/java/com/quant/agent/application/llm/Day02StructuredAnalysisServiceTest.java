package com.quant.agent.application.llm;

import com.quant.agent.domain.output.StockAnalysis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StructuredAnalysisService 单元测试。
 *
 * <p>不调用真实 LLM，直接 mock StockAnalysisAiService 接口（AiServices 代理）。
 * 测试聚焦：校验逻辑 + 重试策略 + 失败路径。
 */
class Day02StructuredAnalysisServiceTest {

    /**
     * 正常路径：LLM 返回合法 DTO，服务返回强类型结果。
     */
    @Test
    void shouldReturnStructuredResultWhenLlmReturnsValidJson() {
        // Fixture：合法的 StockAnalysis
        StockAnalysis validResult = new StockAnalysis("BUY", "600519", 8.5, "业绩稳健");
        StockAnalysisAiService aiService = mock(StockAnalysisAiService.class);
        when(aiService.analyze("贵州茅台")).thenReturn(validResult);

        StructuredAnalysisService service = new StructuredAnalysisService(aiService);

        StockAnalysis result = service.analyze("贵州茅台");

        // 验证强类型 DTO，不只是「没抛异常」
        assertNotNull(result);
        assertEquals("BUY", result.action());
        assertEquals("600519", result.symbol());
        assertEquals(8.5, result.score());
        assertEquals("业绩稳健", result.reason());

        // 只调用了 1 次（合法不重试）
        verify(aiService, times(1)).analyze("贵州茅台");
    }

    /**
     * 失败路径：LLM 返回非法 DTO（字段缺失），重试耗尽后抛明确异常。
     */
    @Test
    void shouldThrowAfterRetriesWhenLlmReturnsInvalidJson() {
        // Fixture：非法 DTO（score 超出范围 0~10）
        StockAnalysis invalidResult = new StockAnalysis("BUY", "600519", 999.0, "理由");
        StockAnalysisAiService aiService = mock(StockAnalysisAiService.class);
        when(aiService.analyze("贵州茅台")).thenReturn(invalidResult);

        StructuredAnalysisService service = new StructuredAnalysisService(aiService);

        // 重试 2 次后仍非法 → 抛明确异常
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.analyze("贵州茅台")
        );
        assertTrue(exception.getMessage().contains("结构化分析失败"));

        // 验证确实调用了 2 次（重试 2 次）
        verify(aiService, times(2)).analyze("贵州茅台");
    }

    /**
     * 重试恢复：第 1 次非法，第 2 次合法，最终成功。
     */
    @Test
    void shouldSucceedAfterRetry() {
        // 第 1 次非法（字段缺失）
        StockAnalysis invalidResult = new StockAnalysis("BUY", "600519", null, "理由");
        // 第 2 次合法
        StockAnalysis validResult = new StockAnalysis("BUY", "600519", 8.5, "业绩稳健");

        StockAnalysisAiService aiService = mock(StockAnalysisAiService.class);
        when(aiService.analyze("贵州茅台"))
                .thenReturn(invalidResult)
                .thenReturn(validResult);

        StructuredAnalysisService service = new StructuredAnalysisService(aiService);

        StockAnalysis result = service.analyze("贵州茅台");

        assertNotNull(result);
        assertEquals("BUY", result.action());
        assertEquals(8.5, result.score());
        verify(aiService, times(2)).analyze("贵州茅台");
    }

    /**
     * 边界路径：LLM 返回 null，视为非法，重试耗尽后抛异常。
     */
    @Test
    void shouldThrowWhenLlmReturnsNull() {
        StockAnalysisAiService aiService = mock(StockAnalysisAiService.class);
        when(aiService.analyze("贵州茅台")).thenReturn(null);

        StructuredAnalysisService service = new StructuredAnalysisService(aiService);

        assertThrows(IllegalStateException.class, () -> service.analyze("贵州茅台"));
        verify(aiService, times(2)).analyze("贵州茅台");
    }
}
