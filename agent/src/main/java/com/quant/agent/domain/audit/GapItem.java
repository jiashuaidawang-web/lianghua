package com.quant.agent.domain.audit;

// ============================================================================================
// 【Day 10 · 阅读入口】GapItem —— 单个"缺口"，GapMatrix 的一行。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 差分审计的最小产出单元。一条 = "一个做不到的需求"。
//   学完能回答：
//     1. current 和 required 分别是什么？
//     2. evidence 字段为什么重要？
//     3. recommendedAction 是给谁看的？
//
//   💡 current vs required：
//     required = Task 要求的能力（"我要查资金流向"）
//     current  = 现有的能力（"现有：getStockPrice, getFundamental"）
//     两者对比 → 发现缺口。
//
//   💡 evidence 为什么重要？
//     审计的核心是"可追溯"——不能只说"缺了"，要说"根据什么判断缺了"。
//     evidence 记录判断依据（如"CapabilityInventory 扫描结果：无 getFlow 相关工具"）。
//
//   💡 recommendedAction 给谁看？
//     给两个消费者：
//       1. 用户："建议：通过 MCP 连接远端资金流向数据源"
//       2. Planner（Re-planning 时）："避开 getFlow，改用 getStockPrice 近似"
//
//   ⬇ 下一步：看 GapMatrix（GapItem 的集合 + 汇总方法）。
// ============================================================================================

/**
 * 单个缺口：Task 要求的能力 vs 现有能力的落差。
 *
 * <p>由 DiffAuditService 的 Diff 引擎产出。
 *
 * @param gapId             缺口 ID（如 "GAP-001"）
 * @param category          缺口类别（对应 CapabilityType）
 * @param current           现有能力描述
 * @param required          任务要求的能力
 * @param severity          严重级别（由 Java 规则判定）
 * @param evidence          判断依据（可追溯）
 * @param recommendedAction 建议动作（给用户或 Planner）
 */
public record GapItem(
        String gapId,
        String category,
        String current,
        String required,
        Severity severity,
        String evidence,
        String recommendedAction) {

    /**
     * 是否是阻断性缺口（CRITICAL）。
     * <p>DiffAuditNode 据此决定 auditPassed。
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }
}
