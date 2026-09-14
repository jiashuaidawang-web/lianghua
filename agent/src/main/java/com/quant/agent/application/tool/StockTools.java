package com.quant.agent.application.tool;

import com.quant.agent.infrastructure.tool.MarketDataGateway;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// ============================================================================================
// 【Day 8 · 阅读入口】StockTools —— LLM 能"伸手"调的 Java 方法集合（工具工具箱）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 3 的"能力仓库"，Day 8 升级为"受控能力仓库"。
//   建议阅读时机：Day 8 读完 MarketDataGateway 后读它。
//   学完能回答：
//     1. Day 3 和 Day 8 的 StockTools 有什么不同？
//     2. 为什么 StockTools 不直接调 Adapter，而是调 Gateway？
//     3. 重构后 DataFetchTaskHandler 需要改吗？为什么？
//
//   💡 Day 3 和 Day 8 的 StockTools 有什么不同？
//
//     ┌─────────────────────┬──────────────────────────────┬──────────────────────────────┐
//     │ 维度                 │ Day 3 StockTools              │ Day 8 StockTools              │
//     ├─────────────────────┼──────────────────────────────┼──────────────────────────────┤
//     │ 数据来源              │ 硬编码 Mock（price=1500）      │ MarketDataGateway（真实/降级） │
//     │ 限流/缓存/超时        │ 无                            │ 由 Gateway 提供               │
//     │ 降级兜底              │ 无（永远返回 Mock）            │ Gateway 失败→lastKnownGood/Mock│
//     │ degraded 标记         │ 无                            │ 有（LLM 可识别数据可信度）     │
//     │ @Tool 签名            │ 不变                          │ 不变                         │
//     └─────────────────────┴──────────────────────────────┴──────────────────────────────┘
//
//   💡 为什么 StockTools 不直接调 Adapter，而是调 Gateway？
//     StockTools 是"应用层的工具声明"，职责是：
//       - 用 @Tool 注解告诉 LLM "我能做什么"
//       - 简单调用下游
//     限流/缓存/降级是"基础设施策略"，属于 Gateway。
//     如果 StockTools 直接调 Adapter → 失去限流/缓存保护。
//     调 Gateway → Gateway 负责所有工程护栏。
//
//   💡 重构后 DataFetchTaskHandler 需要改吗？
//     不需要！DataFetchTaskHandler 只依赖 StockTools 的 @Tool 方法签名（getStockPrice / getFundamental），
//     不关心内部是 Mock 还是 Gateway。
//     这就是"纵向演进、保持接口稳定"——下游消费者无感。
//
//   ⬇ 下一步：看 MarketDataGateway（Gateway 的内部编排）。
// ============================================================================================

/**
 * 股票分析工具集。
 *
 * <p>每个 @Tool 方法 = LLM 可以调用的一个"能力"。
 * LLM 决定调哪个工具、传什么参数；Java 真正执行。
 *
 * <p>Day 8 升级：从硬编码 Mock 改为通过 MarketDataGateway 获取受控数据
 * （限流 + 缓存 + 超时 + 降级兜底）。
 *
 * <p>类比：Command 模式 —— 每个方法是一个 Command，LLM 是调用者，Java 是执行者。
 */
@Component  // 必须是 Spring Bean，AiServices.builder().tools(stockTools) 才能注入
public class StockTools {

    private static final Logger log = LoggerFactory.getLogger(StockTools.class);

    // -------------------------------------------------------------------------
    // Day 8 改动：注入 MarketDataGateway（替代硬编码 Mock）
    // -------------------------------------------------------------------------
    // Gateway 内部封装了 RateLimiter + Cache + EastMoneyAdapter + 降级兜底。
    // StockTools 只负责"声明能力 + 调 Gateway"，不直接处理限流/缓存逻辑。
    private final MarketDataGateway marketDataGateway;

    public StockTools(MarketDataGateway marketDataGateway) {
        this.marketDataGateway = marketDataGateway;
        log.info("StockTools 初始化: 接入 MarketDataGateway（受控数据访问）");
    }

    /**
     * 获取股票当前价格。
     *
     * <p>Day 8 改动：不再返回硬编码 Mock，而是通过 Gateway 获取真实/降级数据。
     * 返回的 JSON 包含 degraded 字段：degraded=true 表示非实时数据（限流/失败兜底）。
     *
     * @Tool 注解：
     *   参数就是"工具说明书"的 description。
     *   LLM 读这段话，就知道"什么时候该调这个方法"。
     *
     * @P 注解：
     *   参数的说明书。symbol 是入参，告诉 LLM "这要传个股票代码，例如 600519"。
     *   没有 @P 的话，LLM 不知道这个参数该传什么。
     */
    @Tool("获取股票当前价格，输入股票代码，返回价格信息。注意：返回的数据可能带有 degraded 标记，表示非实时数据（限流或降级）")
    public String getStockPrice(@P("股票代码，例如 600519") String symbol) {
        log.info("Tool 调用 getStockPrice: symbol={}", symbol);
        try {
            // -----------------------------------------------------------------
            // Day 8 改动：通过 Gateway 获取（限流→缓存→Adapter→降级）
            // -----------------------------------------------------------------
            return marketDataGateway.getPrice(symbol);
        } catch (Exception e) {
            // Gateway 内部已兜底，这里再兜一层（防万一）
            log.warn("getStockPrice 异常: symbol={}, error={}", symbol, e.getMessage());
            return "{\"symbol\":\"" + (symbol == null ? "UNKNOWN" : symbol) + "\",\"error\":\"获取失败\",\"degraded\":true}";
        }
    }

    /**
     * 获取股票基本面信息。
     *
     * <p>Day 8 改动：不再返回硬编码 Mock，而是通过 Gateway 获取真实/降级数据。
     */
    @Tool("获取股票基本面信息，包括市盈率(PE)、市净率(PB)、行业。注意：返回的数据可能带有 degraded 标记，表示非实时数据（限流或降级）")
    public String getFundamental(@P("股票代码，例如 600519") String symbol) {
        log.info("Tool 调用 getFundamental: symbol={}", symbol);
        try {
            return marketDataGateway.getFundamental(symbol);
        } catch (Exception e) {
            log.warn("getFundamental 异常: symbol={}, error={}", symbol, e.getMessage());
            return "{\"symbol\":\"" + (symbol == null ? "UNKNOWN" : symbol) + "\",\"error\":\"获取失败\",\"degraded\":true}";
        }
    }
}
