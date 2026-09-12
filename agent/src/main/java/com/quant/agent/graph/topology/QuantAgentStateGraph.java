package com.quant.agent.graph.topology;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.ExecutorNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.PlannerNode;
import com.quant.agent.graph.nodes.ReviewNode;
import com.quant.agent.graph.nodes.ToolNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

    /**
     * Day 5 构造器：注入 Day 5 的三个节点。
     *
     * <p>注意：这个构造器和 Day 4 的是分开的，因为注入的节点不同。
     */
    public QuantAgentStateGraph(PlannerNode plannerNode, ExecutorNode executorNode, ReviewNode reviewNode) {
        this.day5PlannerNode = plannerNode;
        this.day5ExecutorNode = executorNode;
        this.day5ReviewNode = reviewNode;
    }

    /**
     * Day 5 编译：动态规划 + 重规划循环。
     *
     * <p>拓扑：START → planner → executor → review →(pass→END / fail→planner)
     */
    public CompiledGraph<QuantAgentState> compileDay5() throws GraphStateException {
        StateGraph<QuantAgentState> graph = new StateGraph<>(QuantAgentState::new);

        // 注册节点
        graph.addNode(PLANNER, AsyncNodeAction.node_async(day5PlannerNode::apply));
        graph.addNode(EXECUTOR, AsyncNodeAction.node_async(day5ExecutorNode::apply));
        graph.addNode(REVIEW, AsyncNodeAction.node_async(day5ReviewNode::apply));

        // 固定边：START→planner, planner→executor, executor→review
        graph.addEdge(START, PLANNER);
        graph.addEdge(PLANNER, EXECUTOR);
        graph.addEdge(EXECUTOR, REVIEW);

        // -----------------------------------------------------------------
        // 条件边：reviewNode 之后的"智能岔口"（重规划循环）
        // -----------------------------------------------------------------
        // 这是 Day 5 和 Day 4 最大的不同：
        //   Day 4 的条件边：analysis → tool 或 output（单向，不循环）
        //   Day 5 的条件边：review → END 或 planner（可能循环！）
        graph.addConditionalEdges(
                REVIEW,
                AsyncEdgeAction.edge_async(state -> {
                    // 路由员看 state.reviewPassed()
                    boolean pass = state.reviewPassed();
                    String route = pass ? END : PLANNER;
                    log.debug("Day5 条件边路由: reviewPassed={} → {}", pass, route);
                    return route;
                }),
                // 路由表：pass → END（结束），fail → PLANNER（重规划）
                Map.of(END, END, PLANNER, PLANNER)
        );

        return graph.compile();
    }
}
