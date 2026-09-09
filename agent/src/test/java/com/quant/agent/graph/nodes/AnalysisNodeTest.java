package com.quant.agent.graph.nodes;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.output.StockAnalysis;
import com.quant.agent.domain.state.QuantAgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AnalysisNode 单元测试。
 *
 * <p>mock StructuredAnalysisService（Day 2 能力），验证 Node 正确写 State。
 */
class AnalysisNodeTest {

    @Test
    void shouldWriteAnalysisResultAndNeedsToolWhenScoreHigh() {
        // 评分 8.5 >= 7 → needsTool=true
        StructuredAnalysisService service = mock(StructuredAnalysisService.class);
        when(service.analyze("600519")).thenReturn(
                new StockAnalysis("BUY", "600519", 8.5, "业绩稳健"));

        AnalysisNode node = new AnalysisNode(service);
        QuantAgentState state = new QuantAgentState(Map.of("symbol", "600519"));

        Map<String, Object> updates = node.apply(state);

        assertNotNull(updates.get("analysisResult"));
        assertEquals(true, updates.get("needsTool"));
    }

    @Test
    void shouldSetNeedsToolFalseWhenScoreLow() {
        // 评分 5.0 < 7 → needsTool=false
        StructuredAnalysisService service = mock(StructuredAnalysisService.class);
        when(service.analyze("600519")).thenReturn(
                new StockAnalysis("HOLD", "600519", 5.0, "观望"));

        AnalysisNode node = new AnalysisNode(service);
        QuantAgentState state = new QuantAgentState(Map.of("symbol", "600519"));

        Map<String, Object> updates = node.apply(state);

        assertEquals(false, updates.get("needsTool"));
    }

    @Test
    void shouldWriteErrorMessageOnFailure() {
        StructuredAnalysisService service = mock(StructuredAnalysisService.class);
        when(service.analyze("600519")).thenThrow(new RuntimeException("LLM timeout"));

        AnalysisNode node = new AnalysisNode(service);
        QuantAgentState state = new QuantAgentState(Map.of("symbol", "600519"));

        Map<String, Object> updates = node.apply(state);

        assertNotNull(updates.get("errorMessage"));
        assertEquals(false, updates.get("needsTool"), "失败时应直接走向 output");
    }
}
