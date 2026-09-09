package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 输出节点：汇总 analysisResult + toolData，写 finalResult。
 *
 * <p>纯 Java 确定性逻辑，不调 LLM。
 */
public class OutputNode {

    private static final Logger log = LoggerFactory.getLogger(OutputNode.class);

    public Map<String, Object> apply(QuantAgentState state) {
        log.info("outputNode 执行");

        String analysis = state.analysisResult();
        String toolData = state.toolData();
        String error = state.errorMessage();

        StringBuilder result = new StringBuilder();
        if (error != null) {
            result.append("【错误】").append(error);
        } else {
            result.append("【分析】").append(analysis);
            if (toolData != null) {
                result.append("\n【数据】").append(toolData);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(StateKeys.FINAL_RESULT, result.toString());
        return updates;
    }
}
