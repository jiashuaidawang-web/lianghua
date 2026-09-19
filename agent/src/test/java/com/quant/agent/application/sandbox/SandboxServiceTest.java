package com.quant.agent.application.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.sandbox.SandboxResult;
import com.quant.agent.infrastructure.sandbox.FakeSandboxExecutor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 11 · JUnit 5】SandboxServiceTest —— 沙盒应用服务的单元测试。
// ============================================================================================

/**
 * SandboxService 的测试。
 *
 * <p>用 FakeSandboxExecutor 替身，不启动容器。覆盖：
 * 正常路径、策略拒绝、超时、基础设施错误、配额缺省。
 */
class SandboxServiceTest {

    private static final String SAFE_SCRIPT = """
            import json
            print(json.dumps({"ok": True}))
            """;

    @Test
    void happyPath_returnsSuccessText() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        String output = service.run(SAFE_SCRIPT, "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX OK]"), "应标记成功: " + output);
        assertTrue(output.contains("exitCode=0"), "应包含退出码: " + output);
        assertTrue(output.contains("elapsed="), "应包含耗时: " + output);
    }

    @Test
    void dangerousScript_isRejectedBeforeContainerStarts() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        String output = service.run("import os; os.system('rm -rf /')", "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX REJECTED]"), "危险脚本应被拒绝: " + output);
        assertTrue(output.contains("安全策略拒绝"), "应说明是策略拒绝: " + output);
    }

    @Test
    void blankScript_isRejected() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        String output = service.run("   ", "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX REJECTED]"));
    }

    @Test
    void timeoutFromExecutor_isReported() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysTimeout());

        String output = service.run(SAFE_SCRIPT, "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX TIMEOUT]"), "应标记超时: " + output);
    }

    @Test
    void infrastructureError_isReported() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysError());

        String output = service.run(SAFE_SCRIPT, "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX ERROR]"), "应标记错误: " + output);
        assertTrue(output.contains("基础设施错误"), "应说明是基础设施错误: " + output);
    }

    @Test
    void unsupportedLanguage_isReportedAsError() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        String output = service.run(SAFE_SCRIPT, "ruby", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("[SANDBOX ERROR]"), "不支持的语言应报错: " + output);
        assertTrue(output.contains("不支持的语言"), "应说明语言不支持: " + output);
    }

    @Test
    void nullLimits_fallsBackToDefault() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        // limits = null 不应抛异常，应回退到 DEFAULT
        String output = service.run(SAFE_SCRIPT, "python", Map.of(), null);

        assertTrue(output.contains("[SANDBOX OK]"), "配额缺省时应回退 DEFAULT 并成功: " + output);
    }

    @Test
    void resultContainsStdout() {
        SandboxService service = new SandboxService(FakeSandboxExecutor.alwaysSuccess());

        String output = service.run(SAFE_SCRIPT, "python", Map.of(), SandboxLimits.DEFAULT);

        assertTrue(output.contains("stdout:"), "成功时应回显 stdout: " + output);
    }

    @Test
    void securityRejectedResult_isNotOk() {
        SandboxResult result = SandboxResult.securityRejected("命中危险模式");
        assertFalse(result.ok());
        assertTrue(result.securityRejected());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void timedOutResult_isNotOk() {
        SandboxResult result = SandboxResult.timedOut("", "", 1000L, false);
        assertFalse(result.ok());
        assertTrue(result.timedOut());
    }

    @Test
    void successResult_isOk() {
        SandboxResult result = SandboxResult.success(0, "out", "", 10L, false);
        assertTrue(result.ok());
    }
}
