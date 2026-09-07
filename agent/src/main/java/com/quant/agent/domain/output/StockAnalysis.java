package com.quant.agent.domain.output;

/**
 * 结构化分析结果 DTO。
 *
 * <p>这个 record 是 LLM 的「输出契约」：既是 Java 强类型对象，也是 LLM 要遵循的 JSON Schema。
 *
 * <p>LLM 必须返回：{"action":"BUY|SELL|HOLD","symbol":"600519","score":0.0~10.0,"reason":"..."}
 */
public record StockAnalysis(
        String action,
        String symbol,
        Double score,
        String reason) {

    /**
     * 校验 DTO 合法性。LLM 输出视为不可信输入，必须校验后才进入下游。
     *
     * @return 合法返回 true；非法返回 false
     */
    public boolean isValid() {
        if (action == null || symbol == null || score == null || reason == null) {
            return false;
        }
        if (!action.equals("BUY") && !action.equals("SELL") && !action.equals("HOLD")) {
            return false;
        }
        if (score < 0.0 || score > 10.0) {
            return false;
        }
        return true;
    }
}
