package com.quant.agent.domain.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// ============================================================================================
// 【Day 4 · 阅读入口】QuantAgentState —— 节点间流转的"共享行李箱"，Day 4 状态体系的核心。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 状态体系的"数据载体"。所有节点都读它、写它。
//   建议阅读时机：读完 StateKeys 后立刻读它。
//   学完能回答：
//     1. 为什么要继承 AgentState 而不是自己写一个 POJO？
//     2. "类型化 accessor"是什么意思？为什么不直接用 Map.get？
//     3. 这个类是怎么同时支持"Day 7 Checkpoint 序列化"和"Day 14 HITL 人工干预"的？
//
//   💡 为什么要继承 AgentState？
//     LangGraph4j 的图框架要求状态必须是 AgentState 类型（或子类），因为：
//       - AgentState 内置了 Map<String, Object> 存储 + StateSerializer 序列化支持
//       - 框架的 addNode / addEdge / invoke 都认 AgentState 类型
//     如果自己写一个普通 POJO，框架根本不认，图跑不起来。
//
//   💡 AgentState 底层是什么？
//     本质上就是一个 Map<String, Object>（加一些工具方法）。
//     每个节点往 State 里写数据 = 往 Map 里 put；
//     每个节点读数据 = 从 Map 里 get。
//     所以节点间通信全靠这个"共享的 Map"。
//
//   💡 "类型化 accessor"是什么？为什么重要？
//     如果只用 Map.get，你得这么写：
//       String symbol = (String) state.get("symbol");   // 强转！写错 key 只运行时才报错

//     现在有了类型化 accessor：
//       String symbol = state.symbol();                  // 无强转、编译期检查 key、IDE 补全
//     好处：少写强转、少犯错、代码更干净。
//
//   💡 不可变语义（重要！）
//     节点收到的 state 是"不可变的快照"（框架保证）。
//     节点不能改 state 自己，而是返回一个 Map<String, Object> 表示"我要改哪些字段"，
//     框架负责把增量 merge 到新的 State 里，传给下一个节点。
//     这就是 AnalysisNode / ToolNode / OutputNode 都返回 Map 的原因。
//
//   💡 怎么支持 Day 7 Checkpoint？
//     AgentState 可以被 StateSerializer 序列化成字节（存 Redis/DB）。
//     恢复时 new QuantAgentState(序列化数据) 即可 —— 构造器接受的 Map<String,Object> 正好对应。
//     QuantAgentStateTest.shouldRoundTripThroughMap() 就是在验证这个能力。
//
//   💡 怎么支持 Day 14 HITL（Human-in-the-Loop）？
//     HITL 需要"图执行到一半暂停，让人修改 State 再继续"。
//     因为 State 是 Map，人可以改任意字段（比如把 needsTool 从 true 改成 false），
//     继续执行时新节点读到的是修改后的值。
//
//   ⬇ 下一步：看三个 Node（AnalysisNode / ToolNode / OutputNode），它们是读写了 State 的"工人"。
// ============================================================================================

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

    /**
     * 构造器：接收初始数据 Map。
     *
     * 为什么用 Map 而不是一个个字段传？
     *   因为不同场景初始数据不同：
     *     - GraphRunner 启动时只传 {SYMBOL: "600519"}
     *     - Day 7 Checkpoint 恢复时传完整的 Map（所有字段都有）
     *     - Day 14 HITL 恢复时传被人修改过的 Map
     *   用 Map 构造最灵活，不管数据多少都能塞。
     */
    public QuantAgentState(Map<String, Object> initData) {
        super(initData);  // 委托给 AgentState，它内部就是 this.map = initData
    }

    // ========================================================================
    // 类型化 accessor（高频字段）
    // ========================================================================
    // 每个方法做的事：从 Map 里按 StateKeys 取 key，强转成目标类型，缺失返回 null/false。
    // 为什么是 @SuppressWarnings("unchecked")？
    //   因为 Map.get 返回 Object，强转 String/Boolean 时编译器会警告"未经检查的转换"。
    //   我们在 accessor 里保证类型正确，所以抑制警告，不让调用方被警告淹没。

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

    // boolean 类型特殊处理：缺失时返回 false（而不是 null，避免 NPE）
    public boolean needsTool() {
        return Boolean.TRUE.equals(value(StateKeys.NEEDS_TOOL).orElse(false));
    }

    @SuppressWarnings("unchecked")
    public String errorMessage() {
        return (String) value(StateKeys.ERROR_MESSAGE).orElse(null);
    }

    // ========================================================================
    // Day 5 新增 accessor（Planner / Executor / Review 节点使用的字段）
    // ========================================================================

    /** PlannerNode 生成的任务列表 */
    @SuppressWarnings("unchecked")
    public <T> List<com.quant.agent.domain.task.Task> tasks() {
        return (List<com.quant.agent.domain.task.Task>) value(StateKeys.TASKS).orElse(List.of());
    }

    /** ExecutorNode 的执行结果（Map<TaskType名, 结果文本>） */
    @SuppressWarnings("unchecked")
    public Map<String, String> results() {
        return (Map<String, String>) value(StateKeys.RESULTS).orElse(Map.of());
    }

    /** ReviewNode 的审查结果："pass" 或 "fail" */
    public boolean reviewPassed() {
        return "pass".equals(value(StateKeys.REVIEW_RESULT).orElse(""));
    }

    /** 重规划次数计数器 */
    public int planAttempt() {
        return (int) value(StateKeys.PLAN_ATTEMPT).orElse(0);
    }

    /** RenderNode 的 LLM 润色结果（给人类用户看的自然语言文本） */
    @SuppressWarnings("unchecked")
    public String renderedResult() {
        return (String) value(StateKeys.RENDERED_RESULT).orElse(null);
    }

    // ========================================================================
    // Day 10 新增 accessor（DiffAuditNode 使用的字段）
    // ========================================================================

    /** DiffAuditNode 的缺口矩阵 */
    @SuppressWarnings("unchecked")
    public com.quant.agent.domain.audit.GapMatrix gapMatrix() {
        return (com.quant.agent.domain.audit.GapMatrix) value(StateKeys.GAP_MATRIX)
                .orElse(com.quant.agent.domain.audit.GapMatrix.EMPTY);
    }

    /** DiffAuditNode 的审计是否通过 */
    public boolean auditPassed() {
        return Boolean.TRUE.equals(value(StateKeys.AUDIT_PASSED).orElse(false));
    }
}
