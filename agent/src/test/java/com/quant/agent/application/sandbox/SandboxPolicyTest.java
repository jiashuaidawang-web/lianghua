package com.quant.agent.application.sandbox;

import com.quant.agent.domain.sandbox.SandboxLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// ============================================================================================
// 【Day 11 · JUnit 5】SandboxPolicyTest —— 脚本"安检门"的单元测试。
// ============================================================================================

/**
 * SandboxPolicy 的确定性校验测试。
 *
 * <p>覆盖：合规脚本、各黑名单模式、空脚本、超长脚本。
 */
class SandboxPolicyTest {

    @Test
    void nullOrBlankScript_isRejected() {
        assertNotNull(SandboxPolicy.validate(null));
        assertNotNull(SandboxPolicy.validate(""));
        assertNotNull(SandboxPolicy.validate("   "));
    }

    @Test
    void overlyLongScript_isRejected() {
        String huge = "x".repeat(100_001); // > MAX_SCRIPT_LENGTH
        assertNotNull(SandboxPolicy.validate(huge));
    }

    @Test
    void harmlessPythonFactor_isAllowed() {
        // 典型的合法因子计算：纯数学运算，无危险调用
        String script = """
                import json
                data = json.load(open('/sandbox/input.json'))
                pe = data['pe']
                result = {"score": 100.0 / pe}
                print(json.dumps(result))
                """;
        assertNull(SandboxPolicy.validate(script), "纯计算脚本应通过");
    }

    @Test
    void osSystem_isRejected() {
        assertNotNull(SandboxPolicy.validate("import os\nos.system('rm -rf /')"));
    }

    @Test
    void subprocess_isRejected() {
        assertNotNull(SandboxPolicy.validate("from subprocess import call\ncall(['ls'])"));
    }

    @Test
    void evalAndExec_areRejected() {
        assertNotNull(SandboxPolicy.validate("eval(malicious)"));
        assertNotNull(SandboxPolicy.validate("exec(malicious_code)"));
    }

    @Test
    void socketAccess_isRejected() {
        assertNotNull(SandboxPolicy.validate("import socket\ns = socket.socket()"));
    }

    @Test
    void urllibAccess_isRejected() {
        assertNotNull(SandboxPolicy.validate("import urllib.request\nurllib.request.urlopen('http://evil')"));
    }

    @Test
    void requestsAccess_isRejected() {
        assertNotNull(SandboxPolicy.validate("import requests\nrequests.get('http://evil')"));
    }

    @Test
    void dangerousPaths_areRejected() {
        assertNotNull(SandboxPolicy.validate("open('/etc/passwd').read()"));
        assertNotNull(SandboxPolicy.validate("open('/root/secret').read()"));
    }

    @Test
    void caseInsensitiveDenylist_rejectsMixedCase() {
        // 大小写混写绕过 → 仍应被拒（正则带 CASE_INSENSITIVE）
        assertNotNull(SandboxPolicy.validate("IMPORT SOCKET"));
        assertNotNull(SandboxPolicy.validate("Eval(x)"));
    }

    @Test
    void limitsFactory_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> SandboxLimits.of(0, 256, 1.0, 64, 64, false, 65536));
        assertThrows(IllegalArgumentException.class, () -> SandboxLimits.of(1000, -1, 1.0, 64, 64, false, 65536));
    }

    @Test
    void limitsFactory_acceptsValidValues() {
        SandboxLimits limits = SandboxLimits.of(5000, 128, 0.5, 32, 32, false, 1024);
        assertEquals(5000, limits.timeoutMs());
        assertEquals(128, limits.memoryMb());
    }

    @Test
    void defaultLimits_areReasonable() {
        SandboxLimits d = SandboxLimits.DEFAULT;
        assertTrue(d.timeoutMs() > 0);
        assertFalse(d.networkAllowed(), "默认应无网络");
        assertTrue(d.maxOutputBytes() > 0);
    }
}
