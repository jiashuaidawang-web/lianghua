package com.quant.agent.domain.diagnosis;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisCategory —— 归因类别枚举，诊断的"确定性分类结果"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 诊断能力的分类基石。由 DiagnosisService 的决策树产出，
//   不由 LLM 猜测（constitution: deterministic policy checks）。
//   建议阅读时机：Day 12 最先读它。
//   学完能回答：
//     1. 为什么分类必须确定性、不调 LLM？
//     2. 严重度顺序是什么？
//     3. HEALTHY 也算一个类别吗？为什么？
//
//   💡 为什么分类必须确定性？
//     "这个问题严不严重"是事实判断，不是观点判断——不能靠 LLM 猜。
//     LLM 会"护犊子"：让它判断自己产出的任务失败有多严重 → 倾向说"不严重"。
//     所以分类由 Java 决策树完成，可审计、可单测、行为确定。
//
//   💡 严重度顺序（降序）：
//     RISK_TRIGGER > SANDBOX_FAILURE > DATA_MISSING > EXECUTION_ERROR > HEALTHY
//     决策树按此顺序命中即停（见 DiagnosisService）。
//
//   💡 HEALTHY 也算类别？
//     是。它代表"扫描完全部证据，未发现异常"，是决策树的正常终止状态。
//     把它显式建模为枚举，比用 null 表示"没异常"更安全（避免 NPE）。
//
//   ⬇ 下一步：看 Diagnosis（DiagnosisCategory 是它的一个字段）。
// ============================================================================================

/**
 * 归因类别：诊断的确定性分类结果。
 *
 * <p>由 {@link com.quant.agent.application.diagnosis.DiagnosisService} 的决策树产出，
 * 不由 LLM 猜测。按严重度降序排列（ordinal 越小越严重，HEALTHY 最轻）。
 */
public enum DiagnosisCategory {

    /**
     * 风控触发 —— 安全策略拦截（如沙盒脚本命中黑名单）。
     * <p>最严重：涉及安全红线，必须阻断并告知用户。
     */
    RISK_TRIGGER,

    /**
     * 沙盒执行失败 —— 超时 / 错误退出。
     * <p>脚本本身可能合规，但执行环境/资源出了问题。
     */
    SANDBOX_FAILURE,

    /**
     * 数据缺失 —— 数据获取失败 / 字段为空 / 超时 / 源不可用。
     * <p>策略依赖的数据没拿到，继续跑也没意义。
     */
    DATA_MISSING,

    /**
     * 执行错误 —— 通用执行失败兜底（未知类型、未执行、其他异常）。
     */
    EXECUTION_ERROR,

    /**
     * 健康 —— 扫描完全部证据，未发现异常。
     * <p>正常终止状态，走原流程（review → diffAudit → render）。
     */
    HEALTHY;

    /**
     * 是否需要阻断原流程、进入诊断路径。
     *
     * <p>HEALTHY 之外的所有类别都视为异常，应跳过盲目重规划，
     * 把诊断结果交给用户/HITL 决策。
     */
    public boolean isAnomaly() {
        return this != HEALTHY;
    }
}
