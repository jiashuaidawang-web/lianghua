package com.quant.agent.graph.handlers;

import com.quant.agent.application.sandbox.SandboxService;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.infrastructure.sandbox.FakeSandboxExecutor;
import com.quant.agent.infrastructure.sandbox.SandboxExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 11 · JUnit 5】SandboxTaskHandlerTest —— SANDBOX 任务 Handler 的单元测试。
// ============================================================================================

/**
 * SandboxTaskHandler 的测试。
 *
 * <p>覆盖：正常执行、缺 script、危险脚本被拒绝、配额解析、未知类型不匹配。
 */
class SandboxTaskHandlerTest {

    private static final String SAFE_SCRIPT = """
            import json
            print(json.dumps({"result": 42}))
            """;

    private SandboxTaskHandler handler(SandboxService service) {
        return new SandboxTaskHandler(service);
    }

    @Test
    void type_returnsSandbox() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));
        assertEquals(TaskType.SANDBOX, handler.type());
    }

    @Test
    void handle_happyPath_returnsSuccessText() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        Task task = new Task(TaskType.SANDBOX, "因子计算",
                Map.of("script", SAFE_SCRIPT, "language", "python",
                        "inputData", Map.of("pe", 30)));

        String output = handler.handle(task);

        assertTrue(output.contains("[SANDBOX OK]"), "应成功: " + output);
    }

    @Test
    void handle_missingScript_returnsErrorText() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        Task task = new Task(TaskType.SANDBOX, "因子计算", Map.of(/* 没有 script */));

        String output = handler.handle(task);

        assertTrue(output.contains("[SANDBOX ERROR]"), "缺 script 应报错: " + output);
        assertTrue(output.contains("缺少 script"), "应说明缺少 script: " + output);
    }

    @Test
    void handle_dangerousScript_isRejected() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        Task task = new Task(TaskType.SANDBOX, "恶意",
                Map.of("script", "import socket; socket.socket()"));

        String output = handler.handle(task);

        assertTrue(output.contains("[SANDBOX REJECTED]"), "危险脚本应被拒绝: " + output);
    }

    @Test
    void handle_parsesLimitsFromParams() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        Task task = new Task(TaskType.SANDBOX, "因子计算",
                Map.of("script", SAFE_SCRIPT,
                        "limits", Map.of("memoryMb", 512, "timeoutMs", 60000)));

        // 配额解析失败会回退 DEFAULT，不会抛异常
        String output = handler.handle(task);
        assertTrue(output.contains("[SANDBOX OK]"),
                "应成功（无论配额是否解析）: " + output);
    }

    @Test
    void handle_invalidLimitsFallbackToDefault() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        // limits 是非法值（负数）→ 解析时抛异常 → 回退 DEFAULT
        Task task = new Task(TaskType.SANDBOX, "因子计算",
                Map.of("script", SAFE_SCRIPT,
                        "limits", Map.of("memoryMb", -1)));

        String output = handler.handle(task);
        assertTrue(output.contains("[SANDBOX OK]"), "非法配额应回退 DEFAULT 并成功: " + output);
    }

    @Test
    void handle_executorException_doesNotThrow() {
        // 一个会抛异常的 executor
        SandboxExecutor boomExecutor = (limits, script, language, inputData) -> {
            throw new RuntimeException("kaboom");
        };
        SandboxTaskHandler handler = handler(new SandboxService(boomExecutor));

        Task task = new Task(TaskType.SANDBOX, "因子计算", Map.of("script", SAFE_SCRIPT));

        // 不应上抛，应捕获后返回错误文本
        String output = handler.handle(task);
        assertTrue(output.contains("[SANDBOX ERROR]"), "异常应被捕获: " + output);
    }

    @Test
    void handle_languageDefaultsToPython() {
        SandboxTaskHandler handler = handler(new SandboxService(FakeSandboxExecutor.alwaysSuccess()));

        // 不传 language → 默认 python
        Task task = new Task(TaskType.SANDBOX, "因子计算", Map.of("script", SAFE_SCRIPT));

        String output = handler.handle(task);
        assertTrue(output.contains("[SANDBOX OK]"), "默认语言 python 应成功: " + output);
    }

    @Test
    void handle_preservesTargetAndParams() {
        Task task = new Task(TaskType.SANDBOX, "因子计算", Map.of("script", SAFE_SCRIPT));
        assertEquals("因子计算", task.target());
        assertEquals(SAFE_SCRIPT, task.getParam("script", String.class));
        assertFalse(task.params().isEmpty(), "params 应可访问");
    }
}
