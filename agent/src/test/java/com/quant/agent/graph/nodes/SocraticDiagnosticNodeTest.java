package com.quant.agent.graph.nodes;

import com.quant.agent.application.diagnosis.SocraticDiagnoser;
import com.quant.agent.domain.diagnosis.DiagnosisResult;
import com.quant.agent.domain.diagnosis.DiagnosisStatus;
import com.quant.agent.domain.state.QuantAgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SocraticDiagnosticNode 单元测试。
 *
 * <p>验证：
 * 1. 正常路径（mock LLM 返回合法 JSON）→ DIAGNOSED + 合法 DiagnosisResult
 * 2. symptom 为空 → 直接进 HITL
 * 3. LLM 输出非法 category → VALIDATION_FAILED
 * 4. LLM 调用抛异常 → LLM_ERROR
 */
class SocraticDiagnosticNodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造 mock 的 ChatModel，返回固定的 LLM 响应。
     */
    private ChatModel mockChatModel(String llmResponse) {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(anyString())).thenReturn(llmResponse);
        return mockModel;
    }

    /**
     * 构造一个正常的初始 State（含 symptom）。
     */
    private QuantAgentState stateWithSymptom(String symptom) {
        Map<String, Object> initData = new HashMap<>();
        initData.put("symptom", symptom);
        initData.put("diagnosisStatus", DiagnosisStatus.PENDING.name());
        return new QuantAgentState(initData);
    }

    /**
     * 构造一个正常的初始 State（含 symptom + symbol + errorMessage）。
     */
    private QuantAgentState stateWithFullContext(String symptom, String symbol, String errorMessage) {
        Map<String, Object> initData = new HashMap<>();
        initData.put("symptom", symptom);
        initData.put("symbol", symbol);
        initData.put("errorMessage", errorMessage);
        initData.put("diagnosisStatus", DiagnosisStatus.PENDING.name());
        return new QuantAgentState(initData);
    }

    // ========================================================================
    // 正常路径测试
    // ========================================================================

    /**
     * 正常路径：mock LLM 返回合法 JSON（category=DATA）→ DIAGNOSED + 合法 DiagnosisResult。
     */
    @Test
    void shouldDiagnoseDataIssue() {
        // Fixture：LLM 返回合法 JSON
        String llmJson = """
                ```json
                {
                  "category": "DATA",
                  "hypothesis": "数据接口返回空",
                  "evidence": ["接口返回 200 但 data 字段为空", "重试仍为空"],
                  "confidence": 0.85,
                  "nextAction": "切换备用数据源"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("回测收益从 15% 跌到 -40%");

        // 执行
        Map<String, Object> updates = node.apply(state);

        // 验证：写入了 diagnosisResult + diagnosisStatus
        assertNotNull(updates);
        assertTrue(updates.containsKey("diagnosisResult"));
        assertTrue(updates.containsKey("diagnosisStatus"));

        DiagnosisResult result = (DiagnosisResult) updates.get("diagnosisResult");
        assertEquals("DATA", result.category());
        assertEquals(DiagnosisStatus.DIAGNOSED.name(), updates.get("diagnosisStatus"));
        assertTrue(result.isValid());
        assertEquals(0.85, result.confidence(), 0.001);
        assertFalse(result.evidence().isEmpty());
        assertTrue(result.isRetryable(), "DATA + confidence=0.85 应该可重规划");
    }

    /**
     * 正常路径：mock LLM 返回 STRATEGY category → 不可重规划。
     */
    @Test
    void shouldDiagnoseStrategyIssueNotRetryable() {
        String llmJson = """
                {
                  "category": "STRATEGY",
                  "hypothesis": "策略参数过拟合",
                  "evidence": ["回测曲线过于平滑", "样本外表现差"],
                  "confidence": 0.9,
                  "nextAction": "人工调整参数"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("回测过拟合");
        Map<String, Object> updates = node.apply(state);

        DiagnosisResult result = (DiagnosisResult) updates.get("diagnosisResult");
        assertEquals("STRATEGY", result.category());
        assertFalse(result.isRetryable(), "STRATEGY 问题应该进 HITL，不可自动重规划");
    }

    // ========================================================================
    // 边界路径测试
    // ========================================================================

    /**
     * symptom 为空 → 直接进 HITL，不调 LLM。
     */
    @Test
    void shouldHandleEmptySymptom() {
        ChatModel mockModel = mock(ChatModel.class);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom(null);

        Map<String, Object> updates = node.apply(state);

        // 验证：没调 LLM（verify never）
        verify(mockModel, never()).chat(anyString());
        // 验证：状态是 LLM_ERROR
        assertEquals("LLM_ERROR", updates.get("diagnosisStatus"));
    }

    // ========================================================================
    // 失败路径测试
    // ========================================================================

    /**
     * LLM 输出非法 category → VALIDATION_FAILED。
     */
    @Test
    void shouldHandleInvalidCategory() {
        String llmJson = """
                {
                  "category": "玄学问题",
                  "hypothesis": "风水不好",
                  "evidence": ["证据1"],
                  "confidence": 0.5,
                  "nextAction": "换方位"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("异常");
        Map<String, Object> updates = node.apply(state);

        DiagnosisResult result = (DiagnosisResult) updates.get("diagnosisResult");
        assertEquals("VALIDATION_FAILED", updates.get("diagnosisStatus"));
        assertFalse(result.isValid());
    }

    /**
     * LLM 输出 confidence 超出范围 → VALIDATION_FAILED。
     */
    @Test
    void shouldHandleConfidenceOutOfRange() {
        String llmJson = """
                {
                  "category": "DATA",
                  "hypothesis": "假设",
                  "evidence": ["证据1"],
                  "confidence": 1.5,
                  "nextAction": "动作"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("异常");
        Map<String, Object> updates = node.apply(state);

        assertEquals("VALIDATION_FAILED", updates.get("diagnosisStatus"));
    }

    /**
     * LLM 输出 evidence 为空 → VALIDATION_FAILED。
     */
    @Test
    void shouldHandleEmptyEvidence() {
        String llmJson = """
                {
                  "category": "CODE",
                  "hypothesis": "代码 bug",
                  "evidence": [],
                  "confidence": 0.8,
                  "nextAction": "修复"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("异常");
        Map<String, Object> updates = node.apply(state);

        assertEquals("VALIDATION_FAILED", updates.get("diagnosisStatus"));
    }

    /**
     * LLM 调用抛异常（如超时）→ LLM_ERROR。
     */
    @Test
    void shouldHandleLlmException() {
        ChatModel mockModel = mock(ChatModel.class);
        when(mockModel.chat(anyString())).thenThrow(new RuntimeException("timeout"));

        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithSymptom("异常");
        Map<String, Object> updates = node.apply(state);

        DiagnosisResult result = (DiagnosisResult) updates.get("diagnosisResult");
        assertEquals("LLM_ERROR", updates.get("diagnosisStatus"));
        assertFalse(result.isValid());
    }

    // ========================================================================
    // 上下文传递测试
    // ========================================================================

    /**
     * 验证：Node 把 symbol/errorMessage 传给 Diagnoser（作为 context）。
     */
    @Test
    void shouldPassContextToDiagnoser() {
        String llmJson = """
                {
                  "category": "ENVIRONMENT",
                  "hypothesis": "网络超时",
                  "evidence": ["连接超时"],
                  "confidence": 0.75,
                  "nextAction": "重试"
                }
                """;

        ChatModel mockModel = mockChatModel(llmJson);
        SocraticDiagnoser diagnoser = new SocraticDiagnoser(mockModel, objectMapper);
        SocraticDiagnosticNode node = new SocraticDiagnosticNode(diagnoser);

        QuantAgentState state = stateWithFullContext(
                "连接超时", "600519", "网络错误");

        Map<String, Object> updates = node.apply(state);

        // 验证：LLM 被调用，且 prompt 包含 symbol 和 errorMessage
        verify(mockModel).chat(contains("600519"));

        DiagnosisResult result = (DiagnosisResult) updates.get("diagnosisResult");
        assertEquals("ENVIRONMENT", result.category());
        assertTrue(result.isRetryable(), "ENVIRONMENT + confidence=0.75 应该可重规划");
    }
}
