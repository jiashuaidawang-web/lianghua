package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 5 · 阅读入口】ReviewNode —— Day 5 新增节点 3/3，图的第三个业务节点。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的第三个节点（executorNode → reviewNode）。
//   建议阅读时机：读完 ExecutorNode 后读它。
//   学完能回答：
//     1. ReviewNode 审查什么？怎么审查？
//     2. pass 和 fail 分别走哪里？
//     3. 怎么防止「无限重规划」？
//
//   💡 ReviewNode 审查什么？怎么审查？
//     审查 State 里的 RESULTS（所有 Task 的执行结果）。
//
//     当前实现是「简化版」：检查 RESULTS 里有没有包含 "失败" 字样的结果。
//     - 全部成功 → pass
//     - 有失败 → fail
//
//     生产环境应该调 LLM 做更智能的审查：
//       "请审查以下执行结果，判断是否达到用户预期。返回 pass 或 fail。"
//
//   💡 pass 和 fail 分别走哪里？
//     pass → END（图结束，返回最终结果）
//     fail → 回到 PlannerNode（重规划，生成新的 Task 列表）
//
//     这就是 spec 里那张图的含义：
//       Review → pass → END
//       Review → fail → PlannerNode（循环）
//
//   💡 怎么防止「无限重规划」？
//     State 里有 PLAN_ATTEMPT 计数器。
//     每次 PlannerNode 执行 +1。
//     ReviewNode 在决定 fail 之前，先检查 PLAN_ATTEMPT：
//       < 3 → 允许重规划（fail → PlannerNode）
//       >= 3 → 强制结束（写错误信息，不再重规划）
//
//   ⬇ 下一步：看 QuantAgentStateGraph（把三个新节点连成图）。
// ============================================================================================

/**
 * 审查节点：审查 ExecutorNode 的执行结果，决定 pass 或 fail。
 *
 * <p>当前实现是简化版（检查是否有失败结果），生产环境应调 LLM 做智能审查。
 */
public class ReviewNode {

    private static final Logger log = LoggerFactory.getLogger(ReviewNode.class);

    // 最大重规划次数：防无限循环
    private static final int MAX_PLAN_ATTEMPT = 3;

    public Map<String, Object> apply(QuantAgentState state) {
        log.info("reviewNode 执行");

        Map<String, Object> updates = new HashMap<>();

        // -------------------------------------------------------------------------
        // 审查逻辑：检查 RESULTS 里有没有失败
        // -------------------------------------------------------------------------
        // 简化版：遍历 RESULTS，如果有任何结果包含 "失败" → fail
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) state.value(StateKeys.RESULTS).orElse(Map.of());

        boolean hasFailure = results.values().stream()
                .anyMatch(result -> result != null && result.contains("失败"));

        // -------------------------------------------------------------------------
        // 决定 pass 或 fail
        // -------------------------------------------------------------------------
        String reviewResult;
        if (hasFailure) {
            // 有失败 → 检查是否超过重规划上限
            int attempt = state.planAttempt();
            if (attempt >= MAX_PLAN_ATTEMPT) {
                // 超过上限 → 强制结束，不再重规划
                log.warn("重规划次数耗尽: attempt={}, 强制结束", attempt);
                reviewResult = "pass";  // 强制 pass，结束循环
                updates.put(StateKeys.ERROR_MESSAGE, "重规划 " + attempt + " 次后仍失败，强制结束");
            } else {
                // 未超上限 → fail，触发重规划
                log.info("审查不通过，准备重规划: attempt={}/{}", attempt, MAX_PLAN_ATTEMPT);
                reviewResult = "fail";
            }
        } else {
            // 全部成功 → pass
            log.info("审查通过");
            reviewResult = "pass";
        }

        // 写 REVIEW_RESULT：条件边据此决定走 END 还是 PlannerNode
        updates.put(StateKeys.REVIEW_RESULT, reviewResult);
        return updates;
    }
}
