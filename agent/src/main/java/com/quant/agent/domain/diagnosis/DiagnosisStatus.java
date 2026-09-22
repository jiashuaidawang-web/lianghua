package com.quant.agent.domain.diagnosis;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisStatus —— 诊断状态的"状态机"，追踪诊断生命周期。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 Socratic 诊断的"进度条"。
//   学完能回答：
//     1. 为什么需要 status，而不是靠 diagnosisResult 是否 null 判断？
//     2. VALIDATION_FAILED 和 LLM_ERROR 有什么区别？
//
//   💡 为什么不能靠 null 判断？
//     null 只能区分"没诊断"和"诊断了"，无法区分：
//       - 诊断了但 LLM 输出不合规（VALIDATION_FAILED）
//       - 诊断了但 LLM 调用失败（LLM_ERROR）
//     这两种失败的处理方式不同（前者提示"LLM 输出错"，后者提示"网络/超时"），
//     靠 null 无法区分 → 需要显式 status。
//
//   💡 VALIDATION_FAILED vs LLM_ERROR：
//     - VALIDATION_FAILED：LLM 调通了，但输出格式/category/confidence 不合规
//     - LLM_ERROR：LLM 调用本身失败（超时 / 网络 / 服务端错）
//
//   ⬇ 下一步：看 SocraticDiagnosticNode（哪里写 status）。
// ============================================================================================

/**
 * 诊断状态：追踪诊断生命周期。
 */
public enum DiagnosisStatus {

    /** 待诊断（初始状态） */
    PENDING("待诊断"),

    /** 诊断完成（可路由） */
    DIAGNOSED("诊断完成"),

    /** LLM 输出校验失败（格式/category/confidence 不合规） */
    VALIDATION_FAILED("校验失败"),

    /** LLM 调用失败（超时 / 网络 / 服务端错） */
    LLM_ERROR("LLM 调用失败");

    private final String displayName;

    DiagnosisStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == VALIDATION_FAILED || this == LLM_ERROR;
    }
}
