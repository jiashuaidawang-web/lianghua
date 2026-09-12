package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

// ============================================================================================
// 【Day 4 · 阅读入口】OutputNode —— 图里的"汇总工人"，最后一个节点，产出最终结果。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的最后一个业务节点（→ outputNode → END）。
//   建议阅读时机：读完 ToolNode 后读它。
//   学完能回答：
//     1. 为什么 OutputNode 是"纯确定性逻辑"？它和 AnalysisNode 的本质区别是什么？
//     2. 它怎么处理"走过了 ToolNode"和"没走过 ToolNode"两种情况？
//     3. 为什么它从不抛异常？
//
//   💡 OutputNode 和 AnalysisNode 的本质区别：
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ AnalysisNode                  │ OutputNode                   │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ 是否调 LLM           │ 是（概率性行为）               │ 否（纯确定性 Java）           │
//     │ 可能失败             │ 是（网络/非法输出）            │ 否（只读 Map + 字符串拼接）   │
//     │ 职责                 │ 分析决策                       │ 汇总呈现                     │
//     │ 可替换为单元测试     │ 难（需 mock LLM）             │ 易（纯函数，输入确定输出确定） │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//     设计哲学：把"可能出错的 LLM 调用"和"确定性的结果汇总"分开。
//     OutputNode 绝不可能因为 LLM 抽风而出错，因为它根本不调 LLM。
//
//   💡 两种路径的汇总逻辑：
//     路径 A（needsTool=true）：state 有 analysisResult + toolData
//       → 输出 "【分析】...\n【数据】..."
//     路径 B（needsTool=false）：state 只有 analysisResult，toolData = null
//       → 输出 "【分析】..."（不输出【数据】段）
//     路径 C（出错）：state 有 errorMessage
//       → 输出 "【错误】..."
//
//   💨 为什么从不抛异常？
//     它只做"读 Map + 字符串拼接"，这两件事不会抛异常（最多 null → 被 if 拦住）。
//     作为最后一个节点，它必须"善始善终"：把最终结果写进 State，让图正常结束。
//     如果 OutputNode 抛异常，整个图就失败了，用户看不到任何输出。
//
//   ⬇ 下一步：看 QuantAgentStateGraph（把这三个节点连成图的"设计图"）。
// ============================================================================================

/**
 * 输出节点：汇总 analysisResult + toolData，写 finalResult。
 *
 * <p>纯 Java 确定性逻辑，不调 LLM。
 */
public class OutputNode {

    private static final Logger log = LoggerFactory.getLogger(OutputNode.class);

    public Map<String, Object> apply(QuantAgentState state) {
        log.info("outputNode 执行");

        // 从 State 读上游写入的数据（可能为 null）
        String analysis = state.analysisResult();
        String toolData = state.toolData();
        String error = state.errorMessage();

        StringBuilder result = new StringBuilder();

        if (error != null) {
            // 路径 C：上游出错了 → 展示错误
            result.append("【错误】").append(error);
        } else {
            // 路径 A/B：正常情况 → 展示分析
            result.append("【分析】").append(analysis);
            // 只有 toolData 存在时才展示（路径 A 有，路径 B 没有）
            if (toolData != null) {
                result.append("\n【数据】").append(toolData);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        // 写入 FINAL_RESULT —— 这是整个图的"终点产物"
        updates.put(StateKeys.FINAL_RESULT, result.toString());
        return updates;
    }
}
