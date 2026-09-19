package com.quant.agent.graph.nodes;

import com.quant.agent.application.audit.DiffAuditService;
import com.quant.agent.domain.audit.GapMatrix;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.domain.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 10 · 阅读入口】DiffAuditNode —— Day 10 新增节点，图的"审计闸门"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 新增的第四个业务节点（executorNode → reviewNode → diffAuditNode）。
//   建议阅读时机：读完 DiffAuditService + GapMatrix 后读它。
//   学完能回答：
//     1. DiffAuditNode 在图里放在哪个位置？为什么？
//     2. auditPassed 是怎么决定的？
//     3. DiffAuditNode 和 ReviewNode 有什么区别？
//
//   💡 DiffAuditNode 放在哪？
//     放在 ReviewNode 之后、RenderNode 之前：
//       executor → review → diffAudit → render → END
//     原因：
//       - review 已经审查了"执行结果是否成功"（执行层审计）
//       - diffAudit 审计"任务需求 vs 现有能力"（能力层审计）
//       两层审计职责不同，不能合并。
//
//   💡 auditPassed 怎么决定？
//     由 GapMatrix.hasCritical() 决定：
//       hasCritical() = false → auditPassed = true  → 继续执行
//       hasCritical() = true  → auditPassed = false → 阻断，走 END
//     这是确定性规则，不是 LLM 判断。
//
//   💡 DiffAuditNode vs ReviewNode？
//
//     ┌─────────────────┬────────────────────────────┬────────────────────────────┐
//     │ 维度             │ ReviewNode (Day 5)          │ DiffAuditNode (Day 10)      │
//     ├─────────────────┼────────────────────────────┼────────────────────────────┤
//     │ 审什么            │ 执行结果（RESULTS）是否成功   │ 任务需求 vs 现有能力是否匹配  │
//     │ 审计时机          │ 执行后（看 results）         │ 执行后（看 tasks + 能力库）   │
//     │ 判定依据          │ 结果里有没有"失败"字样       │ GapMatrix.hasCritical()     │
//     │ 失败后果          → 重规划循环               → 直接终止（能力缺口无法重规划补齐）│
//     └─────────────────┴────────────────────────────┴────────────────────────────┘
//
//   ⬇ 下一步：看 QuantAgentStateGraph（Day 10 的拓扑改动）。
// ============================================================================================

/**
 * 差分审计节点：审查"任务需求 vs 现有能力"的缺口。
 *
 * <p>放在 ReviewNode 之后、RenderNode 之前。产出 GapMatrix 写入 State，
 * 条件边据此决定走 render（通过）还是 END（阻断）。
 */
public class DiffAuditNode {

    private static final Logger log = LoggerFactory.getLogger(DiffAuditNode.class);

    private final DiffAuditService diffAuditService;

    public DiffAuditNode(DiffAuditService diffAuditService) {
        this.diffAuditService = diffAuditService;
    }

    /**
     * 执行审计。
     *
     * @param state 当前状态（含 tasks / results）
     * @return 增量更新 Map（写入 gapMatrix + auditPassed）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        log.info("diffAuditNode 执行");

        List<Task> tasks = state.tasks();

        // -------------------------------------------------------------------------
        // 执行审计：Task vs Capability
        // -------------------------------------------------------------------------
        GapMatrix gapMatrix = diffAuditService.audit(tasks);

        Map<String, Object> updates = new HashMap<>();

        // 写 GapMatrix
        updates.put(StateKeys.GAP_MATRIX, gapMatrix);

        // 写 auditPassed：没有 CRITICAL 缺口才算通过
        boolean auditPassed = !gapMatrix.hasCritical();
        updates.put(StateKeys.AUDIT_PASSED, auditPassed);

        log.info("diffAuditNode 完成: auditPassed={}, gaps={}, critical={}",
                auditPassed, gapMatrix.size(), gapMatrix.criticalCount());

        // 如果有缺口，记录摘要到 errorMessage（给 OutputNode 渲染用）
        if (!gapMatrix.isEmpty()) {
            updates.put(StateKeys.ERROR_MESSAGE, gapMatrix.summary());
        }

        return updates;
    }
}
