package com.quant.agent.graph.nodes;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 4 · 阅读入口】AnalysisNode —— 图里的"分析工人"，第一个干活的节点。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的第一个业务节点（START → analysisNode）。
//   建议阅读时机：Day 4 读完 QuantAgentState 后读它。
//   学完能回答：
//     1. 节点的"输入/输出"是什么？它怎么读 State、怎么"写" State？
//     2. 为什么返回 Map 而不是直接改 state？
//     3. "score >= 7.0 才调工具"这个阈值是怎么决定要不要走 toolNode 的？
//
//   💡 节点的输入/输出契约（极其重要！）：
//     输入：QuantAgentState state（不可变快照，节点不能改它）
//     输出：Map<String, Object>（"我要更新哪些字段"，框架负责 merge）
//
//     为什么不能直接改 state？
//       因为 LangGraph4j 要保证"每次节点执行都是无副作用的纯函数"。
//       如果节点直接改 state，并发执行、重试、Checkpoint 恢复都会出问题。
//       所以框架规定：节点只读 state，返回增量 Map，框架负责 apply。
//
//   💡 这个节点做了什么？
//     1. 从 state 取出 symbol（股票代码）
//     2. 调 StructuredAnalysisService.analyze(symbol) ← 复用 Day 2 的能力！
//     3. 拿到 StockAnalysis DTO，拼装成 "操作=BUY, 评分=8.5, 理由=业绩稳健" 文本
//     4. 写 ANALYSIS_RESULT（分析文本）
//     5. 写 NEEDS_TOOL = (score >= 7.0) —— 这是后续条件边的路由依据
//
//   💡 为什么 score >= 7.0 才调工具？
//     设计意图：评分高（>=7）说明 LLM 很看好，值得花成本调工具验证；
//     评分低（<7）说明 LLM 不看好，调工具是浪费，直接输出结论。
//     这是一个"成本/收益"的工程判断，阈值可以调。
//
//   💡 异常处理策略：
//     如果 LLM 调用失败（网络/非法输出/重试耗尽），走 catch：
//       - 写 ERROR_MESSAGE（告诉下游出错了）
//       - 写 NEEDS_TOOL = false（出错就别调工具了，直接走到输出节点报错）
//     这保证了"任何情况下图都能跑到底"，不会卡住。
//
//   ⬇ 下一步：看 ToolNode（被条件边路由到的"工具工人"）。
// ============================================================================================

/**
 * 分析节点：调 LLM 分析股票，写 analysisResult。
 *
 * <p>复用 Day 2 的 StructuredAnalysisService，不直接调 LLM SDK。
 */
public class AnalysisNode {

    private static final Logger log = LoggerFactory.getLogger(AnalysisNode.class);

    // 注入 Day 2 的结构化分析服务（带校验 + 重试）
    private final StructuredAnalysisService analysisService;

    public AnalysisNode(StructuredAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 执行节点逻辑。
     *
     * @param state 当前状态（不可变，只读）
     * @return 增量更新 Map（框架 merge 到 State）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        String symbol = state.symbol();  // 从 State 读输入
        log.info("analysisNode 执行: symbol={}", symbol);

        try {
            // -----------------------------------------------------------------
            // 复用 Day 2 的结构化分析（含校验 + 重试）
            // -----------------------------------------------------------------
            // 这一步会调 LLM（可能重试最多 2 次），拿到合法的 StockAnalysis
            var analysis = analysisService.analyze(symbol);

            // 准备增量更新 Map（不是改 state！）
            Map<String, Object> updates = new HashMap<>();

            // 拼装成一段可读的文本写入 ANALYSIS_RESULT
            updates.put(StateKeys.ANALYSIS_RESULT,
                    String.format("操作=%s, 评分=%s, 理由=%s",
                            analysis.action(), analysis.score(), analysis.reason()));

            // 条件边路由依据：评分 >= 7.0 才需要调工具验证
            // 这个值会被 QuantAgentStateGraph 的条件边读取，决定走 toolNode 还是 outputNode
            updates.put(StateKeys.NEEDS_TOOL, analysis.score() >= 7.0);

            return updates;  // 返回给框架

        } catch (Exception e) {
            // LLM 调用失败（网络/非法输出/重试耗尽）
            log.warn("analysisNode 失败: symbol={}, error={}", symbol, e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.ERROR_MESSAGE, "分析失败: " + e.getMessage());
            updates.put(StateKeys.NEEDS_TOOL, false);  // 失败时直接走到输出，不调工具
            return updates;
        }
    }
}
