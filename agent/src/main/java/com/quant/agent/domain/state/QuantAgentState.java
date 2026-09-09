package com.quant.agent.domain.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

/**
 * Agent 状态：在节点间流转的共享上下文。
 *
 * <p>继承 LangGraph4j 的 AgentState（Map 存储），支撑：
 * <ul>
 *   <li>Day 7 Checkpoint 序列化（AgentState 可被 StateSerializer 克隆）</li>
 *   <li>Day 14 HITL（Map 开放，可修改任意字段）</li>
 * </ul>
 *
 * <p>类型化 accessor 保护高频字段，减少强转散落。
 */
public class QuantAgentState extends AgentState {

    public QuantAgentState(Map<String, Object> initData) {
        super(initData);
    }

    // ---- 类型化 accessor（高频字段） ----

    @SuppressWarnings("unchecked")
    public String symbol() {
        return (String) value(StateKeys.SYMBOL).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public String analysisResult() {
        return (String) value(StateKeys.ANALYSIS_RESULT).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public String toolData() {
        return (String) value(StateKeys.TOOL_DATA).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public String finalResult() {
        return (String) value(StateKeys.FINAL_RESULT).orElse(null);
    }

    public boolean needsTool() {
        return Boolean.TRUE.equals(value(StateKeys.NEEDS_TOOL).orElse(false));
    }

    @SuppressWarnings("unchecked")
    public String errorMessage() {
        return (String) value(StateKeys.ERROR_MESSAGE).orElse(null);
    }
}
