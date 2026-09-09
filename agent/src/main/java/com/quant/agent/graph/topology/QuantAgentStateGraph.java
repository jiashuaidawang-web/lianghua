package com.quant.agent.graph.topology;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.OutputNode;
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

/**
 * Agent 图拓扑定义。
 *
 * <p>拓扑：
 * <pre>
 *   START → analysisNode ─[needsTool=true]→ toolNode → outputNode → END
 *                         ─[needsTool=false]─────────────→ outputNode → END
 * </pre>
 *
 * <p>LangGraph4j 1.8.26 API：
 * <ul>
 *   <li>StateGraph 构造需要 AgentStateFactory（QuantAgentState::new）</li>
 *   <li>addNode 接收 AsyncNodeAction，用 AsyncNodeAction.node() 包装同步函数</li>
 *   <li>addConditionalEdges 接收 AsyncEdgeAction，返回下一节点名</li>
 *   <li>invoke(Map) 传初始 Map，框架用工厂构造 State</li>
 * </ul>
 */
public class QuantAgentStateGraph {

    private static final Logger log = LoggerFactory.getLogger(QuantAgentStateGraph.class);

    // 节点名常量（避免 addEdge 拼写错误）
    public static final String ANALYSIS = "analysis";
    public static final String TOOL = "tool";
    public static final String OUTPUT = "output";

    private final AnalysisNode analysisNode;
    private final ToolNode toolNode;
    private final OutputNode outputNode;

    public QuantAgentStateGraph(AnalysisNode analysisNode, ToolNode toolNode, OutputNode outputNode) {
        this.analysisNode = analysisNode;
        this.toolNode = toolNode;
        this.outputNode = outputNode;
    }

    /**
     * 编译图。
     *
     * <p>compile() 校验图结构：节点是否存在、边是否合法、入口/终点是否连通。
     *
     * @return 编译后的可执行图
     * @throws GraphStateException 图结构非法（节点不存在、边不连通等）
     */
    public CompiledGraph<QuantAgentState> compile() throws GraphStateException {
        StateGraph<QuantAgentState> graph = new StateGraph<>(QuantAgentState::new);

        // 注册节点：AsyncNodeAction.node_async() 把同步 State→Map 包成异步
        graph.addNode(ANALYSIS, AsyncNodeAction.node_async(analysisNode::apply));
        graph.addNode(TOOL, AsyncNodeAction.node_async(toolNode::apply));
        graph.addNode(OUTPUT, AsyncNodeAction.node_async(outputNode::apply));

        // 固定边
        graph.addEdge(START, ANALYSIS);
        graph.addEdge(TOOL, OUTPUT);
        graph.addEdge(OUTPUT, END);

        // 条件边：根据 needsTool 决定走 toolNode 还是直接 outputNode
        graph.addConditionalEdges(
                ANALYSIS,
                AsyncEdgeAction.edge_async(state -> {
                    String route = state.needsTool() ? TOOL : OUTPUT;
                    log.debug("条件边路由: needsTool={} → {}", state.needsTool(), route);
                    return route;
                }),
                Map.of(
                        TOOL, TOOL,      // needsTool=true → toolNode
                        OUTPUT, OUTPUT   // needsTool=false → outputNode
                )
        );

        return graph.compile();
    }
}
