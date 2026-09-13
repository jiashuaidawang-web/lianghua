package com.quant.agent.application.planner;

import com.quant.agent.domain.task.Plan;
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
        when(aiService.plan(anyString())).thenReturn(new Plan(validTasks));

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("600519");

        // 验证：返回合法列表
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(TaskType.ANALYSIS, result.get(0).type());

        // 验证：只调用了 1 次（合法不重试）
        verify(aiService, times(1)).plan(anyString());
    }

    /**
     * 失败路径：LLM 返回空列表，重试耗尽后抛异常。
     */
    @Test
    void shouldThrowAfterRetriesWhenLlmReturnsEmptyList() {
        // Fixture：空列表
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(anyString())).thenReturn(new Plan(List.of()));

        PlannerService service = new PlannerService(aiService);

        // 重试 2 次后仍空 → 抛明确异常
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.plan("600519")
        );
        assertTrue(exception.getMessage().contains("规划失败"));

        // 验证：调用了 2 次（重试 2 次）
        verify(aiService, times(2)).plan(anyString());
    }

    /**
     * 边界路径：LLM 返回包含 null target 的 Task，但自然语言里有代码 → 兜底填充，不重试。
     *
     * <p>这是 fillTargetIfNeeded 的核心场景：LLM 忘了填 target，但 Java 正则已经提取到代码，
     * 直接补上，避免无谓重试。
     */
    @Test
    void shouldFillNullTargetFromInputWhenCodeExtractable() {
        // LLM 返回 target 为 null 的 Task
        Task invalidTask = new Task(TaskType.ANALYSIS, null, Map.of());
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(anyString())).thenReturn(new Plan(List.of(invalidTask)));

        PlannerService service = new PlannerService(aiService);

        // 输入含代码 600519 → 兜底填充 → 合法，不抛异常
        List<Task> result = assertDoesNotThrow(() -> service.plan("分析600519"));

        assertEquals(1, result.size());
        assertEquals("600519", result.get(0).target());  // ← 被兜底填充
        verify(aiService, times(1)).plan(anyString());  // 只调 1 次，没重试
    }

    /**
     * 失败路径：LLM 调用抛异常（网络/反序列化失败）。
     */
    @Test
    void shouldRetryWhenLlmThrowsException() {
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(anyString())).thenThrow(new RuntimeException("LLM 网络超时"));

        PlannerService service = new PlannerService(aiService);

        assertThrows(IllegalStateException.class, () -> service.plan("600519"));
        verify(aiService, times(2)).plan(anyString());
    }

    /**
     * 新增：自然语言请求含股票代码，提取后增强请求传给 LLM。
     */
    @Test
    void shouldExtractStockCodeFromNaturalLanguageAndEnhanceRequest() {
        List<Task> validTasks = List.of(
                Task.of(TaskType.ANALYSIS, "002909"),
                Task.of(TaskType.REPORT, "002909")
        );
        PlannerAiService aiService = mock(PlannerAiService.class);
        // 验证传给 LLM 的请求包含提取到的代码
        when(aiService.plan(contains("002909"))).thenReturn(new Plan(validTasks));

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("这个票值不值得买002909");

        assertEquals(2, result.size());
        // 验证传入 LLM 的请求被增强了（包含 "股票代码: 002909"）
        verify(aiService, times(1)).plan(contains("股票代码: 002909"));
    }

    /**
     * 新增：LLM 没填 target，用正则提取的代码兜底填充。
     */
    @Test
    void shouldFillTargetFromExtractedCodeWhenLlmOmitsIt() {
        // LLM 返回了 type 但没填 target
        Task taskWithoutTarget = new Task(TaskType.ANALYSIS, null, Map.of());
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(contains("600519"))).thenReturn(new Plan(List.of(taskWithoutTarget)));

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("分析一下600519");

        // target 被兜底填充为提取到的代码
        assertEquals(1, result.size());
        assertEquals("600519", result.get(0).target());
        assertEquals(TaskType.ANALYSIS, result.get(0).type());
    }

    /**
     * 新增：自然语言里没有股票代码，LLM 也没填 target → 重试耗尽后抛异常。
     */
    @Test
    void shouldThrowWhenNoStockCodeAnywhere() {
        // LLM 返回没 target 的请求，且自然语言里也没代码
        Task taskWithoutTarget = new Task(TaskType.ANALYSIS, null, Map.of());
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(anyString())).thenReturn(new Plan(List.of(taskWithoutTarget)));

        PlannerService service = new PlannerService(aiService);

        // 没代码可兜底 → target 为空 → 非法 → 重试 2 次后抛异常
        assertThrows(IllegalStateException.class, () -> service.plan("今天天气怎么样"));
        verify(aiService, times(2)).plan(anyString());
    }

    /**
     * 重试恢复：第 1 次异常，第 2 次合法，最终成功。
     */
    @Test
    void shouldSucceedAfterRetry() {
        List<Task> validTasks = List.of(Task.of(TaskType.ANALYSIS, "600519"));
        PlannerAiService aiService = mock(PlannerAiService.class);
        when(aiService.plan(anyString()))
                .thenThrow(new RuntimeException("临时错误"))  // 第 1 次异常
                .thenReturn(new Plan(validTasks));             // 第 2 次合法

        PlannerService service = new PlannerService(aiService);

        List<Task> result = service.plan("600519");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(aiService, times(2)).plan(anyString());
    }
}
