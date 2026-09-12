package com.quant.agent.graph.nodes;

import com.quant.agent.application.tool.StockTools;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 4 · 阅读入口】ToolNode —— 图里的"取数工人"，条件路由才能到的节点。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的第二个业务节点（analysisNode →[needsTool=true]→ toolNode）。
//   建议阅读时机：读完 AnalysisNode 后读它。
//   学完能回答：
//     1. ToolNode 和 Day 3 的 @Tool 调用是什么关系？
//     2. 为什么这里"直接调 StockTools 方法"而不是让 LLM 决定调哪个工具？
//     3. 什么情况下 ToolNode 根本不会执行？
//
//   💡 ToolNode 和 Day 3 的 @Tool 调用有什么区别？（易混淆！重点理解）
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ Day 3（@Tool 工具调用）        │ Day 4（ToolNode）             │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ 谁决定调哪个工具      │ LLM 自主决定（看工具说明书）     │ Java 代码写死（getStockPrice + │
//     │                     │                              │ getFundamental 都调）         │
//     │ 调用方式             │ LLM 返回 tool_use → 框架执行   │ node 里直接 stockTools.getXxx()│
//     │ 属于哪个循环          │ 在 AiServices 代理内部循环     │ 在 LangGraph4j 图循环          │
//     │ 图的节点             │ 否（Day 3 没有图）             │ 是（Day 4 StateGraph 的节点）  │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//     简单说：Day 3 是"LLM 自己决定要不要调工具"；Day 4 是"图路由决定要不要进 ToolNode"。
//     Day 4 的 ToolNode 内部是"确定性地调两个工具"，不再让 LLM 选。
//
//   💡 为什么 ToolNode 里不让 LLM 选工具了？
//     因为 Day 4 的架构理念是：
//       - LLM 只做"分析决策"（AnalysisNode）
//       - Java 做"确定性执行"（ToolNode 里写死调哪几个工具）
//     这样分工明确：概率性行为（LLM）和确定性行为（Java）分离，更容易测试和调试。
//     这是 constitution 里"LLM 不能直接产生副作用，必须通过工具/策略边界"的体现。
//
//   💡 什么情况下 ToolNode 不会执行？
//     当 analysisNode 写的 NEEDS_TOOL = false 时（评分 < 7.0 或分析失败），
//     条件边直接路由到 outputNode，跳过 ToolNode。
//     这就是"条件边"的含义 —— 不是所有节点每次都跑。
//
//   ⬇ 下一步：看 OutputNode（汇总结果的"输出工人"）。
// ============================================================================================

/**
 * 工具节点：调 @Tool 获取真实数据，写 toolData。
 *
 * <p>复用 Day 3 的 StockTools，不直接调第三方 SDK。
 */
public class ToolNode {

    private static final Logger log = LoggerFactory.getLogger(ToolNode.class);

    // 注入 Day 3 的工具集合（getStockPrice + getFundamental）
    private final StockTools stockTools;

    public ToolNode(StockTools stockTools) {
        this.stockTools = stockTools;
    }

    public Map<String, Object> apply(QuantAgentState state) {
        String symbol = state.symbol();
        log.info("toolNode 执行: symbol={}", symbol);

        try {
            // -----------------------------------------------------------------
            // 确定性调用：直接调 Java 方法，不是让 LLM 决定
            // -----------------------------------------------------------------
            // 这里同时调了两个工具（价格 + 基本面），结果拼成一段文本
            String price = stockTools.getStockPrice(symbol);
            String fundamental = stockTools.getFundamental(symbol);

            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_DATA,
                    String.format("价格=%s, 基本面=%s", price, fundamental));
            return updates;

        } catch (Exception e) {
            // 工具调用失败（比如未来接了真实 API，可能超时/限流）
            log.warn("toolNode 失败: symbol={}, error={}", symbol, e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_DATA, "工具调用失败: " + e.getMessage());
            return updates;  // 即使失败也返回，让 outputNode 去展示错误
        }
    }
}
