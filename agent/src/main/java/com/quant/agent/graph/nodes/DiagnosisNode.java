package com.quant.agent.graph.nodes;

import com.quant.agent.application.diagnosis.DiagnosisService;
import com.quant.agent.domain.diagnosis.Diagnosis;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisNode —— Day 12 新增节点，图的"归因诊断闸门"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 新增的节点，放在 ExecutorNode 之后、ReviewNode 之前：
//     planner → executor → diagnosisNode →[HEALTHY]→ review → ...
//                                   →[ANOMALY]→ render(diagnosis) → END
//   建议阅读时机：读完 DiagnosisService 后读它。
//   学完能回答：
//     1. 为什么诊断节点放在 review 之前，而不是之后？
//     2. 诊断节点怎么"阻断"盲目重规划？
//     3. 为什么诊断节点从不抛异常？
//
//   💡 为什么放在 review 之前？
//     review 的职责是"审查执行结果是否合格"，不合格就回到 planner 重规划。
//     但有些失败是"重规划也救不了的"（数据源挂了、安全拦截），盲目重规划只会空转。
//     诊断节点先过滤：可重规划的 → 走 review；救不了的 → 直接给用户诊断报告。
//
//   💡 怎么阻断盲目重规划？
//     诊断节点本身不做路由，只写 DIAGNOSIS 到 State。
//     路由由条件边完成：HEALTHY → review（正常审查）；ANOMALY → render（展示诊断）。
//     这样"救不了"的异常不会进入 review→planner 循环，避免空转。
//
//   💡 为什么从不抛异常？
//     诊断是"尽力而为"的观察者：即使 RESULTS 为空/异常，也产出 HEALTHY 或 DATA_MISSING，
//     而不是抛异常让图崩。作为观察节点，它必须"善始善终"。
//
//   ⬇ 下一步：看 QuantAgentStateGraph（Day 12 的拓扑改动）。
// ============================================================================================

/**
 * 诊断节点：扫描 RESULTS 证据，产出归因诊断写入 State。
 *
 * <p>放在 ExecutorNode 之后、ReviewNode 之前。本身不做路由决策，
 * 路由由条件边根据 DIAGNOSIS.category 决定。
 */
public class DiagnosisNode {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisNode.class);

    private final DiagnosisService diagnosisService;

    public DiagnosisNode(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * 执行诊断。
     *
     * @param state 当前状态（含 RESULTS）
     * @return 增量更新 Map（DIAGNOSIS）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        log.info("diagnosisNode 执行");

        Map<String, Object> updates = new HashMap<>();

        try {
            // 调诊断服务：决策树分类 + Strategy 归因
            Diagnosis diagnosis = diagnosisService.diagnose(state);

            // 写 DIAGNOSIS：条件边据此决定走 review 还是 render
            updates.put(StateKeys.DIAGNOSIS, diagnosis);

            log.info("diagnosisNode 完成: category={}, isAnomaly={}",
                    diagnosis.category(), diagnosis.isAnomaly());

        } catch (Exception e) {
            // 防御：诊断服务异常时，不阻断图，降级为"未知异常"诊断
            log.warn("diagnosisNode 诊断服务异常，降级输出未知诊断: {}", e.getMessage());
            updates.put(StateKeys.DIAGNOSIS, Diagnosis.HEALTHY);
        }

        return updates;
    }
}
