package com.quant.agent.graph.nodes;

import com.quant.agent.application.planner.PlannerService;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.domain.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 5 · 阅读入口】PlannerNode —— Day 5 新增节点 1/3，图的第一个业务节点。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的第一个节点（START → plannerNode）。
//   建议阅读时机：读完 PlannerService 后读它。
//   学完能回答：
//     1. PlannerNode 和 Day 4 的 AnalysisNode 有什么区别？
//     2. 这个节点做了什么？怎么读 State、怎么"写" State？
//     3. 重规划计数器 PLAN_ATTEMPT 是干什么的？
//
//   💡 PlannerNode 和 Day 4 的 AnalysisNode 有什么区别？
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ Day 4 AnalysisNode            │ Day 5 PlannerNode             │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ LLM 做什么            │ 分析股票（返回 DTO）           │ 规划任务（返回 Task 列表）     │
//     │ 输出写哪个 key         │ ANALYSIS_RESULT + NEEDS_TOOL  │ TASKS + PLAN_ATTEMPT         │
//     │ 后续节点              │ toolNode 或 outputNode         │ executorNode                  │
//     │ 失败处理              → outputNode（输出错误）       → 重规划 or 报错             │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//   💡 这个节点做了什么？
//     1. 从 state 取出 symbol（股票代码）
//     2. 调 PlannerService.plan(symbol) ← 复用 Day 2 的结构化输出能力！
//     3. 拿到 List<Task> 任务列表
//     4. 写 TASKS = tasks（任务列表）
//     5. 写 PLAN_ATTEMPT = 当前尝试次数 + 1（重规划计数器）
//
//   💡 PLAN_ATTEMPT 是干什么的？
//     防无限循环！ReviewNode 如果认为结果不合格，会回到 PlannerNode 重规划。
//     但 LLM 可能反复规划都不合格 → 无限循环。
//     PLAN_ATTEMPT 记录「这是第几次规划」，超过上限（如 3 次）→ 强制结束。
//
//   ⬇ 下一步：看 ExecutorNode（执行 PlannerNode 生成的 Task 列表）。
// ============================================================================================

/**
 * 规划节点：调 LLM 生成 Task 列表，写入 State。
 */
public class PlannerNode {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    // 注入 Day 5 的规划服务（调 LLM + 校验）
    private final PlannerService plannerService;

    public PlannerNode(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    /**
     * 执行节点逻辑。
     *
     * @param state 当前状态（不可变，只读）
     * @return 增量更新 Map（框架 merge 到 State）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        String symbol = state.symbol();
        log.info("plannerNode 执行: symbol={}", symbol);

        try {
            // -----------------------------------------------------------------
            // 调 PlannerService 生成 Task 列表（底层调 LLM）
            // -----------------------------------------------------------------
            // 这一步会调 LLM（可能重试最多 2 次），拿到合法的 List<Task>
            List<Task> tasks = plannerService.plan(symbol);

            Map<String, Object> updates = new HashMap<>();

            // 写 TASKS：任务列表，ExecutorNode 会读它
            updates.put(StateKeys.TASKS, tasks);

            // 写 PLAN_ATTEMPT：重规划次数 +1（防无限循环）
            // 第一次规划 = 1，第一次重规划 = 2，第二次重规划 = 3 ……
            int currentAttempt = state.planAttempt();
            updates.put(StateKeys.PLAN_ATTEMPT, currentAttempt + 1);

            return updates;

        } catch (Exception e) {
            // LLM 调用失败（网络/非法输出/重试耗尽）
            log.warn("plannerNode 失败: symbol={}, error={}", symbol, e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.ERROR_MESSAGE, "规划失败: " + e.getMessage());
            return updates;
        }
    }
}
