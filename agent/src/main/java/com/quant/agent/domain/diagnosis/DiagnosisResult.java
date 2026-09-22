package com.quant.agent.domain.diagnosis;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisResult —— Socratic 诊断的"证据报告"，核心产出。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 SocraticDiagnoser 的产出，写入 QuantAgentState。
//   学完能回答：
//     1. 为什么用 record 而不是 class？
//     2. 为什么要实现 Serializable？
//     3. EMPTY / LLM_ERROR / VALIDATION_FAILED 常量有什么用？
//
//   💡 为什么用 record？
//     DiagnosisResult 是纯数据载体（category/hypothesis/evidence/confidence/nextAction），
//     record 自动提供：不可变、equals/hashCode/toString、紧凑构造器。
//     不可变性保证：一旦诊断完成，结果不会被后续节点意外修改。
//
//   💡 为什么要实现 Serializable？
//     存入 State（DIAGNOSIS_RESULT 字段），LangGraph4j 的 StateSerializer 会序列化整个 State
//     （为 Day 7 Checkpoint 断点续传做准备，以及可能的未来 Redis 持久化）。
//     DiagnosisResult 不可序列化 → 图执行时抛 NotSerializableException。
//
//   💡 三个常量：
//     - EMPTY：缺省值（State 里没诊断时返回 EMPTY 而不是 null，避免 NPE）
//     - LLM_ERROR：错误哨兵（LLM 调用失败：超时/网络/服务端错）
//     - VALIDATION_FAILED：错误哨兵（LLM 输出校验失败：格式/category/confidence 不合规）
//
//   ⬇ 下一步：看 SocraticDiagnoser（怎么产出这个 DiagnosisResult）。
// ============================================================================================

/**
 * Socratic 诊断结果：证据驱动的归因报告。
 *
 * <p>实现 Serializable 以支持 Day 7 Checkpoint 序列化。
 */
public record DiagnosisResult(
        String category,
        String hypothesis,
        List<String> evidence,
        double confidence,
        String nextAction
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 空诊断（缺省值，避免 NPE）。 */
    public static final DiagnosisResult EMPTY = new DiagnosisResult(
            null, "", Collections.emptyList(), 0.0, "");

    /** 错误哨兵：LLM 调用失败（超时/网络/服务端错）。 */
    public static final DiagnosisResult LLM_ERROR = new DiagnosisResult(
            "LLM_ERROR", "LLM 调用失败", List.of("需要人工介入"), -1.0, "转人工处理");

    /** 错误哨兵：LLM 输出校验失败（格式/category/confidence 不合规）。 */
    public static final DiagnosisResult VALIDATION_FAILED = new DiagnosisResult(
            "VALIDATION_FAILED", "LLM 输出校验失败", List.of("需要人工介入"), -1.0, "转人工处理");

    /**
     * 紧凑构造器：基础非空校验。
     */
    public DiagnosisResult {
        if (evidence == null) {
            evidence = Collections.emptyList();
        }
    }

    /**
     * 业务校验：诊断结果是否完整可用。
     *
     * <p>注意：错误哨兵（LLM_ERROR / VALIDATION_FAILED）的 category 不是 null，
     * 但 confidence = -1.0 会让 isValid() 返回 false。
     *
     * @return true = 可路由；false = 缺关键字段或是错误哨兵
     */
    public boolean isValid() {
        return category != null && !category.isBlank()
                && hypothesis != null && !hypothesis.isBlank()
                && !evidence.isEmpty()
                && confidence >= 0.0 && confidence <= 1.0;
    }

    /**
     * 是否可自动重规划（DATA/ENVIRONMENT + 高置信）。
     *
     * @return true = 可走自动重规划；false = 必须进 HITL
     */
    public boolean isRetryable() {
        if (!isValid()) {
            return false;
        }
        try {
            DiagnosisCategory cat = DiagnosisCategory.fromValue(category);
            return (cat == DiagnosisCategory.DATA || cat == DiagnosisCategory.ENVIRONMENT)
                    && confidence >= 0.7;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "DiagnosisResult{category='" + category + "', confidence=" + confidence
                + ", evidenceSize=" + evidence.size() + "}";
    }
}
