package com.quant.agent.domain.state;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuantAgentState 单元测试。
 *
 * <p>验证：类型化 accessor、继承 AgentState 的 Map 存储、不可变语义。
 */
class QuantAgentStateTest {

    @Test
    void shouldReturnValueByKey() {
        QuantAgentState state = new QuantAgentState(Map.of(
                StateKeys.SYMBOL, "600519",
                StateKeys.NEEDS_TOOL, true));

        assertEquals("600519", state.symbol());
        assertTrue(state.needsTool());
        assertNull(state.analysisResult());  // 未设置的 key 返回 null
    }

    @Test
    void shouldReturnNullForMissingKeys() {
        QuantAgentState state = new QuantAgentState(Map.of());

        assertNull(state.symbol());
        assertNull(state.errorMessage());
        assertFalse(state.needsTool());  // Boolean 缺失 → false
    }

    @Test
    void shouldExposeDataView() {
        QuantAgentState state = new QuantAgentState(Map.of(StateKeys.SYMBOL, "600519"));

        Map<String, Object> data = state.data();
        assertEquals("600519", data.get(StateKeys.SYMBOL));
    }

    @Test
    void shouldRoundTripThroughMap() {
        // 模拟 Day 7 Checkpoint：序列化 → 反序列化
        QuantAgentState original = new QuantAgentState(Map.of(
                StateKeys.SYMBOL, "600519",
                StateKeys.ANALYSIS_RESULT, "BUY 8.5"));

        QuantAgentState restored = new QuantAgentState(original.data());

        assertEquals(original.symbol(), restored.symbol());
        assertEquals(original.analysisResult(), restored.analysisResult());
    }
}
