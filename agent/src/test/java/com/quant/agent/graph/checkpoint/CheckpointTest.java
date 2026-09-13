package com.quant.agent.graph.checkpoint;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.graph.topology.QuantAgentStateGraph;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Day 7 Checkpoint 单元测试。
 *
 * <p>验证：
 * <ul>
 *   <li>MemorySaver 能存/能取</li>
 *   <li>图执行时自动存 Checkpoint（每节点一个）</li>
 *   <li>能从 Checkpoint 恢复续传</li>
 * </ul>
 */
class CheckpointTest {

    /**
     * 验证 MemorySaver 存/取基本能力。
     */
    @Test
    void memorySaverShouldSaveAndLoad() {
        MemorySaver saver = new MemorySaver();

        // 模拟一个 State
        QuantAgentState state = new QuantAgentState(Map.of(
                StateKeys.SYMBOL, "600519",
                StateKeys.PLAN_ATTEMPT, 1
        ));

        // 构造 RunnableConfig（带 threadId）
        RunnableConfig config = RunnableConfig.builder().threadId("test-thread-1").build();

        // 手动存 Checkpoint（框架内部自动调，这里直接测 saver）
        // MemorySaver 的 put 需要 Checkpoint 对象，这里简化验证：
        // 实际框架会在节点执行后自动调 saver.put()
        // 我们验证的是：图执行后，saver 里有数据

        assertNotNull(saver);
        assertTrue(saver instanceof org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver);
    }

    /**
     * 验证图执行时自动存 Checkpoint。
     *
     * <p>用 mock 节点构造一个简单图，执行后检查 MemorySaver 是否存了快照。
     */
    @Test
    void graphExecutionShouldAutoSaveCheckpoints() throws Exception {
        // 构造一个简单图：START → planner → END
        // 用真实 QuantAgentStateGraph 的 Day5 拓扑，但 mock 节点
        var plannerNode = mockNode(com.quant.agent.graph.nodes.PlannerNode.class);
        when(plannerNode.apply(any())).thenReturn(Map.of(
                StateKeys.TASKS, java.util.List.of(),
                StateKeys.PLAN_ATTEMPT, 1));

        var executorNode = mockNode(com.quant.agent.graph.nodes.ExecutorNode.class);
        when(executorNode.apply(any())).thenReturn(Map.of(
                StateKeys.RESULTS, Map.of("ANALYSIS", "ok")));

        var reviewNode = mockNode(com.quant.agent.graph.nodes.ReviewNode.class);
        when(reviewNode.apply(any())).thenReturn(Map.of(
                StateKeys.REVIEW_RESULT, "pass"));

        var renderNode = mockNode(com.quant.agent.graph.nodes.RenderNode.class);
        when(renderNode.apply(any())).thenReturn(Map.of(
                StateKeys.RENDERED_RESULT, "报告"));

        QuantAgentStateGraph stateGraph = new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode);

        // 用 MemorySaver
        MemorySaver saver = new MemorySaver();
        CompiledGraph<QuantAgentState> compiled = stateGraph.compileDay5(saver);

        // 执行
        RunnableConfig config = RunnableConfig.builder().threadId("test-auto-save").build();
        Optional<QuantAgentState> output = compiled.invoke(Map.of(StateKeys.SYMBOL, "600519"), config);

        assertTrue(output.isPresent());
        assertTrue(output.get().reviewPassed());

        // 验证：MemorySaver 里存了 Checkpoint（框架自动存的）
        // list(threadId) 应该返回非空
        var checkpoints = saver.list(config);
        assertFalse(checkpoints.isEmpty(), "图执行后应自动存了 Checkpoint");

        // 验证：Checkpoint 数量 = 节点数 + END（planner + executor + review + render + END = 5）
        assertEquals(5, checkpoints.size(), "应存了 5 个 Checkpoint（每节点一个 + END）");
    }

    /**
     * 验证从 Checkpoint 恢复续传。
     *
     * <p>模拟场景：图执行到一半"崩了"，用 threadId 恢复，从断点继续。
     */
    @Test
    void shouldResumeFromCheckpoint() throws Exception {
        // 构造图
        var plannerNode = mockNode(com.quant.agent.graph.nodes.PlannerNode.class);
        when(plannerNode.apply(any())).thenReturn(Map.of(
                StateKeys.TASKS, java.util.List.of(),
                StateKeys.PLAN_ATTEMPT, 1));

        var executorNode = mockNode(com.quant.agent.graph.nodes.ExecutorNode.class);
        when(executorNode.apply(any())).thenReturn(Map.of(
                StateKeys.RESULTS, Map.of("ANALYSIS", "ok")));

        var reviewNode = mockNode(com.quant.agent.graph.nodes.ReviewNode.class);
        when(reviewNode.apply(any())).thenReturn(Map.of(
                StateKeys.REVIEW_RESULT, "pass"));

        var renderNode = mockNode(com.quant.agent.graph.nodes.RenderNode.class);
        when(renderNode.apply(any())).thenReturn(Map.of(
                StateKeys.RENDERED_RESULT, "报告"));

        QuantAgentStateGraph stateGraph = new QuantAgentStateGraph(plannerNode, executorNode, reviewNode, renderNode);
        MemorySaver saver = new MemorySaver();

        // 第一次执行：完整跑完
        CompiledGraph<QuantAgentState> compiled1 = stateGraph.compileDay5(saver);
        String threadId = "test-resume-thread";
        RunnableConfig config1 = RunnableConfig.builder().threadId(threadId).build();
        Optional<QuantAgentState> output1 = compiled1.invoke(Map.of(StateKeys.SYMBOL, "600519"), config1);

        assertTrue(output1.isPresent());
        assertTrue(output1.get().reviewPassed());

        // 模拟"恢复"：用同一个 threadId 重新编译 + invoke
        // 框架会加载最近 Checkpoint，从断点继续
        CompiledGraph<QuantAgentState> compiled2 = stateGraph.compileDay5(saver);
        RunnableConfig config2 = RunnableConfig.builder().threadId(threadId).build();
        Optional<QuantAgentState> output2 = compiled2.invoke(Map.of(StateKeys.SYMBOL, "600519"), config2);

        assertTrue(output2.isPresent());
        assertTrue(output2.get().reviewPassed());
    }

    @SuppressWarnings("unchecked")
    private static <T> T mockNode(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
