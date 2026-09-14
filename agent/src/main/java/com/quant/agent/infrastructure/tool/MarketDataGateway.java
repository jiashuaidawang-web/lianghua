package com.quant.agent.infrastructure.tool;

import com.quant.agent.domain.tool.MarketFundamental;
import com.quant.agent.domain.tool.MarketPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// ============================================================================================
// 【Day 8 · 阅读入口】MarketDataGateway —— 行情数据的"编排中心"，Day 8 工程护栏的核心。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 的"策略层"。夹在 StockTools（应用层）和 Adapter（I/O 层）之间。
//      StockTools → MarketDataGateway → [RateLimiter → Cache → EastMoneyAdapter]
//   建议阅读时机：读完 RateLimiter / Cache / Adapter 后读它。
//   学完能回答：
//     1. 为什么要有 Gateway 这一层？StockTools 直接调 Adapter 不行吗？
//     2. Gateway 的"编排顺序"为什么是 限流→缓存→Adapter→降级？
//     3. 为什么 Gateway 要维护 lastKnownGood（最后已知好数据）？
//
//   💡 为什么要有 Gateway 这一层？
//     如果 StockTools 直接调 Adapter：
//       - StockTools 是 @Tool 方法，职责应该是"声明能力 + 简单调用"
//       - 限流/缓存/降级是"策略逻辑"，塞进 Tool → Tool 变臃肿
//     加一层 Gateway：
//       - StockTools = 薄适配层（只负责调 Gateway + 转 JSON）
//       - Gateway = 策略层（限流/缓存/降级编排）
//     符合 constitution 的"Provider SDKs stay behind infrastructure adapters"。
//
//   💡 编排顺序为什么是 限流→缓存→Adapter→降级？
//     1. 限流（最前）：超限直接拒绝，保护下游 API（最便宜的判断）
//     2. 缓存（次之）：命中就不调 API（省网络 + 省令牌）
//     3. Adapter（真正 I/O）：前两道都没拦住 → 发 HTTP
//     4. 降级（最后兜底）：Adapter 失败 → 用 lastKnownGood 或 Mock
//     这个顺序是"从便宜到昂贵"的漏斗。
//
//   💡 为什么维护 lastKnownGood？
//     Adapter 失败时，如果有"上次成功的数据"，返回它（degraded=true）比返回全新 Mock 更好。
//     比如：东财 API 偶尔超时，但 5 秒前成功过 → 返回 5 秒前的数据 + degraded 标记。
//     LLM 看到 degraded=true 会知道"这是旧数据"，比拿到一个随机 Mock 更合理。
//
//   ⬇ 下一步：看 StockTools（重构后调 Gateway）。
// ============================================================================================

/**
 * 行情数据网关：编排限流 → 缓存 → Adapter → 降级。
 *
 * <p>职责：
 * <ul>
 *   <li>统一入口：StockTools 只调 Gateway，不直接碰 Adapter/Cache/RateLimiter</li>
 *   <li>编排策略：限流→缓存→Adapter→降级漏斗</li>
 *   <li>维护 lastKnownGood：Adapter 失败时返回上次成功数据</li>
 *   <li>可观测性：统计命中/限流/降级次数</li>
 * </ul>
 */
public class MarketDataGateway {

    private static final Logger log = LoggerFactory.getLogger(MarketDataCache.class);

    // -------------------------------------------------------------------------
    // 依赖
    // -------------------------------------------------------------------------

    private final RateLimiter rateLimiter;
    private final MarketDataCache cache;
    private final EastMoneyAdapter adapter;

    // -------------------------------------------------------------------------
    // 配置
    // -------------------------------------------------------------------------

    /** 缓存 key 前缀 */
    private static final String KEY_PRICE = "price:";
    private static final String KEY_FUNDAMENTAL = "fundamental:";

    // -------------------------------------------------------------------------
    // lastKnownGood（最后已知好数据，用于降级兜底）
    // -------------------------------------------------------------------------

    private final ConcurrentMap<String, MarketPrice> lastKnownGoodPrice = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MarketFundamental> lastKnownGoodFundamental = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // 统计（可观测性）
    // -------------------------------------------------------------------------

    private final java.util.concurrent.atomic.AtomicLong rateLimitedCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong cacheHitCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong adapterCallCount = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong fallbackCount = new java.util.concurrent.atomic.AtomicLong(0);

    // -------------------------------------------------------------------------
    // 构造器
    // -------------------------------------------------------------------------

    public MarketDataGateway(RateLimiter rateLimiter, MarketDataCache cache, EastMoneyAdapter adapter) {
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.adapter = adapter;
        log.info("MarketDataGateway 初始化");
    }

    // -------------------------------------------------------------------------
    // 公开方法：获取行情 / 基本面
    // -------------------------------------------------------------------------

