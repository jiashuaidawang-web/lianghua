package com.quant.agent.graph.nodes;

import com.quant.agent.application.tool.StockTools;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具节点：调 @Tool 获取真实数据，写 toolData。
 *
 * <p>复用 Day 3 的 StockTools，不直接调第三方 SDK。
 */
public class ToolNode {

    private static final Logger log = LoggerFactory.getLogger(ToolNode.class);

    private final StockTools stockTools;

    public ToolNode(StockTools stockTools) {
        this.stockTools = stockTools;
    }

    public Map<String, Object> apply(QuantAgentState state) {
        String symbol = state.symbol();
        log.info("toolNode 执行: symbol={}", symbol);

        try {
            // 复用 Day 3 的工具
            String price = stockTools.getStockPrice(symbol);
            String fundamental = stockTools.getFundamental(symbol);

            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_DATA,
                    String.format("价格=%s, 基本面=%s", price, fundamental));
            return updates;

        } catch (Exception e) {
            log.warn("toolNode 失败: symbol={}, error={}", symbol, e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(StateKeys.TOOL_DATA, "工具调用失败: " + e.getMessage());
            return updates;
        }
    }
}
