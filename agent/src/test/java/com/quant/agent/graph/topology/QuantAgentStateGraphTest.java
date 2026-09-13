package com.quant.agent.graph.topology;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.graph.nodes.AnalysisNode;
import com.quant.agent.graph.nodes.ExecutorNode;
import com.quant.agent.graph.nodes.OutputNode;
import com.quant.agent.graph.nodes.PlannerNode;
import com.quant.agent.graph.nodes.RenderNode;
import com.quant.agent.graph.nodes.ReviewNode;
import com.quant.agent.graph.nodes.ToolNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QuantAgentStateGraph 单元测试。
 *
 * <p>验证：图编译成功、条件边路由正确、Day5 重规划循环。
 * Node 内部逻辑用 mock 隔离，只测图拓扑本身。
 */
class QuantAgentStateGraphTest {

    // ========================================================================
    // Day 4 拓扑测试（固定流程）
    // ========================================================================

    /**
     * 构造 Day4 图，AnalysisNode 的行为由 mock 控制。
     */
    private CompiledGraph<QuantAgentState> compileDay4Graph(boolean needsTool) throws Exception {
        AnalysisNode analysisNode = mock(AnalysisNode.class);
        when(analysisNode.apply(any())).thenReturn(Map.of(
                "analysisResult", "BUY 8.5",
                "needsTool", needsTool));

        ToolNode toolNode = mock(ToolNode.class);
        when(toolNode.apply(any())).thenReturn(Map.of("toolData", "price=1500"));

        OutputNode outputNode = new OutputNode();

        QuantAgentStateGraph graph = new QuantAgentStateGraph(analysisNode, toolNode, outputNode);
        return graph.compileDay4();
    }

    @Test
    void shouldCompileDay4Successfully() throws Exception {
        // 图结构合法 → compile 不抛异常
        assertDoesNotThrow(() -> compileDay4Graph(false));
    }

    @Test
    void shouldRouteToToolNodeWhenNeedsToolTrue() throws Exception {
        // needsTool=true → 走 analysis → tool → output
        CompiledGraph<QuantAgentState> compiled = compileDay4Graph(true);

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertNotNull(state.finalResult());
        assertTrue(state.finalResult().contains("toolData")
                || state.finalResult().contains("数据"), "应经过 toolNode，包含工具数据");
    }

    @Test
    void shouldRouteDirectlyToOutputWhenNeedsToolFalse() throws Exception {
        // needsTool=false → 走 analysis → output（跳过 toolNode）
        CompiledGraph<QuantAgentState> compiled = compileDay4Graph(false);

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertNull(state.toolData(), "needsTool=false 时不应经过 toolNode");
        assertNotNull(state.finalResult());
    }

    // ========================================================================
    // Day 5 拓扑测试（动态规划 + 重规划循环）
    // ========================================================================

    /**
     * 构造 Day5 图，三个节点全用 mock 控制。
     *
     * @param reviewResults 按次序列举每次 reviewNode 返回的 reviewResult（"pass"/"fail"），
     *                      用于模拟「第 1 次 fail → 重规划 → 第 2 次 pass」的循环。
     */
    private CompiledGraph<QuantAgentState> compileDay5Graph(String... reviewResults) throws Exception {
        List<Task> tasks = List.of(
                Task.of(TaskType.ANALYSIS, "600519"),
                Task.of(TaskType.DATA_FETCH, "600519")
        );

        PlannerNode plannerNode = mock(PlannerNode.class);
        when(plannerNode.apply(any())).thenReturn(Map.of(
                "tasks", tasks,
                "planAttempt", 1));

        ExecutorNode executorNode = mock(ExecutorNode.class);
        when(executorNode.apply(any())).thenReturn(Map.of(
                "results", Map.of(
                        "ANALYSIS", "操作=BUY, 评分=8.5, 理由=业绩稳健",
                        "DATA_FETCH", "价格=1500, 基本面=pe30")));

        ReviewNode reviewNode = mock(ReviewNode.class);
        // 按调用次序返回：第 1 次 reviewResults[0]，第 2 次 reviewResults[1]……
        when(reviewNode.apply(any())).thenReturn(
                Map.of("reviewResult", reviewResults[0]),
                java.util.Arrays.stream(reviewResults)
                        .skip(1)
                        .map(r -> Map.of("reviewResult", (Object) r))
                        .toArray(Map[]::new));

        // renderNode：拓扑测试只关心路由，渲染节点用 mock  stub 即可
        RenderNode renderNode = mock(RenderNode.class);
        when(renderNode.apply(any())).thenReturn(Map.of("renderedResult", "（渲染结果）"));

        QuantAgentStateGraph graph = new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode);
        return graph.compileDay5();
    }

    @Test
    void shouldCompileDay5Successfully() throws Exception {
        // Day5 图结构合法 → compileDay5 不抛异常
        assertDoesNotThrow(() -> compileDay5Graph("pass"));
    }

    @Test
    void shouldPassThroughDay5FlowWhenReviewPasses() throws Exception {
        // review 一次就 pass → planner → executor → review → END（无循环）
        CompiledGraph<QuantAgentState> compiled = compileDay5Graph("pass");

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertTrue(state.reviewPassed(), "review 应通过");
        assertFalse(state.results().isEmpty(), "results 应有执行结果");
    }

    @Test
    void shouldReplanOnceWhenReviewFailsThenPasses() throws Exception {
        // 第 1 次 review fail → 回到 planner 重规划 → 第 2 次 review pass → END
        // 这是 Day5 最核心的特征：重规划循环
        CompiledGraph<QuantAgentState> compiled = compileDay5Graph("fail", "pass");

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        QuantAgentState state = output.get();
        assertTrue(state.reviewPassed(), "最终 review 应通过");
    }

    /**
     * 精确验证重规划循环的节点调用次数。
     *
     * <p>单独测试是因为需要持有 mock 引用才能 verify（compileDay5Graph 内部创建的 mock 不可见）。
     */
    @Test
    void shouldReplanLoopInvokeNodesCorrectTimes() throws Exception {
        List<Task> tasks = List.of(Task.of(TaskType.ANALYSIS, "600519"));

        PlannerNode plannerNode = mock(PlannerNode.class);
        when(plannerNode.apply(any())).thenReturn(Map.of("tasks", tasks, "planAttempt", 1));

        ExecutorNode executorNode = mock(ExecutorNode.class);
        when(executorNode.apply(any())).thenReturn(Map.of("results", Map.of("ANALYSIS", "ok")));

        // 第 1 次 fail，第 2 次 pass
        ReviewNode reviewNode = mock(ReviewNode.class);
        when(reviewNode.apply(any()))
                .thenReturn(Map.of("reviewResult", "fail"))
                .thenReturn(Map.of("reviewResult", "pass"));

        // renderNode：拓扑测试只关心路由，渲染节点用 mock stub 即可
        RenderNode renderNode = mock(RenderNode.class);
        when(renderNode.apply(any())).thenReturn(Map.of("renderedResult", "（渲染结果）"));

        QuantAgentStateGraph graph = new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode);
        CompiledGraph<QuantAgentState> compiled = graph.compileDay5();

        Optional<QuantAgentState> output = compiled.invoke(Map.of("symbol", "600519"));

        assertTrue(output.isPresent());
        assertTrue(output.get().reviewPassed());

        // 精确计数：循环 1 次 → planner/executor/review 各 2 次
        verify(plannerNode, times(2)).apply(any());
        verify(executorNode, times(2)).apply(any());
        verify(reviewNode, times(2)).apply(any());
    }
}
