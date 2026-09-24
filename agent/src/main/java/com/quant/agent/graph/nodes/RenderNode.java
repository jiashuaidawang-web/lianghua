package com.quant.agent.graph.nodes;

import com.quant.agent.application.render.RenderAiService;
import com.quant.agent.domain.diagnosis.Diagnosis;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 5 · 新增】RenderNode —— Day 5 图拓扑的最后一个节点，把结构化结果润色成人话。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 图的终点（review → RENDER → END）。
//   建议阅读时机：读完 RenderAiService 后读它。
//   学完能回答：
//     1. RenderNode 和 Day 4 的 OutputNode 有什么区别？
//     2. 为什么要有降级兜底？
//     3. 它怎么把 Map<String,String> 变成 LLM 能消化的 payload？
//
//   💡 RenderNode 和 Day 4 OutputNode 的区别：
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ Day 4 OutputNode              │ Day 5 RenderNode              │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ 是否调 LLM           │ 否（纯 Java 字符串拼接）       │ 是（LLM 润色）               │
//     │ 输入                 │ analysisResult + toolData     │ tasks + results map          │
//     │ 输出                 | 纯文本                        │ 润色后的人话文本              │
//     │ 失败影响             │ 几乎不会失败                  │ LLM 可能失败 → 降级兜底       │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//     Day 4 的 OutputNode 是"纯确定性汇总"，不调 LLM，所以几乎不会失败。
//     Day 5 的 RenderNode 是"LLM 润色"，调了 LLM 就可能失败（网络/超时/非法输出）。
//     所以 RenderNode 必须有降级兜底：LLM 挂了 → 回退到原始 results 文本，不让图崩。
//
//   💡 降级兜底的意义：
//     RenderNode 是图的最后一个节点，如果它抛异常，用户什么结果都看不到。
//     降级策略：try LLM 渲染，catch 任何异常 → 回退到"把 results map 直接拼成文本"。
//     这样最差用户看到的是没润色过的原始数据，而不是一个 500 错误。
//
//   💡 payload 构造：
//     results 是 Map<TaskType名, 结果文本>，直接遍历拼成：
//       [DATA_FETCH]
//       价格={...}; 基本面={...}
//
//       [ANALYSIS]
//       操作=HOLD, 评分=0.65, ...
//     LLM 拿到这个 payload，按 SYSTEM_PROMPT 的要求整合成人话。
//
//   ⬇ 到这里，Day 5 的完整链路：planner → executor → review → render → END。
// ============================================================================================

/**
 * 渲染节点：把 ExecutorNode 的结构化结果润色成给人类用户看的自然语言。
 *
 * <p>LLM 失败时降级为原始 results 文本，保证图一定能产出输出。
 */
public class RenderNode {

    private static final Logger log = LoggerFactory.getLogger(RenderNode.class);

    private final RenderAiService renderAiService;

    public RenderNode(RenderAiService renderAiService) {
        this.renderAiService = renderAiService;
    }

    /**
     * 执行渲染逻辑。
     *
     * <p>成功 → 返回 LLM 润色后的人话；失败 → 降级为原始 results 文本。
     *
     * @param state 当前状态（含 results / tasks）
     * @return 增量更新 Map（RENDERED_RESULT）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        log.info("renderNode 执行");

        Map<String, String> results = state.results();
        String rendered;

        // -------------------------------------------------------------------------
        // Day 12：有异常诊断时，优先渲染诊断报告（证据→归因→推荐动作），不再渲染原始 results
        // -------------------------------------------------------------------------
        Diagnosis diagnosis = state.diagnosis();
        if (diagnosis != null && diagnosis.isAnomaly()) {
            rendered = renderDiagnosis(diagnosis);
            log.info("renderNode 渲染诊断报告: category={}", diagnosis.category());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.RENDERED_RESULT, rendered);
            return updates;
        }

        try {
            // -----------------------------------------------------------------
            // 步骤 1：把 results map 拼成 LLM 能消化的文本 payload
            // -----------------------------------------------------------------
            StringBuilder payload = new StringBuilder();
            results.forEach((type, result) -> {
                payload.append("[").append(type).append("]\n");
                payload.append(result == null ? "(无结果)" : result).append("\n\n");
            });

            // -----------------------------------------------------------------
            // 步骤 2：调 LLM 润色成人话
            // -----------------------------------------------------------------
            rendered = renderAiService.render(payload.toString());
            log.info("renderNode 润色完成");

        } catch (Exception e) {
            // -----------------------------------------------------------------
            // 降级兜底：LLM 渲染失败 → 回退到原始 results 文本
            // -----------------------------------------------------------------
            // 这是最后一个节点，绝不能抛异常让用户看到 500。
            // 最差情况：用户看到没润色的原始数据，而不是一个错误页面。
            log.warn("renderNode LLM 润色失败，降级输出原始结果: {}", e.toString());
            StringBuilder fallback = new StringBuilder();
            results.forEach((type, result) -> {
                fallback.append("【").append(type).append("】\n");
                fallback.append(result == null ? "(无结果)" : result).append("\n\n");
            });
            rendered = fallback.toString();
        }

        Map<String, Object> updates = new HashMap<>();
        // 写入 RENDERED_RESULT —— Day 5 图的"终点产物"（给人看的版本）
        updates.put(StateKeys.RENDERED_RESULT, rendered);
        return updates;
    }

    /**
     * Day 12：把 Diagnosis 渲染成人类可读的"证据→归因→推荐动作"报告。
     *
     * <p>纯确定性字符串拼接，不调 LLM（诊断报告必须稳定可审计）。
     */
    private String renderDiagnosis(Diagnosis diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("【诊断报告】\n");
        sb.append("归因类别: ").append(diagnosis.category()).append("\n");
        sb.append("整体判断: ").append(diagnosis.summary()).append("\n");
        sb.append("可信度: ").append(diagnosis.confidence()).append("\n\n");

        sb.append("—— 假说（按可信度排序） ——\n");
        if (diagnosis.hypotheses().isEmpty()) {
            sb.append("（无具体假说）\n");
        } else {
            for (Hypothesis h : diagnosis.hypotheses()) {
                sb.append("[").append(h.likelihood()).append("] ").append(h.id())
                        .append(" ").append(h.description()).append("\n");
                if (h.evidence() != null && !h.evidence().isEmpty()) {
                    sb.append("    证据: ");
                    sb.append(String.join(" | ", h.evidence())).append("\n");
                }
            }
        }

        sb.append("\n—— 推荐动作 ——\n");
        if (diagnosis.recommendedActions().isEmpty()) {
            sb.append("（无）\n");
        } else {
            int n = 1;
            for (String action : diagnosis.recommendedActions()) {
                sb.append(n++).append(". ").append(action).append("\n");
            }
        }
        return sb.toString();
    }
}
