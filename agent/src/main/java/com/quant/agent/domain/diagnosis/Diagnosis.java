package com.quant.agent.domain.diagnosis;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// ============================================================================================
// 【Day 12 · 阅读入口】Diagnosis —— "证据模型"，Socratic Diagnosis 的最终产出。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 诊断能力的核心产出。写入 State.DIAGNOSIS，
//   由 RenderNode 渲染成人类可读的"证据→归因→推荐动作"报告。
//   建议阅读时机：读完 Hypothesis + DiagnosisCategory 后读它。
//   学完能回答：
//     1. Diagnosis 和 GapMatrix（Day10）有什么异同？
//     2. 为什么 recommendedActions 是动作建议，而不是直接生成修改后的代码？
//     3. 为什么 Diagnosis 要实现 Serializable？
//
//   💡 Diagnosis vs GapMatrix？
//
//     ┌─────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度         │ GapMatrix (Day10)             │ Diagnosis (Day12)             │
//     ├─────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ 审什么        │ 任务需求 vs 现有能力           │ 执行失败的根因                │
//     │ 时机          │ 执行后（能力层审计）            │ 执行后（运行时归因）           │
//     │ 产出          │ 缺口列表 + severity            │ 归因类别 + 假说 + 推荐动作     │
//     │ 失败后果      │ 阻断（能力缺口无法重规划补齐）   → 交给用户/HITL 决策（不盲目改代码）│
//     └─────────────┴──────────────────────────────┴──────────────────────────────┘
//
//   💡 为什么是"动作建议"而不是直接改代码？
//     本 Day 的核心约束："出现异常时，默认路径是证据→归因→推荐动作，不是直接生成修改后的代码"。
//     改代码是高风险动作（constitution: risk-sensitive actions require deterministic policy + HITL）。
//     Agent 的职责是"诊断 + 建议"，改不改、怎么改由人决定——这就是 Socratic。
//
//   💡 为什么实现 Serializable？
//     Diagnosis 写入 State.DIAGNOSIS，LangGraph4j 的 StateSerializer 会序列化整个 State
//     （服务 Day7 Checkpoint 断点续传）。不可序列化 → 运行时 NotSerializableException。
//
//   ⬇ 下一步：看 DiagnosisService（怎么产出这个 Diagnosis）。
// ============================================================================================

/**
 * 诊断证据模型：Socratic Diagnosis 的最终产出。
 *
 * <p>由 {@link com.quant.agent.application.diagnosis.DiagnosisService} 产出，
 * 写入 {@link com.quant.agent.domain.state.StateKeys#DIAGNOSIS}。
 *
 * @param category          归因类别（确定性分类）
 * @param summary           归因摘要（一句话）
 * @param hypotheses        归因假说列表（按可信度降序）
 * @param recommendedActions 推荐动作（证据驱动，不是代码改动）
 * @param confidence        整体可信度（取最高假说可信度）
 */
public record Diagnosis(
        DiagnosisCategory category,
        String summary,
        List<Hypothesis> hypotheses,
        List<String> recommendedActions,
        Likelihood confidence) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 空诊断（健康，无异常）。 */
    public static final Diagnosis HEALTHY = new Diagnosis(
            DiagnosisCategory.HEALTHY,
            "未检测到异常，所有任务执行正常。",
            Collections.emptyList(),
            Collections.emptyList(),
            Likelihood.HIGH);

    public Diagnosis {
        // 防御性拷贝 + 不可修改：保证不可变性（和 GapMatrix 同理）
        hypotheses = Collections.unmodifiableList(hypotheses);
        recommendedActions = Collections.unmodifiableList(recommendedActions);
    }

    /**
     * 是否需要阻断原流程、进入诊断路径。
     */
    public boolean isAnomaly() {
        return category.isAnomaly();
    }

    /**
     * 最高可信度假说（用于渲染时优先展示）。
     */
    public Optional<Hypothesis> topHypothesis() {
        return hypotheses.isEmpty() ? Optional.empty() : Optional.of(hypotheses.get(0));
    }

    /**
     * 人类可读的诊断摘要（给 RenderNode 用）。
     */
    public String summary() {
        return summary;
    }
}
