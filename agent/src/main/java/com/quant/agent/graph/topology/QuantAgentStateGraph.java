package com.quant.agent.graph.topology;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.ExecutorNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.PlannerNode;
import com.quant.agent.graph.nodes.RenderNode;
import com.quant.agent.graph.nodes.ReviewNode;
import com.quant.agent.graph.nodes.ToolNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

// ============================================================================================
// 【Day 4 + Day 5 · 阅读入口】QuantAgentStateGraph —— 图的"设计图"，支持两种拓扑。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的心脏。把节点连成一张可执行的图。
//   建议阅读时机：Day 4 / Day 5 都要读它。
//   学完能回答：
//     1. Day 4 和 Day 5 的拓扑有什么不同？
//     2. Day 5 的「重规划循环」是怎么实现的？
//     3. 为什么要有两个 compileDay4 / compileDay5 方法？
//
//   💡 Day 4 拓扑（固定流程，无循环）：
//
//         START → analysisNode →[needsTool]→ toolNode → outputNode → END
//                               →[no tool]────────────────→ outputNode → END
//
//   💡 Day 5 拓扑（动态规划 + 重规划循环）：
//
//                    ┌──────────────────────────────────────────┐
//                    │                                          │
//         START → plannerNode → executorNode → reviewNode ──→ END
//                                        ↑               │
//                                        └───[fail]───────┘
//
//     reviewNode 返回 fail → 回到 plannerNode 重规划（循环）
//     reviewNode 返回 pass → 到 END（结束）
//
//   💡 重规划循环的实现：
//     和 Day 4 的条件边一样：addConditionalEdges + 路由表。
//     路由员看 state.reviewPassed() → pass 走 END，fail 走 PLANNER。
//     路由表：Map.of(END→END, PLANNER→PLANNER)。
//
//   💡 为什么有两个 compile 方法？
//     Day 4 和 Day 5 是两套不同的拓扑（不同节点、不同边）。
//     如果混在一个 compile 里，会注册两套节点/边 → 图结构混乱。
//     拆开：compileDay4() 给 Day 4 用，compileDay5() 给 Day 5 用。
//
//   ⬇ 下一步：看 PlannerService / PlannerNode（Day 5 的具体实现）。
// ============================================================================================

/**
 * Agent 图拓扑定义。
 *
 * <p>支持两种拓扑：Day 4（固定流程）和 Day 5（动态规划 + 重规划循环）。
 */
public class QuantAgentStateGraph {

    private static final Logger log = LoggerFactory.getLogger(QuantAgentStateGraph.class);

    // ========================================================================
    // 节点名常量（避免 addEdge 拼写错误）
    // ========================================================================

    // Day 4 节点
    public static final String ANALYSIS = "analysis";
    public static final String TOOL = "tool";
    public static final String OUTPUT = "output";

    // Day 5 节点
    public static final String PLANNER = "planner";
    public static final String EXECUTOR = "executor";
    public static final String REVIEW = "review";
    public static final String RENDER = "render";

    // -------------------------------------------------------------------------
    // Day 6：终止策略参数（重规划上限）
    // -------------------------------------------------------------------------
    // 从 reviewNode 内常量移到这里，作为图拓扑层的显式配置。
    private static final int MAX_PLAN_ATTEMPT = 3;

    // ========================================================================
    // Day 4 拓扑（固定流程）
    // ========================================================================

    // 不用 final：两个构造器各自初始化自己那组，另一组为 null
    private AnalysisNode day4AnalysisNode;
    private ToolNode day4ToolNode;
    private OutputNode day4OutputNode;

    /**
     * Day 4 构造器：注入 Day 4 的三个节点。
     */
    public QuantAgentStateGraph(AnalysisNode analysisNode, ToolNode toolNode, OutputNode outputNode) {
        this.day4AnalysisNode = analysisNode;
        this.day4ToolNode = toolNode;
        this.day4OutputNode = outputNode;
    }

    /**
     * Day 4 编译：固定流程，无循环。
     */
    public CompiledGraph<QuantAgentState> compileDay4() throws GraphStateException {
        StateGraph<QuantAgentState> graph = new StateGraph<>(QuantAgentState::new);

        // 注册节点
        graph.addNode(ANALYSIS, AsyncNodeAction.node_async(day4AnalysisNode::apply));
        graph.addNode(TOOL, AsyncNodeAction.node_async(day4ToolNode::apply));
        graph.addNode(OUTPUT, AsyncNodeAction.node_async(day4OutputNode::apply));

        // 固定边
        graph.addEdge(START, ANALYSIS);
        graph.addEdge(TOOL, OUTPUT);
        graph.addEdge(OUTPUT, END);

        // 条件边：analysisNode 之后根据 needsTool 路由
        graph.addConditionalEdges(
                ANALYSIS,
                AsyncEdgeAction.edge_async(state -> {
                    String route = state.needsTool() ? TOOL : OUTPUT;
                    log.debug("Day4 条件边路由: needsTool={} → {}", state.needsTool(), route);
                    return route;
                }),
                Map.of(TOOL, TOOL, OUTPUT, OUTPUT)
        );

        return graph.compile();
    }

