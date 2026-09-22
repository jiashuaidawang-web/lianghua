package com.quant.agent.application.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.DiagnosisResult;
import com.quant.agent.domain.diagnosis.DiagnosisStatus;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】SocraticDiagnoser —— Socratic 诊断的"编排中心"，Day 12 核心应用服务。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 诊断引擎的编排层。夹在 SocraticDiagnosticNode 和 ChatLanguageModel 之间。
//   学完能回答：
//     1. SocraticDiagnoser 的职责是什么？为什么不放在 Node 里直接调 LLM？
//     2. prompt 是怎么拼的？为什么要求 LLM 先给 hypothesis 再给 evidence？
//     3. 校验逻辑有几层？每层失败怎么处理？
//
//   💡 职责边界：
//     - Node 负责"读 State → 调 Service → 写 State"（编排）。
//     - Service 负责"拼 prompt + 调 LLM + 校验"（业务逻辑）。
//     这样校验逻辑可以独立于图测试（单元测试只 mock ChatLanguageModel）。
//
//   💡 prompt 设计：
//     - 强制推理链：先 hypothesis（假设）→ 再 evidence（证据）→ 最后 nextAction（建议）。
//     - 禁止直接改代码：prompt 里明确"只诊断，不生成代码"。
//     - 输出格式约束：要求 JSON，字段固定。
//
//   💡 校验层次：
//     1. JSON 可解析？
//     2. category ∈ {DATA, STRATEGY, CODE, ENVIRONMENT}？
//     3. confidence ∈ [0.0, 1.0]？
//     4. evidence 非空？
//     任一层失败 → 返回带 VALIDATION_FAILED 状态的 result。
//
//   ⬇ 下一步：看 SocraticDiagnosticNode（怎么把这个 Service 接入图）。
// ============================================================================================

/**
 * Socratic 诊断服务：拼 prompt + 调 LLM + 校验产出 DiagnosisResult。
 *
 * <p>核心职责：把异常 symptom 变成结构化的证据驱动诊断报告。
 */
public class SocraticDiagnoser {

    private static final Logger log = LoggerFactory.getLogger(SocraticDiagnoser.class);

    /** LLM 调用超时（秒）。 */
    private static final int LLM_TIMEOUT_SECONDS = 30;

    /** 最低置信度阈值（低于此值强制进 HITL）。 */
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.7;

