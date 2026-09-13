package com.quant.agent.graph.runtime;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * 执行 Day 4 图（固定流程），返回最终状态。
     *
     * @param symbol 股票代码
     * @return 最终 State（含 finalResult）
     * @throws IllegalStateException 图执行无输出
     */
    public QuantAgentState runDay4(String symbol) {
        log.info("GraphRunner Day4 启动: symbol={}", symbol);

        final CompiledGraph<QuantAgentState> compiledGraph;
        try {
            compiledGraph = stateGraph.compileDay4();
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("图编译失败: " + e.getMessage(), e);
        }

        Optional<QuantAgentState> output = compiledGraph.invoke(Map.of(StateKeys.SYMBOL, symbol));

        QuantAgentState finalState = output.orElseThrow(
                () -> new IllegalStateException("图执行无输出: symbol=" + symbol));

        log.info("GraphRunner Day4 完成: symbol={}, finalResult={}",
                symbol, finalState.finalResult());
        return finalState;
    }

    /**
     * 执行 Day 5 图（动态规划 + 重规划循环），返回最终状态。
     *
     * @param symbol 股票代码
     * @return 最终 State（含 results / reviewResult）
     * @throws IllegalStateException 图执行无输出
     */
    public QuantAgentState runDay5(String symbol) {
        return runDay5(symbol, new org.bsc.langgraph4j.checkpoint.MemorySaver());
    }

    /**
     * 执行 Day 5 图（带 Checkpoint 快照）。
     *
     * <p>Day 7 改动：注入 BaseCheckpointSaver，框架每节点自动存快照。
     * 如果执行中断，可用 threadId 调 {@link #resumeDay5} 恢复。
     *
     * @param symbol          股票代码
     * @param checkpointSaver Checkpoint 存储实现
     * @return 最终 State（含 results / reviewResult）
     */
    public QuantAgentState runDay5(String symbol, BaseCheckpointSaver checkpointSaver) {
        log.info("GraphRunner Day5 启动: symbol={}", symbol);

        final CompiledGraph<QuantAgentState> compiledGraph;
        try {
            compiledGraph = stateGraph.compileDay5(checkpointSaver);
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("图编译失败: " + e.getMessage(), e);
        }

        // 生成 threadId（同一 symbol 的请求可追踪）
        String threadId = "day5-" + symbol + "-" + UUID.randomUUID().toString().substring(0, 8);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Optional<QuantAgentState> output = compiledGraph.invoke(Map.of(StateKeys.SYMBOL, symbol), config);

        QuantAgentState finalState = output.orElseThrow(
                () -> new IllegalStateException("图执行无输出: symbol=" + symbol));

        log.info("GraphRunner Day5 完成: symbol={}, threadId={}, results={}, reviewResult={}",
                symbol, threadId, finalState.results(), finalState.value(StateKeys.REVIEW_RESULT));
        return finalState;
    }

    /**
     * Day 7 新增：从 Checkpoint 恢复续传。
     *
     * <p>当 {@link #runDay5(String, BaseCheckpointSaver)} 执行中断（进程崩溃/网络故障），
     * 用户/调度器可持 threadId 调此方法恢复。框架加载最近一次 Checkpoint，
     * 从断点下一节点继续执行，不重复已完成节点。
     *
     * @param threadId        执行线程 ID（runDay5 时生成）
     * @param checkpointSaver 与 runDay5 时相同的存储实现
     * @return 最终 State
     * @throws IllegalStateException Checkpoint 不存在或恢复失败
     */
    public QuantAgentState resumeDay5(String threadId, BaseCheckpointSaver checkpointSaver) {
        log.info("GraphRunner Day5 恢复: threadId={}", threadId);

        final CompiledGraph<QuantAgentState> compiledGraph;
        try {
            compiledGraph = stateGraph.compileDay5(checkpointSaver);
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("图编译失败: " + e.getMessage(), e);
        }

        // 构造恢复配置：带 threadId，框架自动加载最近 Checkpoint
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Optional<QuantAgentState> output = compiledGraph.invoke(Map.of(StateKeys.SYMBOL, "RESUME"), config);

        QuantAgentState finalState = output.orElseThrow(
                () -> new IllegalStateException("图恢复无输出: threadId=" + threadId));

        log.info("GraphRunner Day5 恢复完成: threadId={}, results={}, reviewResult={}",
                threadId, finalState.results(), finalState.value(StateKeys.REVIEW_RESULT));
        return finalState;
    }
}
