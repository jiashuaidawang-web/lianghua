package com.quant.agent.application.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】LlmDeepAnalysisService —— LLM 深度归因服务，确定性诊断的"兜底智囊"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 分层混合架构的"第二层"。
//     DiagnosisService（决策树 + Strategy）→ 产出 LOW 可信度 → LlmDeepAnalysisService 兜底
//   建议阅读时机：读完 DiagnosisService 后读它。
//   学完能回答：
//     1. 为什么 LLM 只作为兜底，而不是主力？
//     2. 怎么防止 LLM "哄骗"（护犊子 / 编造证据）？
//     3. 什么情况下会触发 LLM 兜底？
//
//   💡 为什么 LLM 只作为兜底？
//     主力是 Java 决策树 + Strategy（确定性、可审计、零 token 成本）。
//     但决策树遇到"没教过的模式"会笼统归为 EXECUTION_ERROR，假说可信度 LOW。
//     这时才请 LLM 出马：分析证据、补充假说。
//     好处：大部分已知异常（沙盒拒绝、数据缺失）不调 LLM，省成本、保确定；
//           LLM 只处理"决策树搞不定的"，发挥它的推理优势。
//
//   💡 怎么防止 LLM "哄骗"？
//     1. 分析对象是 executor 的 RESULTS（第三方事实），不是 LLM 自己的产出 → 避免"护犊子"
//     2. prompt 强制每条假说必须引用证据原文 → 防止编造（hallucination）
//     3. Java 校验：JSON 解析 + evidence 非空检查 → 不合格的假说直接丢弃
//     4. LLM 结果只追加为补充假说，不替换确定性结果 → 双保险
//     5. LLM 调用失败 → 静默降级，不影响确定性诊断输出
//
//   💡 什么情况下触发？
//     确定性 Strategy 产出的最高假说可信度为 LOW 时（证据太模糊，决策树/Strategy 吃不准）。
//     通常是 EXECUTION_ERROR 类别（通用兜底），也可能是其他类别的证据不足。
//
//   ⬇ 下一步：看 DiagnosisService 怎么在适当时机调用这个兜底服务。
// ============================================================================================

/**
 * LLM 深度归因服务：当确定性策略产出 LOW 可信度假说时，作为兜底调用 LLM 做深度分析。
 *
 * <p>设计约束（防"哄骗"）：
 * <ul>
 *   <li>LLM 分析的是 executor 的 RESULTS 证据，不是它自己的产出</li>
 *   <li>prompt 要求每个假说必须引用具体证据原文</li>
 *   <li>LLM 输出经 Java 校验（JSON 解析 + 字段检查）</li>
 *   <li>LLM 结果只作为补充假说追加，不替换确定性结果</li>
 * </ul>
 */
