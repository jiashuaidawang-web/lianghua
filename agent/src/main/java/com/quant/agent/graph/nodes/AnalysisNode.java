package com.quant.agent.graph.nodes;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 分析节点：调 LLM 分析股票，写 analysisResult。
 *
 * <p>复用 Day 2 的 StructuredAnalysisService，不直接调 LLM SDK。
 */
public class AnalysisNode {

    private static final Logger log = LoggerFactory.getLogger(AnalysisNode.class);

    private final StructuredAnalysisService analysisService;

    public AnalysisNode(StructuredAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 执行节点逻辑。
     *
     * @param state 当前状态（不可变）
     * @return 增量更新 Map（框架 merge 到 State）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        String symbol = state.symbol();
        log.info("analysisNode 执行: symbol={}", symbol);

        try {
            // 复用 Day 2 的结构化分析（含校验 + 重试）
            var analysis = analysisService.analyze(symbol);

            Map<String, Object> updates = new HashMap<>();
            // 拼装分析文本
            updates.put(StateKeys.ANALYSIS_RESULT,
                    String.format("操作=%s, 评分=%s, 理由=%s",
                            analysis.action(), analysis.score(), analysis.reason()));
            // 条件边路由依据：评分 >= 7 才需要调工具验证
            updates.put(StateKeys.NEEDS_TOOL, analysis.score() >= 7.0);
            return updates;

        } catch (Exception e) {
            log.warn("analysisNode 失败: symbol={}, error={}", symbol, e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.ERROR_MESSAGE, "分析失败: " + e.getMessage());
            updates.put(StateKeys.NEEDS_TOOL, false);  // 失败时直接走到输出
            return updates;
        }
    }
}
