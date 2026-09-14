package com.quant.agent.domain.tool;

import java.io.Serializable;

// ============================================================================================
// 【Day 8 · 阅读入口】MarketFundamental —— 基本面的领域契约。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 领域层的另一个"数据载体"，和 MarketPrice 并列。
//   学完能回答：为什么 PE/PB 用 Double 而不是 double？
//
//   💡 为什么 PE/PB 用 Double（包装类型）而不是 double？
//     基本面数据可能缺失（东财某些字段不返回）。
//     double 不能表示"缺失"（默认 0.0，但 PE=0 和 "没有 PE" 含义不同）。
//     Double 用 null 表示"缺失"，下游可以区分"PE 真的是 0"和"PE 没有数据"。
//     这是 Day 2 StockAnalysis 用 Double 而不是 double 的同样理由。
//
//   ⬇ 下一步：看 infrastructure/tool/RateLimiter（限流护栏）。
// ============================================================================================

/**
 * 基本面领域对象。
 *
 * <p>强类型 + 合法性校验 + 降级标记，和 MarketPrice 结构一致。
 *
 * @param symbol    股票代码
 * @param pe        市盈率（null = 缺失）
 * @param pb        市净率（null = 缺失）
 * @param sector    行业（null = 缺失）
 * @param degraded  是否为降级数据
 */
public record MarketFundamental(
        String symbol,
        Double pe,
        Double pb,
        String sector,
        boolean degraded
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据合法性校验。
     *
     * <p>规则：
     *   1. symbol 非空
     *   2. 至少有一个字段非 null（pe / pb / sector 全为 null 说明数据无效）
     *   3. pe / pb 如果存在，必须 > 0
     */
    public boolean isValid() {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        boolean hasAnyField = (pe != null) || (pb != null) || (sector != null && !sector.isBlank());
        if (!hasAnyField) {
            return false;
        }
        if (pe != null && pe <= 0.0) return false;
        if (pb != null && pb <= 0.0) return false;
        return true;
    }

    /**
     * 序列化成 JSON 字符串 —— 给 LLM 阅读。
     *
     * <p>null 字段不输出（避免 LLM 读到 "pe":null 误解），degraded 加标记。
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"symbol\":\"").append(symbol).append("\"");
        if (pe != null) sb.append(",\"pe\":").append(pe);
        if (pb != null) sb.append(",\"pb\":").append(pb);
        if (sector != null && !sector.isBlank()) sb.append(",\"sector\":\"").append(sector).append("\"");
        sb.append(",\"degraded\":").append(degraded).append("}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // 便捷构造器
    // -------------------------------------------------------------------------

    public static MarketFundamental real(String symbol, Double pe, Double pb, String sector) {
        return new MarketFundamental(symbol, pe, pb, sector, false);
    }

    public static MarketFundamental degraded(String symbol, Double pe, Double pb, String sector) {
        return new MarketFundamental(symbol, pe, pb, sector, true);
    }
}