    /**
     * 获取实时行情价格（编排：限流→缓存→Adapter→降级）。
     *
     * @param symbol 股票代码
     * @return JSON 字符串（给 LLM 阅读）
     */
    public String getPrice(String symbol) {
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "UNKNOWN" : symbol;
        String cacheKey = KEY_PRICE + safeSymbol;

        // -----------------------------------------------------------------
        // 第 1 道：限流检查
        // -----------------------------------------------------------------
        if (!rateLimiter.tryAcquire()) {
            rateLimitedCount.incrementAndGet();
            log.warn("行情请求被限流: symbol={}", safeSymbol);
            // 限流时：尝试用缓存（即使过期也比重试好），否则降级
            String cached = cache.getString(cacheKey);
            if (cached != null) {
                return cached;  // 限流时返回缓存（即使可能过期）
            }
            return buildRateLimitedResponse(safeSymbol);
        }

        // -----------------------------------------------------------------
        // 第 2 道：缓存命中
        // -----------------------------------------------------------------
        String cached = cache.getString(cacheKey);
        if (cached != null) {
            cacheHitCount.incrementAndGet();
            log.debug("行情缓存命中: symbol={}", safeSymbol);
            return cached;
        }

        // -----------------------------------------------------------------
        // 第 3 道：调 Adapter（真正 I/O）
        // -----------------------------------------------------------------
        try {
            adapterCallCount.incrementAndGet();
            MarketPrice price = adapter.fetchPrice(safeSymbol);

            // 写入缓存 + lastKnownGood
            String json = price.toJson();
            cache.putString(cacheKey, json);
            if (!price.degraded()) {
                lastKnownGoodPrice.put(safeSymbol, price);
            }

            log.debug("行情获取成功: symbol={}, price={}, degraded={}",
                    safeSymbol, price.price(), price.degraded());
            return json;

        } catch (Exception e) {
            // -----------------------------------------------------------------
            // 第 4 道：降级兜底
            // -----------------------------------------------------------------
            fallbackCount.incrementAndGet();
            log.warn("行情获取失败，降级: symbol={}, error={}", safeSymbol, e.getMessage());
            return buildFallbackPriceResponse(safeSymbol);
        }
    }

    /**
     * 获取基本面信息（编排：限流→缓存→Adapter→降级）。
     *
     * @param symbol 股票代码
     * @return JSON 字符串（给 LLM 阅读）
     */
    public String getFundamental(String symbol) {
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "UNKNOWN" : symbol;
        String cacheKey = KEY_FUNDAMENTAL + safeSymbol;

        // 第 1 道：限流
        if (!rateLimiter.tryAcquire()) {
            rateLimitedCount.incrementAndGet();
            log.warn("基本面请求被限流: symbol={}", safeSymbol);
            String cached = cache.getString(cacheKey);
            if (cached != null) return cached;
            return buildRateLimitedResponse(safeSymbol);
        }

        // 第 2 道：缓存
        String cached = cache.getString(cacheKey);
        if (cached != null) {
            cacheHitCount.incrementAndGet();
            return cached;
        }

        // 第 3 道：Adapter
        try {
            adapterCallCount.incrementAndGet();
            MarketFundamental fundamental = adapter.fetchFundamental(safeSymbol);

            String json = fundamental.toJson();
            cache.putString(cacheKey, json);
            if (!fundamental.degraded()) {
                lastKnownGoodFundamental.put(safeSymbol, fundamental);
            }
            return json;

        } catch (Exception e) {
            // 第 4 道：降级
            fallbackCount.incrementAndGet();
            log.warn("基本面获取失败，降级: symbol={}, error={}", safeSymbol, e.getMessage());
            return buildFallbackFundamentalResponse(safeSymbol);
        }
    }

    // -------------------------------------------------------------------------
    // 降级响应构造
    // -------------------------------------------------------------------------

    /**
     * 限流时的降级响应。
     *
     * <p>返回一个标记 degraded=true + 说明"请求频率过高"的 JSON，
     * 让 LLM 知道"不是没数据，是限流了"。
     */
    private String buildRateLimitedResponse(String symbol) {
        MarketPrice last = lastKnownGoodPrice.get(symbol);
        if (last != null) {
            return MarketPrice.degraded(last.symbol(), last.price(), last.currency()).toJson();
        }
        return "{\"symbol\":\"" + symbol + "\",\"error\":\"请求频率过高，已限流\",\"degraded\":true}";
    }

    /**
     * 行情失败的降级响应：优先 lastKnownGood，否则 Mock。
     */
    private String buildFallbackPriceResponse(String symbol) {
        MarketPrice last = lastKnownGoodPrice.get(symbol);
        if (last != null) {
            log.info("行情降级到 lastKnownGood: symbol={}, price={}", symbol, last.price());
            return MarketPrice.degraded(last.symbol(), last.price(), last.currency()).toJson();
        }
        // 全新 Mock（基于 symbol 确定性生成）
        double mockPrice = 100.0 + (Math.abs(symbol.hashCode()) % 1900);
        return MarketPrice.degraded(symbol, mockPrice, "CNY").toJson();
    }

    /**
     * 基本面失败的降级响应：优先 lastKnownGood，否则 Mock。
     */
    private String buildFallbackFundamentalResponse(String symbol) {
        MarketFundamental last = lastKnownGoodFundamental.get(symbol);
        if (last != null) {
            return MarketFundamental.degraded(last.symbol(), last.pe(), last.pb(), last.sector()).toJson();
        }
        double mockPe = 10.0 + (Math.abs(symbol.hashCode()) % 40);
        double mockPb = 1.0 + (Math.abs(symbol.hashCode()) % 15);
        return MarketFundamental.degraded(symbol, mockPe, mockPb, "未知").toJson();
    }

    // -------------------------------------------------------------------------
    // 可观测性：统计快照
    // -------------------------------------------------------------------------

    public long getRateLimitedCount() {
        return rateLimitedCount.get();
    }

    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    public long getAdapterCallCount() {
        return adapterCallCount.get();
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }

    @Override
    public String toString() {
        return String.format("MarketDataGateway{rateLimited=%d, cacheHits=%d, adapterCalls=%d, fallbacks=%d, cache=%s}",
                getRateLimitedCount(), getCacheHitCount(), getAdapterCallCount(),
                getFallbackCount(), cache.toString());
    }
}
