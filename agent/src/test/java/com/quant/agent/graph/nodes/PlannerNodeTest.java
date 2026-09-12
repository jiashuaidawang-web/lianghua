package com.quant.agent.graph.nodes;

import com.quant.agent.application.planner.PlannerService;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PlannerNode 单元测试。
 *
 * <p>验证：节点正确调 PlannerService，结果写入 State。
 * PlannerService 内部逻辑用 mock 隔离，只测节点本身。
 */
class PlannerNodeTest {

    /**
     * 正常路径：PlannerService 返回合法 Task 列表，节点写入 State。
     */
    @Test
    void shouldWriteTasksToState() {
        // Fixture：PlannerService 返回 2 个 Task
        List<Task> expectedTasks = List.of(
                Task.of(TaskType.ANALYSIS, "600519"),
                Task.of(TaskType.DATA_FETCH, "600519")
        );
        PlannerService plannerService = mock(PlannerService.class);
        when(plannerService.plan("600519")).thenReturn(expectedTasks);

        PlannerNode node = new PlannerNode(plannerService);

        // 初始 State：只有 symbol
        QuantAgentState state = new QuantAgentState(Map.of("symbol", "600519"));

        // 执行节点
        Map<String, Object> updates = node.apply(state);

        // 验证：TASKS 写入
        assertTrue(updates.containsKey("tasks"));
        assertEquals(expectedTasks, updates.get("tasks"));

        // 验证：PLAN_ATTEMPT = 1（第一次规划）
        assertEquals(1, updates.get("planAttempt"));

        // 验证：PlannerService.plan 被调了 1 次
        verify(plannerService, times(1)).plan("600519");
    }

    /**
     * 失败路径：PlannerService 抛异常，节点写 ERROR_MESSAGE。
     */
    @Test
    void shouldWriteErrorMessageOnFailure() {
        PlannerService plannerService = mock(PlannerService.class);
        when(plannerService.plan("600519"))
                .thenThrow(new IllegalStateException("LLM 调用失败"));

        PlannerNode node = new PlannerNode(plannerService);

        QuantAgentState state = new QuantAgentState(Map.of("symbol", "600519"));

        Map<String, Object> updates = node.apply(state);

        // 验证：ERROR_MESSAGE 写入
        assertTrue(updates.containsKey("errorMessage"));
        assertTrue(updates.get("errorMessage").toString().contains("LLM 调用失败"));
    }
}