    private final ChatModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public SocraticDiagnoser(ChatModel chatLanguageModel, ObjectMapper objectMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 Socratic 诊断。
     *
     * @param symptom 异常症状（如"回测收益从 15% 跌到 -40%"）
     * @param context 上下文信息（历史执行结果、环境信息等）
     * @return 诊断结果（成功=DIAGNOSED，失败=VALIDATION_FAILED 或 LLM_ERROR）
     */
    public DiagnosisResult diagnose(String symptom, Map<String, String> context) {
        log.info("SocraticDiagnoser 开始诊断: symptom={}", symptom);

        if (symptom == null || symptom.isBlank()) {
            log.warn("symptom 为空，跳过诊断");
            return DiagnosisResult.EMPTY;
        }

        try {
            // 第 1 步：拼 Socratic prompt
            String prompt = buildSocraticPrompt(symptom, context);

            // 第 2 步：调 LLM（概率性行为）
            log.debug("调用 LLM 生成诊断");
            String llmResponse = callLlmWithTimeout(prompt);
            log.debug("LLM 返回: {}", llmResponse);

            // 第 3 步：解析 JSON
            DiagnosisResult rawResult = parseDiagnosisJson(llmResponse);

            // 第 4 步：校验（Java 确定性）
            DiagnosisResult validatedResult = validateDiagnosis(rawResult);

            log.info("诊断完成: category={}, confidence={}, evidenceSize={}",
                    validatedResult.category(), validatedResult.confidence(),
                    validatedResult.evidence().size());
            return validatedResult;

        } catch (JsonProcessingException e) {
            log.error("LLM 输出 JSON 解析失败", e);
            return withStatus(DiagnosisStatus.VALIDATION_FAILED);
        } catch (IllegalArgumentException e) {
            log.error("LLM 输出校验失败: {}", e.getMessage());
            return withStatus(DiagnosisStatus.VALIDATION_FAILED);
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return withStatus(DiagnosisStatus.LLM_ERROR);
        }
    }

    /**
     * 拼 Socratic prompt：强制推理链 + 输出格式约束。
     */
    private String buildSocraticPrompt(String symptom, Map<String, String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个量化交易系统的诊断专家。请对以下异常进行归因诊断。\n\n");
        sb.append("## 异常症状\n");
        sb.append(symptom).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("## 上下文信息\n");
            context.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("## 诊断要求\n");
        sb.append("1. 先给出 hypothesis（假设：可能的原因）\n");
        sb.append("2. 再给出 evidence（证据：支持假设的具体事实，至少 1 条）\n");
        sb.append("3. 最后给出 nextAction（建议动作：下一步该做什么）\n");
        sb.append("4. category 必须是以下之一：DATA（数据问题）、STRATEGY（策略问题）、CODE（代码问题）、ENVIRONMENT（环境问题）\n");
        sb.append("5. confidence 必须是 0.0~1.0 的小数，表示你对这个诊断的把握程度\n");
        sb.append("6. **只诊断，不生成代码**\n\n");

        sb.append("## 输出格式（严格 JSON）\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"category\": \"DATA 或 STRATEGY 或 CODE 或 ENVIRONMENT\",\n");
        sb.append("  \"hypothesis\": \"你的假设\",\n");
        sb.append("  \"evidence\": [\"证据1\", \"证据2\"],\n");
        sb.append("  \"confidence\": 0.85,\n");
        sb.append("  \"nextAction\": \"建议动作\"\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 调 LLM（带超时控制）。
     */
    private String callLlmWithTimeout(String prompt) {
        // LangChain4j 1.20.0 的 ChatModel.chat(String) 返回 String（完整文本输出）。
        // 这是同步调用（非流式），适合"拿完整 JSON"的场景。
        // TODO: Day 19 生产化时迁移到带超时的异步调用
        return chatLanguageModel.chat(prompt);
    }

    /**
     * 解析 LLM 输出的 JSON。
     */
    private DiagnosisResult parseDiagnosisJson(String llmResponse) throws JsonProcessingException {
        // 清理 LLM 输出：去掉 ```json 包裹和前后空白
        String cleaned = llmResponse.trim();
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }
        cleaned = cleaned.trim();

        // 解析为 Map
        Map<String, Object> map = objectMapper.readValue(cleaned,
                new TypeReference<Map<String, Object>>() {});

        // 提取字段
        String category = getStringOrDefault(map, "category", "");
        String hypothesis = getStringOrDefault(map, "hypothesis", "");
        double confidence = getDoubleOrDefault(map, "confidence", 0.0);
        String nextAction = getStringOrDefault(map, "nextAction", "");

        // evidence 可能是 List 或单个 String
        List<String> evidence = extractEvidence(map.get("evidence"));

        return new DiagnosisResult(category, hypothesis, evidence, confidence, nextAction);
    }

    /**
     * 校验诊断结果（Java 确定性）。
     */
    private DiagnosisResult validateDiagnosis(DiagnosisResult raw) {
        // 校验 1：category 枚举
        try {
            DiagnosisCategory.fromValue(raw.category());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("category 校验失败: " + e.getMessage());
        }

        // 校验 2：confidence 范围
        if (raw.confidence() < 0.0 || raw.confidence() > 1.0) {
            throw new IllegalArgumentException("confidence 超出范围: " + raw.confidence());
        }

        // 校验 3：evidence 非空
        if (raw.evidence() == null || raw.evidence().isEmpty()) {
            throw new IllegalArgumentException("evidence 不能为空");
        }

        // 校验 4：hypothesis 非空
        if (raw.hypothesis() == null || raw.hypothesis().isBlank()) {
            throw new IllegalArgumentException("hypothesis 不能为空");
        }

        return raw;
    }

    /**
     * 创建带错误状态的诊断结果。
     *
     * @param status 错误状态（LLM_ERROR 或 VALIDATION_FAILED）
     * @return 对应的错误哨兵
     */
    private DiagnosisResult withStatus(DiagnosisStatus status) {
        return switch (status) {
            case LLM_ERROR -> DiagnosisResult.LLM_ERROR;
            case VALIDATION_FAILED -> DiagnosisResult.VALIDATION_FAILED;
            default -> DiagnosisResult.LLM_ERROR;
        };
    }

    /**
     * 从 Map 安全取 String。
     */
    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 从 Map 安全取 double。
     */
    private double getDoubleOrDefault(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 提取 evidence 字段（兼容 List 和单个 String）。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractEvidence(Object evidenceObj) {
        if (evidenceObj == null) {
            return List.of();
        }
        if (evidenceObj instanceof List) {
            try {
                return (List<String>) evidenceObj;
            } catch (ClassCastException e) {
                return List.of(evidenceObj.toString());
            }
        }
        return List.of(evidenceObj.toString());
    }
}