@Service
public class LlmDeepAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmDeepAnalysisService.class);

    private final ChatModel plainChatLanguageModel;
    private final ObjectMapper objectMapper;

    public LlmDeepAnalysisService(ChatModel plainChatLanguageModel, ObjectMapper objectMapper) {
        this.plainChatLanguageModel = plainChatLanguageModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 LLM 对证据做深度归因分析。
     *
     * <p>任何情况下都不会抛异常——LLM 调用失败时静默返回空列表，
     * 由调用方（DiagnosisService）继续使用确定性结果。
     *
     * @param results  执行结果证据（Map<TaskType名, 结果文本>）
     * @param category 决策树已分类的归因类别（给 LLM 上下文，帮助它聚焦）
     * @return LLM 产出的补充假说列表（为空表示 LLM 调用失败或无有效输出）
     */
    public List<Hypothesis> deepAnalyze(Map<String, String> results, DiagnosisCategory category) {
        log.info("LLM 深度归因启动: category={}, evidenceCount={}", category, results.size());

        String prompt = buildPrompt(results, category);
        try {
            String response = plainChatLanguageModel.chat(prompt);
            log.debug("LLM 深度归因原始输出: {}", response);

            List<Hypothesis> llmHypotheses = parseHypotheses(response);
            log.info("LLM 深度归因完成: 产出 {} 个有效假说", llmHypotheses.size());
            return llmHypotheses;

        } catch (Exception e) {
            // 防御：LLM 调用失败 → 静默降级，不影响确定性诊断
            log.warn("LLM 深度归因失败，返回空假说（不影响确定性诊断）: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 构建 LLM prompt：把证据和类别上下文喂给 LLM，要求输出结构化假说。
     *
     * <p>prompt 的关键约束：
     * <ul>
     *   <li>明确告诉 LLM "分析的是证据，不是你自己产出的" → 避免护犊子</li>
     *   <li>强制每条假说引用证据原文 → 防止编造</li>
     *   <li>要求纯 JSON 输出 → 方便 Java 解析</li>
     * </ul>
     */
    private String buildPrompt(Map<String, String> results, DiagnosisCategory category) {
        StringBuilder evidence = new StringBuilder();
        results.forEach((type, result) -> {
            evidence.append("[").append(type).append("]\n");
            evidence.append(result == null ? "(无结果)" : result).append("\n\n");
        });

        return """
                你是一个量化交易系统的故障诊断专家。系统执行任务后出现了异常。

                ## 已分类的归因类别
                %s

                ## 执行结果证据（这是客观事实，你的分析必须严格基于这些证据）
                %s

                ## 你的任务
                基于上述证据，给出 1-3 条可能的根因假说。每条假说必须：
                1. 引用证据中的具体原文作为依据（防止编造）
                2. 给出可信度（HIGH/MEDIUM/LOW）
                3. 是具体的、可操作的，而不是泛泛而谈

                ## 输出格式（纯 JSON，不要 markdown 代码块）
                {
                  "hypotheses": [
                    {
                      "id": "L1",
                      "description": "假说描述",
                      "evidence": ["引用的证据原文片段"],
                      "likelihood": "HIGH"
                    }
                  ]
                }

                ## 约束
                - 每个 hypothesis 的 evidence 数组必须非空，且包含证据原文
                - likelihood 只能是 HIGH、MEDIUM、LOW 之一
                - 只输出 JSON，不要其他文字
                - 不要为"自己产出的内容"辩护——你分析的是系统执行结果，不是你自己
                """.formatted(category, evidence.toString());
    }

    /**
     * 解析 LLM 输出的 JSON 为假说列表。
     *
     * <p>严格校验：JSON 必须可解析、hypotheses 必须是数组、
     * 每条假说必须有非空 description 和 evidence。不合格的直接丢弃。
     */
    private List<Hypothesis> parseHypotheses(String response) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return hypotheses;
        }

        try {
            // 清理可能的 markdown 代码块包裹
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode hypothesesNode = root.path("hypotheses");
            if (!hypothesesNode.isArray()) {
                log.warn("LLM 输出缺少 hypotheses 数组");
                return hypotheses;
            }

            int counter = 0;
            for (JsonNode node : hypothesesNode) {
                counter++;
                String id = node.path("id").asText("L" + counter);
                String description = node.path("description").asText("");
                String likelihoodStr = node.path("likelihood").asText("LOW");

                List<String> evidenceList = new ArrayList<>();
                JsonNode evidenceNode = node.path("evidence");
                if (evidenceNode.isArray()) {
                    for (JsonNode e : evidenceNode) {
                        String evidenceText = e.asText();
                        if (evidenceText != null && !evidenceText.isBlank()) {
                            evidenceList.add(evidenceText);
                        }
                    }
                }

                // 校验：description 非空 + evidence 非空，否则丢弃这条假说
                if (description.isBlank() || evidenceList.isEmpty()) {
                    log.warn("LLM 假说 {} 缺少描述或证据，丢弃（防编造）", id);
                    continue;
                }

                Likelihood likelihood = parseLikelihood(likelihoodStr);
                hypotheses.add(new Hypothesis(id, description, evidenceList, likelihood));
            }

        } catch (Exception e) {
            log.warn("LLM 输出 JSON 解析失败: {}", e.toString());
        }

        return hypotheses;
    }

    /**
     * 解析可信度字符串，非法值降级为 LOW。
     */
    private Likelihood parseLikelihood(String value) {
        if (value == null || value.isBlank()) {
            return Likelihood.LOW;
        }
        try {
            return Likelihood.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("LLM 输出非法 likelihood: {}，降级为 LOW", value);
            return Likelihood.LOW;
        }
    }
}
