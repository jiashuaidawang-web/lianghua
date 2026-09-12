package com.quant.agent.graph.runtime;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

// ============================================================================================
// 【Day 4 · 阅读入口】GraphRunner —— "一键开工"按钮，封装 compile + invoke 的统一入口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 调用链的"图执行入口"，夹在 Controller 和 StateGraph 之间。
//     Controller → GraphRunner → QuantAgentStateGraph → 框架内部 → 最终 State
//   建议阅读时机：读完 QuantAgentStateGraph 后读它。
//   学完能回答：
//     1. 为什么不直接在 Controller 里 compile + invoke，非要包一层 GraphRunner？
//     2. invoke(Map) 的 Map 怎么变成 State 的？
//     3. 为什么返回 Optional？orElseThrow 在防什么？
//
//   💡 为什么要有 GraphRunner 这一层？
//     如果 Controller 直接调 StateGraph，Controller 就得知道：
//       - 怎么 compile（会抛受检异常 GraphStateException）
//       - 怎么 invoke（传什么 Map、怎么取结果）
//     这样 Controller 就"知道太多图细节"了，违反分层原则。
//     GraphRunner 把细节包起来，Controller 只需要 run(symbol) —— 给代码，拿结果。
//
//   💡 invoke(Map) 的 Map 怎么变成 State？
//     看这行：compiledGraph.invoke(Map.of(StateKeys.SYMBOL, symbol))
//       1. 你传一个 Map {symbol: "600519"}
//       2. 框架拿到这个 Map，调 StateGraph 注入的工厂（QuantAgentState::new）
//       3. new QuantAgentState(Map.of(SYMBOL, "600519")) → 初始 State
//       4. 从这个初始 State 开始，驱动整张图执行
//
//   💡 为什么 invoke 返回 Optional？
//     因为图可能"正常结束但没产出结果"（比如配置错误导致 END 没有前驱节点写入数据）。
//     Optional 强迫调用方处理"空结果"情况，而不是拿到 null 后 NPE。
//     orElseThrow：如果真的空了，抛明确异常，告诉调用方"图执行无输出"。
//
//   💡 为什么 compile 放在 run 里而不是启动时？
//     当前实现每次 run 都 compile 一次（简化版，适合学习）。
//     生产环境通常启动时 compile 一次，缓存 CompiledGraph，每次 invoke 复用。
//     但 Day 4 为了"每次都能反映最新代码"，选择每次 compile。
//
//   ⬇ 下一步：看 GraphController（Day 4 的 HTTP 入口）。
// ============================================================================================

/**
 * 图运行统一入口：封装 compile + invoke。
 *
 * <p>调用方只需传入 symbol，框架驱动整张图执行到 END。
 *
 * <p>LangGraph4j 的 invoke(Map) 接收初始数据 Map，内部用 AgentStateFactory 构造 State。
 */
public class GraphRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphRunner.class);

    private final QuantAgentStateGraph stateGraph;

    public GraphRunner(QuantAgentStateGraph stateGraph) {
        this.stateGraph = stateGraph;
    }

    /**
     * 执行图，返回最终状态。
     *
     * @param symbol 股票代码
     * @return 最终 State（含 finalResult）
     * @throws IllegalStateException 图执行无输出
     */
    public QuantAgentState run(String symbol) {
        log.info("GraphRunner 启动: symbol={}", symbol);

        // -----------------------------------------------------------------
        // ① compile()：把"设计图"变成"可执行图"（校验节点/边）
        // -----------------------------------------------------------------
        // compile() 可能抛 GraphStateException（受检异常），
        // 这里转成 IllegalStateException（非受检），避免污染上层签名。
        final CompiledGraph<QuantAgentState> compiledGraph;
        try {
            compiledGraph = stateGraph.compile();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("图编译失败: " + e.getMessage(), e);
        }

        // -----------------------------------------------------------------
        // ② invoke(Map)：传入初始数据，驱动整张图执行到 END
        // -----------------------------------------------------------------
        // Map.of(StateKeys.SYMBOL, symbol) —— 只给初始 symbol，其他字段由节点填充
        // 框架内部：
        //   用 QuantAgentState::new 把 Map 变成初始 State
        //   → 从 START 出发，按拓扑执行节点
        //   → 条件边根据 needsTool 路由
        //   → 到达 END 后返回最终 State
        Optional<QuantAgentState> output = compiledGraph.invoke(Map.of(StateKeys.SYMBOL, symbol));

        // -----------------------------------------------------------------
        // ③ 取出最终结果，空则快速失败
        // -----------------------------------------------------------------
        QuantAgentState finalState = output.orElseThrow(
                () -> new IllegalStateException("图执行无输出: symbol=" + symbol));

        log.info("GraphRunner 完成: symbol={}, finalResult={}",
                symbol, finalState.finalResult());
        return finalState;
    }
}
