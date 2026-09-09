package com.quant.agent.domain.state;

/**
 * State 键名常量。
 *
 * <p>集中管理所有 key，避免 Node 间拼写错误（Map 没有编译期检查）。
 */
public final class StateKeys {
    private StateKeys() {}

    public static final String SYMBOL = "symbol";
    public static final String ANALYSIS_RESULT = "analysisResult";
    public static final String TOOL_DATA = "toolData";
    public static final String FINAL_RESULT = "finalResult";
    public static final String NEEDS_TOOL = "needsTool";
    public static final String ERROR_MESSAGE = "errorMessage";
}
