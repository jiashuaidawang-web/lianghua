package com.quant.agent.interfaces.http;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.runtime.GraphRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// ============================================================================================
// 【Day 4 · 阅读入口】GraphController —— Day 4 的 HTTP 入口，图的"水龙头"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 调用链的"最上游"。
//     浏览器 → GraphController → GraphRunner → QuantAgentStateGraph → 框架 → 最终结果
//   建议阅读时机：Day 4 最后读它（它只是水龙头，真正的水泵在下游）。
//   学完能回答：Day 4 的完整链路从头到尾走一遍。
//
//   💡 Day 4 完整调用链（串起 Day 0~4 所有知识）：
//
//     浏览器 GET /api/v1/graph/analyze?symbol=贵州茅台
//       │
//       ▼
//     GraphController.analyze("贵州茅台")               ← 接请求，返结果（传输层）
//       │  调 graphRunner.run(symbol)
//       ▼
//     GraphRunner.run("贵州茅台")                       ← compile + invoke（执行入口）
//       │  ① compile()：StateGraph → CompiledGraph（校验）
//       │  ② compiledGraph.invoke({symbol: "贵州茅台"})
//       ▼
//     框架内部：new QuantAgentState({symbol}) → 初始 State
//       │
//       ▼
//     START → analysisNode.apply(state)                ← 调 LLM（Day 2 的 Service）
//       │  写 ANALYSIS_RESULT = "操作=BUY, 评分=8.5, ..."
//       │  写 NEEDS_TOOL = (8.5 >= 7.0) = true
//       ▼
//     条件边读 state.needsTool() = true → 路由到 toolNode
//       │
//       ▼
//     toolNode.apply(state)                            ← 调 @Tool（Day 3 的 StockTools）
//       │  写 TOOL_DATA = "价格={...}, 基本面={...}"
//       ▼
//     outputNode.apply(state)                          ← 纯确定性汇总
//       │  写 FINAL_RESULT = "【分析】...\n【数据】..."
//       ▼
//     END → 框架返回最终 State
//       │
//       ▼
//     GraphRunner 拿出 finalState.finalResult()
//       │
//       ▼
//     GraphController 把 finalResult() 作为 HTTP 响应体返回浏览器
//
//   💡 这个入口串起了 Day 1~4 的所有能力：
//     - Day 1 的 WebFlux（返回 String 给浏览器）
//     - Day 2 的 StructuredAnalysisService（analysisNode 内部用）
//     - Day 3 的 StockTools（toolNode 内部用）
//     - Day 4 的 StateGraph + QuantAgentState（整条链的骨架）
//
//   ⬇ Day 4 到这里结束。下一步进入 Day 5（Planner Capability）。
// ============================================================================================

/**
 * 图执行端点。
 *
 * <p>GET /api/v1/graph/analyze?symbol=贵州茅台
 * 驱动整张 StateGraph 执行，返回最终结果。
 */
@RestController
public class GraphController {

    private final GraphRunner graphRunner;

    public GraphController(GraphRunner graphRunner) {
        this.graphRunner = graphRunner;
    }

    @GetMapping("/api/v1/graph/analyze")
    public String analyze(@RequestParam String symbol) {
        // 一键开工：给 symbol，拿最终结果
        QuantAgentState finalState = graphRunner.run(symbol);
        return finalState.finalResult();
    }
}
