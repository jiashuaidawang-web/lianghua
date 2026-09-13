package com.quant.agent.interfaces.http;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.runtime.GraphRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 5 · 阅读入口】PlannerController —— Day 5 图执行的 HTTP 入口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 调用链的"最上游"。
//     浏览器 → PlannerController → GraphRunner.runDay5() → Day5 StateGraph → 最终结果
//   建议阅读时机：Day 5 最后读它。
//   学完能回答：Day 5 的入口和 Day 4 的入口返回值有什么不同？
//
//   💡 Day 5 入口和 Day 4 入口的区别：
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ Day 4 GraphController         │ Day 5 PlannerController       │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ URL                  │ /api/v1/graph/analyze         │ /api/v1/planner/plan          │
//     │ 参数名                │ symbol（股票代码）             │ query（自然语言请求）          │
//     │ 参数示例              │ ?symbol=600519                │ ?query=分析茅台值不值得买      │
//     │ 返回类型              │ String（finalResult 文本）    │ Map（tasks + results + review）│
//     │ 用的 Runner 方法      │ runDay4()                     │ runDay5()                     │
//     │ 流程                 │ 固定：analysis→tool→output    │ 动态：planner→executor→review │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//   💡 为什么 Day 5 的参数叫 query 不叫 symbol？
//     Day 4 是"给一个股票代码，做固定分析" —— 输入是 symbol。
//     Day 5 是"给一句自然语言，动态规划任务" —— 输入是 query（可能包含股票代码）。
//     例：?query=这个票值不值得买002909 → Java 先提取 002909，LLM 再做规划。
//
//   💡 为什么 Day 5 返回 Map 不返回 String？
//     Day 4 只返回最终文本（finalResult），因为流程固定，用户只需要结论。
//     Day 5 是动态规划，用户需要看到：
//       - 规划了哪些 Task（TASKS）
//       - 每个 Task 的执行结果（RESULTS）
//       审查结论（REVIEW_RESULT）
//     所以返回完整的 Map，前端可以展示详细执行过程。
//
//   ⬇ Day 5 到这里结束。
// ============================================================================================

/**
 * Day 5 图执行端点。
 *
 * <p>GET /api/v1/planner/plan?query=这个票值不值得买002909
 * 驱动 Day 5 的 Planner 图执行，返回规划 + 执行 + 审查的完整结果。
 */
@RestController
public class PlannerController {

    private final GraphRunner graphRunner;

    public PlannerController(@Qualifier("graphRunnerDay5") GraphRunner graphRunner) {
        this.graphRunner = graphRunner;
    }

    /**
     * 执行 Day 5 规划图。
     *
     * @param query 自然语言请求（可包含股票代码，如 "分析茅台" "这个票值不值得买002909"）
     * @return 包含 tasks / results / reviewResult 的 Map
     */
    @GetMapping("/api/v1/planner/plan")
    public Map<String, Object> plan(@RequestParam String query) {
        // 调 Day 5 拓扑：planner → executor → review →(pass→END / fail→planner)
        QuantAgentState finalState = graphRunner.runDay5(query);

        // 提取到的股票代码：从第一个 Task 的 target 派生（PlannerService 用正则提取后兜底填入）
        // 注意：tasks 可能为空（规划失败时），所以 extractedSymbol 可能是 null
        String extractedSymbol = finalState.tasks().isEmpty()
                ? null
                : finalState.tasks().get(0).target();

        // 返回完整信息，不只是最终文本
        // 用 HashMap 而不是 Map.of()：Map.of() 不允许 null 值，会抛 NPE 导致 500
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("extractedSymbol", extractedSymbol);
        response.put("tasks", finalState.tasks());
        response.put("results", finalState.results());
        response.put("reviewResult", finalState.value("reviewResult").orElse("unknown"));
        response.put("renderedResult", finalState.renderedResult());
        response.put("planAttempt", finalState.planAttempt());
        response.put("errorMessage", finalState.errorMessage());
        return response;
    }
}
