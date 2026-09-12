package com.quant.agent.application.planner;

import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PlannerService 单元测试。
 *
 * <p>验证：校验逻辑 + 重试策略 + 失败路径。
 * PlannerAiService 用 mock 隔离，只测服务本身。
 */
class PlannerServiceTest {

    /**
     * 正常路径：LLM 返回合法 Task 列表，服务返回。
     */
    @Test
    void shouldReturnTasksWhenLlmReturnsValidList() {
        // Fixture：合法的 Task 列表
        List<Task> validTasks = List.of(
                Task.of(TaskType.ANALYSIS, "600519"),
                Task.of(TaskType.DATA_FETCH, "600519")
        );
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan("600519")).thenReturn(validTasks);

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("600519");

        // 验证：返回合法列表
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(TaskType.ANALYSIS, result.get(0).type());

        // 验证：只调用了 1 次（合法不重试）
        verify(aiService, times(1)).plan("600519");
    }

    /**
     * 失败路径：LLM 返回空列表，重试耗尽后抛异常。
     */
    @Test
    void shouldThrowAfterRetriesWhenLlmReturnsEmptyList() {
        // Fixture：空列表
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan("600519")).thenReturn(List.of());

        PlannerService service = new PlannerService(aiService);

        // 重试 2 次后仍空 → 抛明确异常
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.plan("600519")
        );
        assertTrue(exception.getMessage().contains("规划失败"));

        // 验证：调用了 2 次（重试 2 次）
        verify(aiService, times(2)).plan("600519");
    }

    /**
     * 失败路径：LLM 返回包含 null target 的 Task，视为非法。
     */
    @Test
    void shouldRetryWhenTaskTargetIsNull() {
        // Fixture：Task 的 target 为 null（非法）
        Task invalidTask = new Task(TaskType.ANALYSIS, null, Map.of());
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan("600519")).thenReturn(List.of(invalidTask));

        PlannerService service = new PlannerService(aiService);

        // 非法 → 重试 2 次后抛异常
        assertThrows(IllegalStateException.class, () -> service.plan("600519"));
        verify(aiService, times(2)).plan("600519");
    }

    /**
     * 失败路径：LLM 调用抛异常（网络/反序列化失败）。
     */
    @Test
    void shouldRetryWhenLlmThrowsException() {
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan("600519")).thenThrow(new RuntimeException("LLM 网络超时"));

        PlannerService service = new PlannerService(aiService);

        assertThrows(IllegalStateException.class, () -> service.plan("600519"));
        verify(aiService, times(2)).plan("600519");
    }

    /**
     * 重试恢复：第 1 次异常，第 2 次合法，最终成功。
     */
    @Test
    void shouldSucceedAfterRetry() {
        List<Task> validTasks = List.of(Task.of(TaskType.ANALYSIS, "600519"));
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan("600519"))
                .thenThrow(new RuntimeException("临时错误"))  // 第 1 次异常
                .thenReturn(validTasks);                       // 第 2 次合法

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("600519");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(aiService, times(2)).plan("600519");
    }
}
