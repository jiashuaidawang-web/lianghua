package com.quant.agent.graph.runtime;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

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

        // compile() 校验图结构
        final CompiledGraph<QuantAgentState> compiledGraph;
        try {
            compiledGraph = stateGraph.compile();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("图编译失败: " + e.getMessage(), e);
        }

        // invoke(Map) — 传初始 Map，框架构造 State
        Optional<QuantAgentState> output = compiledGraph.invoke(Map.of(StateKeys.SYMBOL, symbol));

        QuantAgentState finalState = output.orElseThrow(
                () -> new IllegalStateException("图执行无输出: symbol=" + symbol));

        log.info("GraphRunner 完成: symbol={}, finalResult={}",
                symbol, finalState.finalResult());
        return finalState;
    }
}
