package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.Diagnosis;
import com.quant.agent.domain.diagnosis.DiagnosisCategory;

import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisStrategy —— 归因策略接口，每种类别一个实现。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 的"归因策略"接口。和 Day5 的 TaskHandler 是同类模式。
//   建议阅读时机：读完 Diagnosis 后读它。
//   学完能回答：
//     1. 为什么用策略模式而不是 if/else 大杂烩？
//     2. 每个 Strategy 输入是什么？输出是什么？
//     3. 为什么 Strategy 不调 LLM？
//
//   💡 为什么用策略模式？
//     if/else 写法：
//       if (category == DATA_MISSING) { ... 30行 ... }
//       else if (category == SANDBOX_FAILURE) { ... 30行 ... }
//       // 加新类别 → 必须改这个大方法 ← 违反开闭原则
//
//     策略模式：每种类别一个 Strategy 类，DiagnosisService 按类别选策略。
//     加新类别 → 新建一个 Strategy 类 → 不用改已有代码 ← 符合开闭原则。
//
//   💡 输入 / 输出？
//     输入：RESULTS（Map<TaskType, 结果文本>）—— 证据源。
//     输出：List<Hypothesis> + List<推荐动作> —— 由 Service 组装成 Diagnosis。
//
//   💡 为什么 Strategy 不调 LLM？
//     归因必须是确定性的、可审计的。让 LLM "自由发挥"解释失败原因 → 幻觉风险。
//     每个 Strategy 用确定性规则从证据里提取假说，行为可预测、可单测。
//
//   ⬇ 下一步：看具体的 Strategy 实现（DataMissingStrategy / SandboxFailureStrategy / ...）。
// ============================================================================================

/**
 * 归因策略接口：给定证据（RESULTS），产出假说 + 推荐动作。
 *
 * <p>每种 {@link DiagnosisCategory} 一个实现类。由 {@link DiagnosisService} 的决策树选出。
 */
public interface DiagnosisStrategy {

    /**
     * 这个策略处理哪种归因类别。
     *
     * @return 对应的 DiagnosisCategory
     */
    DiagnosisCategory category();

    /**
     * 根据证据生成归因假说。
     *
     * @param results 执行结果证据（Map<TaskType名, 结果文本>）
     * @return 假说列表（按可信度降序）
     */
    java.util.List<com.quant.agent.domain.diagnosis.Hypothesis> hypothesize(Map<String, String> results);

    /**
     * 根据证据生成推荐动作（不是代码改动，是给用户的建议）。
     *
     * @param results 执行结果证据
     * @return 推荐动作列表
     */
    java.util.List<String> recommend(Map<String, String> results);
}
