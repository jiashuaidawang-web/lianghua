package com.quant.agent.domain.diagnosis;

import java.io.Serializable;
import java.util.List;

// ============================================================================================
// 【Day 12 · 阅读入口】Hypothesis —— 单条归因假说，诊断的"一个可能解释"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 证据模型的最小推理单元。一条 = "一个可能的根因 + 证据"。
//   建议阅读时机：读完 DiagnosisCategory + Likelihood 后读它。
//   学完能回答：
//     1. 为什么要有多个假说，而不是只给一个结论？
//     2. evidence 字段为什么重要？
//     3. 为什么 Hypothesis 要实现 Serializable？
//
//   💡 为什么多个假说？
//     归因不是一锤子买卖。同一个失败现象可能有多个根因（超时可能是网络、也可能是死循环）。
//     列出多个假说，按可信度排序，让用户/HITL 做最终判断——而不是 Agent 武断地下结论。
//     这就是"苏格拉底式"：提出有证据的假说，而不是直接给答案改代码。
//
//   💡 evidence 为什么重要？
//     可审计的核心：不能只说"数据源挂了"，要说"根据什么判断挂了"。
//     evidence 记录判断依据（如 stderr 原文、结果文本片段），可追溯、可验证。
//
//   💡 为什么实现 Serializable？
//     Hypothesis 存在 Diagnosis.hypotheses 里，Diagnosis 写入 State.DIAGNOSIS，
//     LangGraph4j 的 StateSerializer 会序列化整个 State（服务 Day7 Checkpoint 断点续传）。
//     不可序列化 → 图执行时抛 NotSerializableException → 运行时才炸。
//
//   ⬇ 下一步：看 Diagnosis（Hypothesis 的集合 + 汇总）。
// ============================================================================================

/**
 * 单条归因假说：一个可能的根因 + 证据 + 可信度。
 *
 * <p>由 {@link com.quant.agent.application.diagnosis.DiagnosisStrategy} 产出。
 *
 * @param id          假说编号（如 "H1"、"H2"）
 * @param description 假说描述（人话）
 * @param evidence    证据列表（可追溯的判断依据）
 * @param likelihood  可信度（由证据匹配度确定性判定）
 */
public record Hypothesis(
        String id,
        String description,
        List<String> evidence,
        Likelihood likelihood) implements Serializable {

    private static final long serialVersionUID = 1L;
}
