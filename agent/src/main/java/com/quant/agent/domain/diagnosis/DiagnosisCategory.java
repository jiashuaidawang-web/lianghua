package com.quant.agent.domain.diagnosis;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisCategory —— 诊断类别的"枚举围栏"，Java 确定性边界。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 Socratic 诊断的"分类字典"。
//   学完能回答：
//     1. 为什么 category 必须是枚举，而不能让 LLM 自由输出字符串？
//     2. fromValue 在校验流程的哪一步被调用？
//
//   💡 为什么必须是枚举？
//     LLM 可能输出"策略问题！"（带感叹号）、"策略"（简称）、"strategy"（英文）等变体。
//     如果允许自由字符串，下游路由.case 匹配会漏掉这些变体 → 误判。
//     枚举强制"只能这四个值"，LLM 输出不在枚举里 → 校验失败 → 进人工。
//
//   💡 四个类别的工程含义：
//     - DATA：外部数据问题（缺、延迟、格式错）→ 重试可能自动恢复
//     - STRATEGY：策略逻辑/参数问题 → 重规划可能重复犯错，必须人工
//     - CODE：代码 bug / 异常 → 必须人工修复
//     - ENVIRONMENT：网络/依赖问题 → 重试可能自动恢复
//
//   ⬇ 下一步：看 SocraticDiagnoser（哪里调 fromValue 做校验）。
// ============================================================================================

/**
 * 诊断类别：异常归因的四个确定性分类。
 *
 * <p>LLM 只能输出这四个值之一，否则 Java 校验失败。
 */
public enum DiagnosisCategory {

    /** 数据问题（缺失 / 延迟 / 格式错） */
    DATA("数据问题"),

    /** 策略问题（逻辑 / 参数） */
    STRATEGY("策略问题"),

    /** 代码问题（bug / 异常） */
    CODE("代码问题"),

    /** 环境问题（网络 / 依赖） */
    ENVIRONMENT("环境问题");

    private final String displayName;

    DiagnosisCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 从字符串解析类别（LLM 输出校验用）。
     *
     * @param value LLM 输出的 category 字符串
     * @return 匹配的枚举值
     * @throws IllegalArgumentException 不在枚举范围内
     */
    public static DiagnosisCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("category 不能为空");
        }
        String trimmed = value.trim();
        for (DiagnosisCategory category : values()) {
            if (category.name().equals(trimmed)) {
                return category;
            }
        }
        throw new IllegalArgumentException(
                "非法 category: " + trimmed + "，必须是 DATA/STRATEGY/CODE/ENVIRONMENT 之一");
    }
}
