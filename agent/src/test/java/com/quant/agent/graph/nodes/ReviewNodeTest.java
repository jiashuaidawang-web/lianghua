package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReviewNode 单元测试。
 *
 * <p>验证：审查逻辑正确（有失败 → fail，全成功 → pass，超限 → 强制 pass）。
 */
class ReviewNodeTest {

    private final ReviewNode node = new ReviewNode();

    /**
     * 全成功 → pass。
     */
    @Test
    void shouldPassWhenAllTasksSucceeded() {
        // Fixture：RESULTS 里没有"失败"字样
        QuantAgentState state = new QuantAgentState(Map.of(
                "symbol", "600519",
                "results", Map.of(
                        "ANALYSIS", "操作=BUY, 评分=8.5, 理由=业绩稳健",
                        "DATA_FETCH", "价格=1500, 基本面=pe30"
                ),
                "planAttempt", 1
        ));

        Map<String, Object> updates = node.apply(state);

        // 验证：REVIEW_RESULT = "pass"
        assertEquals("pass", updates.get("reviewResult"));
    }

    /**
     * 有失败 → fail（未超上限）。
     */
    @Test
    void shouldFailWhenAnyTaskFailed() {
        // Fixture：RESULTS 里有"失败"
        QuantAgentState state = new QuantAgentState(Map.of(
                "symbol", "600519",
                "results", Map.of(
                        "ANALYSIS", "操作=BUY, 评分=8.5",
                        "DATA_FETCH", "数据获取失败: 网络超时"  // ← 有"失败"
                ),
                "planAttempt", 1  // 第 1 次规划，未超上限
        ));

        Map<String, Object> updates = node.apply(state);

        // 验证：REVIEW_RESULT = "fail"
        assertEquals("fail", updates.get("reviewResult"));
    }

    /**
     * 有失败但超过重规划上限 → 强制 pass（防无限循环）。
     */
    @Test
    void shouldFailHonestlyEvenWhenMaxAttemptsReached() {
        // Day 6 改动：reviewNode 不再做终止策略判断，只管诚实审查。
        // 即使 planAttempt 已达上限，有失败就返回 fail（终止交给条件边路由函数）。
        QuantAgentState state = new QuantAgentState(Map.of(
                "symbol", "600519",
                "results", Map.of(
                        "ANALYSIS", "分析失败: LLM 超时"  // ← 有"失败"
                ),
                "planAttempt", 3  // ← 已达上限（但 reviewNode 不再关心这个）
        ));

        Map<String, Object> updates = node.apply(state);

        // 验证：诚实返回 fail（不再强制 pass）
        assertEquals("fail", updates.get("reviewResult"));

        // 验证：不再写 errorMessage（那是终止策略的事，不是审查的事）
        assertFalse(updates.containsKey("errorMessage"),
                "reviewNode 不再负责终止策略，不应写 errorMessage");
    }
}
