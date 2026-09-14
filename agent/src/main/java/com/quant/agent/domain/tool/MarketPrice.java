package com.quant.agent.domain.tool;

import java.io.Serializable;

// ============================================================================================
// 【Day 8 · 阅读入口】MarketPrice —— 行情数据的"领域契约"，替代 Day 3 的裸 JSON 字符串。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 领域层的"数据载体"。Tool/Adapter/Gateway 都围绕它转。
//   建议阅读时机：Day 8 最先读它（理解"为什么要从 JSON 字符串升级到强类型"）。
//   学完能回答：
//     1. 为什么 Day 3 用 JSON 字符串，Day 8 要改成强类型 record？
//     2. 这个 record 怎么同时支持"真实数据"和"降级数据"？
//     3. isValid() 在这里起什么作用？
//
//   💡 为什么 Day 3 用 JSON 字符串，Day 8 要改成强类型？
//     Day 3 是 Mock 阶段，LLM 只读文本，JSON 字符串够用。
//     Day 8 要接真实 API，Java 代码需要：
//       - 解析响应 → 强类型（.price() 而不是 map.get("price")）
//       - 校验数据合法性（price > 0、symbol 非空）
//       - 区分"真实数据"和"降级数据"（degraded 字段）
//     强类型让这些操作编译期可检查、IDE 可补全。
//
//   💡 怎么同时支持"真实数据"和"降级数据"？
//     degraded 字段：
//       - false = 来自真实数据源（东财 API）
//       - true  = 来自降级（缓存兜底 / Mock 兜底）
//     LLM 读到 degraded=true 的数据，就知道"这是旧数据/假数据"，不会当作实时行情做决策。
//     这是 constitution 里"LLM 输出视为不可信输入"的延伸 —— 工具数据也要标记可信度。
//
//   💡 isValid() 的作用？
//     守门员：price <= 0、symbol 为空 → 非法 → Gateway 走降级路径。
//     防止"东财返回了 200 但数据是空的"这种脏数据污染 State。
//
//   ⬇ 下一步：看 MarketFundamental（基本面的领域契约）。
// ============================================================================================

/**
 * 行情价格领域对象。
 *
 * <p>替代 Day 3 的裸 JSON 字符串，提供：
 * <ul>
 *   <li>强类型访问（.price() 而非 map.get）</li>
 *   <li>数据合法性校验（isValid）</li>
 *   <li>降级标记（degraded）—— 让 LLM 知道数据可信度</li>
 * </ul>
 *
 * @param symbol    股票代码（如 "600519"）
 * @param price     当前价格（必须 > 0 才合法）
 * @param currency  币种（CNY）
 * @param degraded  是否为降级数据（true = 缓存兜底/Mock 兜底，非实时）
 */
public record MarketPrice(
        String symbol,
        double price,
        String currency,
        boolean degraded
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据合法性校验。
     *
     * <p>规则：
     *   1. symbol 非空
     *   2. price > 0（东财异常时可能返回 0 或负数）
     *   3. currency 非空
     *
     * @return 合法返回 true；任一规则不满足返回 false
     */
    public boolean isValid() {
        return symbol != null && !symbol.isBlank()
                && price > 0.0
                && currency != null && !currency.isBlank();
    }

    /**
     * 序列化成 JSON 字符串 —— 给 LLM 阅读的格式。
     *
     * <p>注意：degraded=true 时会加一个 "[降级数据]" 标记，让 LLM 知道不可全信。
     */
    public String toJson() {
        String tag = degraded ? "[降级数据] " : "";
        return "{\"symbol\":\"" + symbol
                + "\",\"price\":" + price
                + ",\"currency\":\"" + currency
                + "\",\"degraded\":" + degraded + "}";
    }

    // -------------------------------------------------------------------------
    // 便捷构造器
    // -------------------------------------------------------------------------

    /** 真实数据构造器（degraded = false） */
    public static MarketPrice real(String symbol, double price, String currency) {
        return new MarketPrice(symbol, price, currency, false);
    }

    /** 降级数据构造器（degraded = true） */
    public static MarketPrice degraded(String symbol, double price, String currency) {
        return new MarketPrice(symbol, price, currency, true);
    }
}
