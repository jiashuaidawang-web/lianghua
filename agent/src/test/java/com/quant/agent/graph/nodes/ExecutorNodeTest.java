package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.graph.handlers.TaskHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ExecutorNode 单元测试。
 *
 * <p>验证：节点按 TaskType 分发到对应 Handler 执行，结果写入 State。
 * Handler 用 mock 隔离，只测节点的分发逻辑。
 */
class ExecutorNodeTest {

    /**
     * 正常路径：2 个 Task 分别分发到对应 Handler。
     */
    @Test
    void shouldDispatchTasksToHandlers() {
        // Fixture：两个 mock Handler
        TaskHandler analysisHandler = mock(TaskHandler.class);
        when(analysisHandler.type()).thenReturn(TaskType.ANALYSIS);
        when(analysisHandler.handle(any())).thenReturn("分析结果: BUY 8.5");

        TaskHandler dataFetchHandler = mock(TaskHandler.class);
        when(dataFetchHandler.type()).thenReturn(TaskType.DATA_FETCH);
        when(dataFetchHandler.handle(any())).thenReturn("价格=1500, 基本面=pe30");

        // 构造 ExecutorNode，注入 mock Handlers
        ExecutorNode node = new ExecutorNode(List.of(analysisHandler, dataFetchHandler));

        // 初始 State：有 2 个 Task
        List<Task> tasks = List.of(
                Task.of(TaskType.ANALYSIS, "600519"),
                Task.of(TaskType.DATA_FETCH, "600519")
        );
        QuantAgentState state = new QuantAgentState(Map.of("tasks", tasks));

        // 执行节点
        Map<String, Object> updates = node.apply(state);

        // 验证：RESULTS 写入
        assertTrue(updates.containsKey("results"));
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) updates.get("results");
        assertEquals("分析结果: BUY 8.5", results.get("ANALYSIS"));
        assertEquals("价格=1500, 基本面=pe30", results.get("DATA_FETCH"));

        // 验证：每个 Handler 的 handle 被调了 1 次
        verify(analysisHandler, times(1)).handle(any());
        verify(dataFetchHandler, times(1)).handle(any());
    }

    /**
     * 边界路径：Task 的 type 没有对应 Handler，跳过不报错。
     */
    @Test
    void shouldSkipUnknownTaskType() {
        // 只有 ANALYSIS Handler，没有 REPORT Handler
        TaskHandler analysisHandler = mock(TaskHandler.class);
        when(analysisHandler.type()).thenReturn(TaskType.ANALYSIS);
        when(analysisHandler.handle(any())).thenReturn("分析结果");

        ExecutorNode node = new ExecutorNode(List.of(analysisHandler));

        // Task 列表包含一个没有 Handler 的 REPORT
        List<Task> tasks = List.of(
                Task.of(TaskType.ANALYSIS, "600519"),
                Task.of(TaskType.REPORT, "600519")  // ← 没有 Handler
        );
        QuantAgentState state = new QuantAgentState(Map.of("tasks", tasks));

        // 执行节点（不应该抛异常）
        Map<String, Object> updates = assertDoesNotThrow(() -> node.apply(state));

        // 验证：RESULTS 有 ANALYSIS 的结果
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) updates.get("results");
        assertEquals("分析结果", results.get("ANALYSIS"));

        // REPORT 没有 Handler → 结果里应该有"未知类型"标记
        assertTrue(results.get("REPORT").contains("未知类型"));
    }
}