    // ========================================================================
    // Day 5 拓扑（动态规划 + 重规划循环）
    // ========================================================================

    private PlannerNode day5PlannerNode;
    private ExecutorNode day5ExecutorNode;
    private ReviewNode day5ReviewNode;
    private RenderNode day5RenderNode;

    /**
     * Day 5 构造器：注入 Day 5 的四个节点。
     *
     * <p>注意：这个构造器和 Day 4 的是分开的，因为注入的节点不同。
     */
    public QuantAgentStateGraph(PlannerNode plannerNode, ExecutorNode executorNode,
                                 ReviewNode reviewNode, RenderNode renderNode) {
        this.day5PlannerNode = plannerNode;
        this.day5ExecutorNode = executorNode;
        this.day5ReviewNode = reviewNode;
        this.day5RenderNode = renderNode;
    }

    /**
     * Day 5 + Day 7 编译：动态规划 + 重规划循环 + 结果渲染 + Checkpoint 快照。
     *
     * <p>拓扑：
     * <pre>
     *   START → planner → executor → review ──[attempt&lt;MAX &amp; pass]──→ render → END
     *                                ──[attempt&lt;MAX &amp; fail]──→ planner（重规划循环）
     *                                ──[attempt&gt;=MAX]────────→ END（直接终止）
     * </pre>
     *
     * <p>Day 7 改动：新增 {@link CompileConfig} + {@link BaseCheckpointSaver} 注入。
     * 框架在每节点执行后自动调 saver.put() 存快照，无需手动调 save。
     *
     * @param checkpointSaver Checkpoint 存储实现（MemorySaver 测试用 / FileSystemSaver 单机 / Redis 生产）
     */
    public CompiledGraph<QuantAgentState> compileDay5(BaseCheckpointSaver checkpointSaver) throws GraphStateException {
        StateGraph<QuantAgentState> graph = new StateGraph<>(QuantAgentState::new);

        // 注册节点
        graph.addNode(PLANNER, AsyncNodeAction.node_async(day5PlannerNode::apply));
        graph.addNode(EXECUTOR, AsyncNodeAction.node_async(day5ExecutorNode::apply));
        graph.addNode(REVIEW, AsyncNodeAction.node_async(day5ReviewNode::apply));
        graph.addNode(RENDER, AsyncNodeAction.node_async(day5RenderNode::apply));

        // 固定边：START→planner, planner→executor, executor→review, render→END
        graph.addEdge(START, PLANNER);
        graph.addEdge(PLANNER, EXECUTOR);
        graph.addEdge(EXECUTOR, REVIEW);
        graph.addEdge(RENDER, END);

        // -----------------------------------------------------------------
        // 条件边：reviewNode 之后的"智能岔口"（重规划循环 + 终止策略）
        // -----------------------------------------------------------------
        // Day 6 改动：终止策略从 reviewNode 移到条件边路由函数。
        //   reviewNode 只管诚实审查（pass/fail），不管循环策略。
        //   路由函数读 planAttempt，超限直接 return END，不再伪造 pass。
        graph.addConditionalEdges(
                REVIEW,
                AsyncEdgeAction.edge_async(state -> {
                    // 终止策略：重规划次数耗尽 → 直接走 END
                    int maxAttempt = MAX_PLAN_ATTEMPT;
                    if (state.planAttempt() >= maxAttempt) {
                        log.warn("重规划次数耗尽: attempt={}/{}, 直接终止", state.planAttempt(), maxAttempt);
                        return END;
                    }
                    // 正常路由：pass → render，fail → planner（重规划）
                    boolean pass = state.reviewPassed();
                    String route = pass ? RENDER : PLANNER;
                    log.debug("Day5 条件边路由: reviewPassed={} → {}", pass, route);
                    return route;
                }),
                // 路由表：pass → RENDER, fail → PLANNER, 终止 → END
                Map.of(RENDER, RENDER, PLANNER, PLANNER, END, END)
        );

        // -----------------------------------------------------------------
        // Day 7 改动：接入 CheckpointSaver，框架自动每节点存快照
        // -----------------------------------------------------------------
        CompileConfig config = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .build();

        return graph.compile(config);
    }

    /**
     * Day 5 编译（无 Checkpoint，兼容旧调用）。
     *
     * <p>保留无参版本，方便不需要快照的场景（如简单测试）。
     */
    public CompiledGraph<QuantAgentState> compileDay5() throws GraphStateException {
        return compileDay5(new org.bsc.langgraph4j.checkpoint.MemorySaver());
    }
}
