package com.quant.agent.graph.nodes;

import com.quant.agent.application.diagnosis.SocraticDiagnoser;
import com.quant.agent.domain.diagnosis.DiagnosisResult;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】SocraticDiagnosticNode —— Socratic 诊断节点，图的"诊断专家"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 新增节点，站在 ReviewNode 之后、重规划/HITL 之前。
//   建议阅读时机：读完 SocraticDiagnoser + DiagnosisResult 后读它。
//   学完能回答：
//     1. SocraticDiagnosticNode 放在图的哪个位置？为什么？
//     2. 它怎么决定"写什么 symptom"？
//     3. 诊断结果写进 State 后，谁决定下一步？
//
//   💡 放在哪？
//     放在 ReviewNode 之后：review fail → socraticDiagnostic → 决定下一步。
//     原因：review 发现失败了，但不知道"为什么失败"——诊断节点负责归因。
//
//   💡 symptom 从哪来？
//     不是本节点写的，是**上游 ReviewNode 或 Risk 节点**写的。
//     本节点只读 symptom，然后调 SocraticDiagnoser 诊断。
//     （职责分离：谁触发谁写症状，诊断节点只诊断。）
//
//   💡 谁决定下一步？
//     本节点只写 diagnosisResult + diagnosisStatus，**不决定下一步**。
//     下一步由图拓扑的条件边路由函数决定（读 diagnosisResult 判断 retryable）。
//
//   ⬇ 下一步：看 QuantAgentStateGraph（Day 12 的拓扑改动 + 路由函数）。
// ============================================================================================

/**
 * Socratic 诊断节点：读 symptom → 调 Diagnoser → 写诊断结果。
 *
 * <p>放在 ReviewNode 之后。产出 DiagnosisResult 写入 State，
 * 条件边据此决定走 retry（重规划）还是 HITL（人工确认）。
 */
public class SocraticDiagnosticNode {

    private static final Logger log = LoggerFactory.getLogger(SocraticDiagnosticNode.class);

    private final SocraticDiagnoser socraticDiagnoser;

    public SocraticDiagnosticNode(SocraticDiagnoser socraticDiagnoser) {
        this.socraticDiagnoser = socraticDiagnoser;
    }

    /**
     * 执行诊断。
     *
     * @param state 当前状态（含 symptom）
     * @return 增量更新 Map（写入 diagnosisResult + diagnosisStatus）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        log.info("socraticDiagnosticNode 执行");

        String symptom = state.symptom();
        Map<String, Object> updates = new HashMap<>();

        // -------------------------------------------------------------------------
        // 前置校验：symptom 为空则直接进 HITL，不浪费 LLM 调用
        // -------------------------------------------------------------------------
        if (symptom == null || symptom.isBlank()) {
            log.warn("symptom 为空，跳过诊断，直接进 HITL");
            updates.put(StateKeys.DIAGNOSIS_RESULT,
                    new DiagnosisResult("UNKNOWN", "无症状信息", List.of("无法诊断"), 0.0, "转人工"));
            updates.put(StateKeys.DIAGNOSIS_STATUS, "LLM_ERROR");
            return updates;
        }

        // -------------------------------------------------------------------------
        // 构建上下文（给 LLM 更多背景信息）
        // -------------------------------------------------------------------------
        Map<String, String> context = buildContext(state);

        // -------------------------------------------------------------------------
        // 调 SocraticDiagnoser（拼 prompt + 调 LLM + 校验）
        // -------------------------------------------------------------------------
        DiagnosisResult result = socraticDiagnoser.diagnose(symptom, context);

        // -------------------------------------------------------------------------
        // 写 State：diagnosisResult + diagnosisStatus
        // -------------------------------------------------------------------------
        updates.put(StateKeys.DIAGNOSIS_RESULT, result);

        // 根据 result 推断 status：
        //   - result 是错误哨兵（category=UNKNOWN + 失败假设） → LLM_ERROR
        //   - result 校验不通过（isValid=false） → VALIDATION_FAILED
        //   - result 合法 → DIAGNOSED
        String status = inferStatus(result, symptom);
        updates.put(StateKeys.DIAGNOSIS_STATUS, status);

        log.info("socraticDiagnosticNode 完成: category={}, confidence={}, status={}",
                result.category(), result.confidence(), status);

        return updates;
    }

    /**
     * 构建诊断上下文（给 LLM 更多背景）。
     */
    private Map<String, String> buildContext(QuantAgentState state) {
        Map<String, String> context = new HashMap<>();
        if (state.symbol() != null) {
            context.put("股票代码", state.symbol());
        }
        if (state.errorMessage() != null) {
            context.put("错误信息", state.errorMessage());
        }
        if (state.reviewPassed()) {
            context.put("审查历史", "之前已通过审查");
        }
        return context;
    }

    /**
     * 根据诊断结果推断状态。
     *
     * <p>推断逻辑：
     * <ul>
     *   <li>result 是 LLM_ERROR 哨兵 → "LLM_ERROR"</li>
     *   <li>result 是 VALIDATION_FAILED 哨兵 → "VALIDATION_FAILED"</li>
     *   <li>result 合法 → "DIAGNOSED"</li>
     *   <li>其他 → "LLM_ERROR"（兜底）</li>
     * </ul>
     */
    private String inferStatus(DiagnosisResult result, String originalSymptom) {
        if (result == DiagnosisResult.LLM_ERROR) {
            return "LLM_ERROR";
        }
        if (result == DiagnosisResult.VALIDATION_FAILED) {
            return "VALIDATION_FAILED";
        }
        if (result.isValid()) {
            return "DIAGNOSED";
        }
        return "LLM_ERROR"; // 兜底
    }
}
