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

// ============================================================================================
// 【Day 4 · 阅读入口】QuantAgentStateGraph —— Day 4 的核心！图的"设计图"，定义节点怎么连。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 的心脏。把三个 Node 连成一张可执行的图。
//   建议阅读时机：Day 4 读完三个 Node 后重点读它（这是最难也最重要的文件）。
//   学完能回答：
//     1. StateGraph / CompiledGraph / 节点 / 边 分别是什么概念？
//     2. "条件边"和"固定边"有什么区别？路由逻辑怎么写？
//     3. compile() 做了什么？为什么 invoke() 前必须先 compile()？
//     4. AsyncNodeAction.node_async / AsyncEdgeAction.edge_async 是什么适配器？
//
//   💡 核心概念地图（LangGraph4j 的"世界观"）：
//
//     StateGraph<QuantAgentState>   —— "设计图"（还没建房子时的蓝图）
//       ├── addNode(name, action)   —— 注册一个"工位"（节点）
//       ├── addEdge(from, to)       —— 注册一条"固定传送带"（无条件，必走）
//       ├── addConditionalEdges(...)—— 注册一条"智能岔口"（按条件选方向）
//       └── compile()               —— "施工"：把蓝图校验并变成可执行的 CompiledGraph
//
//     CompiledGraph<QuantAgentState> —— "建好的工厂"（可执行）
//       └── invoke(Map 初始数据)     —— "开工"：从 START 驱动整张图跑到 END
//
//   💡 本图的拓扑结构（务必脑子里有这张图）：
//
//                    ┌─────────────────────────────────────────────┐
//                    │                  StateGraph                 │
//                    │                                             │
//         START ──→  analysisNode ──┬──[needsTool=true]──→ toolNode ──→ outputNode ──→ END
//                                   │
//                                   └──[needsTool=false]─────────────→ outputNode ──→ END
//                                                                         ↑
//                                                              (两条路径汇合到 outputNode)
//
//     3 个节点：analysis / tool / output
//     3 条固定边：START→analysis, tool→output, output→END（必走，无条件）
//     1 条条件边：analysis→?（看 needsTool 决定走 tool 还是 output）
//
//   💡 "条件边"怎么写？（最难的部分，重点看 addConditionalEdges 那几行）
//
//     addConditionalEdges(分析节点名, 路由函数, 路由表)
//                       ↑              ↑          ↑
//                       |              |          └── 所有可能的路由结果 → 对应节点名
//                       |              └───────────── 一个函数：state → 选哪个路由
//                       └──────────────────────────── 在哪个节点之后设岔口
//
//     路由函数：AsyncEdgeAction.edge_async(state -> {
//                  return state.needsTool() ? "tool" : "output";
//              })
//     路由表：Map.of("tool" → "tool", "output" → "output")
//             ↑ 路由函数返回的字符串必须在路由表里，否则报错
//
//   💡 为什么节点方法要包一层 AsyncNodeAction.node_async / edge_async？
//     因为节点方法（analysisNode::apply）是同步的（返回 Map），
//     但 LangGraph4j 内部用异步架构（支持并行、超时、取消）。
//     AsyncNodeAction.node_async() 把同步函数"适配"成异步接口：
//       同步：QuantAgentState → Map<String,Object>
//       异步：QuantAgentState → CompletableFuture<Map<String,Object>>
//     这样框架能统一处理，未来想写真正的异步节点（比如调 WebFlux）也方便。
//
//   💡 compile() 做了什么？
//     1. 校验所有节点是否存在（你 addEdge 引用的节点必须已经 addNode）
//     2. 校验所有边是否连通（从 START 出发能到 END）
//     3. 校验条件边的路由表是否包含所有可能返回值
//     4. 如果校验失败 → 抛 GraphStateException（编译期错误，不是运行时才炸）
//     这就是"先编译再执行"的好处：配置错误在启动/编译时就发现，而不是用户请求时才炸。
//
//   ⬇ 下一步：看 GraphRunner（封装 compile + invoke 的统一入口）。
// ============================================================================================

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

    // ========================================================================
    // 节点名常量（避免 addEdge 拼写错误）
    // ========================================================================
    // 为什么用常量？因为 addNode("analysis", ...) 和 addEdge(START, "analysis")
    // 用的是字符串，拼错只有运行时报错。用常量 → 编译期检查 + IDE 自动补全。
    // 这和 StateKeys 是同一个思路：把"魔法字符串"消灭掉。
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

        // -----------------------------------------------------------------
        // 1. 创建 StateGraph，注入"状态工厂"
        // -----------------------------------------------------------------
        // QuantAgentState::new 是构造器引用，等价于 initData -> new QuantAgentState(initData)
        // 框架在 invoke(Map) 时会用这个工厂把初始 Map 变成 State 对象
        StateGraph<QuantAgentState> graph = new StateGraph<>(QuantAgentState::new);

        // -----------------------------------------------------------------
        // 2. 注册节点：把三个"工位"装上
        // -----------------------------------------------------------------
        // AsyncNodeAction.node_async(analysisNode::apply)：
        //   把 analysisNode::apply（同步函数）适配成框架认识的异步接口
        // 参数是 QuantAgentState，返回是 Map<String,Object>
        graph.addNode(ANALYSIS, AsyncNodeAction.node_async(analysisNode::apply));
        graph.addNode(TOOL, AsyncNodeAction.node_async(toolNode::apply));
        graph.addNode(OUTPUT, AsyncNodeAction.node_async(outputNode::apply));

        // -----------------------------------------------------------------
        // 3. 注册固定边：三条"必走的传送带"
        // -----------------------------------------------------------------
        graph.addEdge(START, ANALYSIS);    // 起点 → 分析节点（第一个执行的节点）
        graph.addEdge(TOOL, OUTPUT);       // 工具节点 → 输出节点（走过工具后必须到输出）
        graph.addEdge(OUTPUT, END);        // 输出节点 → 终点（最后一步）

        // -----------------------------------------------------------------
        // 4. 注册条件边：analysisNode 之后的"智能岔口"
        // -----------------------------------------------------------------
        // 这是图里唯一需要"做决定"的地方：
        //   读 state.needsTool() → true 走 TOOL，false 走 OUTPUT
        graph.addConditionalEdges(
                ANALYSIS,       // 在 analysisNode 之后设岔口
                AsyncEdgeAction.edge_async(state -> {
                    // 路由函数：读 State 里的 needsTool，决定走哪条路
                    String route = state.needsTool() ? TOOL : OUTPUT;
                    log.debug("条件边路由: needsTool={} → {}", state.needsTool(), route);
                    return route;  // 返回的字符串必须在下面的路由表里
                }),
                // 路由表：所有可能的返回值 → 对应节点名
                // 这里 key 和 value 碰巧一样（TOOL→TOOL, OUTPUT→OUTPUT），
                // 但它们的语义不同：key 是路由函数的返回值，value 是实际要去的节点
                Map.of(
                        TOOL, TOOL,      // needsTool=true → 去 toolNode
                        OUTPUT, OUTPUT   // needsTool=false → 去 outputNode
                )
        );

        // -----------------------------------------------------------------
        // 5. 编译：校验 + 构建可执行图
        // -----------------------------------------------------------------
        // 这一步会检查：
        //   - addEdge 引用的节点都 addNode 了吗？（否则 GraphStateException）
        //   - 从 START 能到 END 吗？（否则图跑不起来）
        //   - 条件边的路由表覆盖所有可能返回值了吗？
        // 校验通过 → 返回 CompiledGraph（可执行）；不通过 → 抛异常，告诉你哪错了
        return graph.compile();
    }
}
