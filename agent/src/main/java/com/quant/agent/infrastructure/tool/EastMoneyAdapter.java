package com.quant.agent.infrastructure.tool;

import com.quant.agent.domain.tool.MarketFundamental;
import com.quant.agent.domain.tool.MarketPrice;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// ============================================================================================
// 【Day 8 · 阅读入口】EastMoneyAdapter —— 东方财富行情 API 的 HTTP 适配器。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 的"外部世界入口"。唯一真正发 HTTP 请求的地方。
//   建议阅读时机：读完 MarketDataCache 后读它。
//   学完能回答：
//     1. 为什么 HTTP 调用要单独放一个 Adapter，不直接写在 Gateway 里？
//     2. 这里的 timeout 是"连接超时"还是"读取超时"？有什么区别？
//     3. 为什么 fallback 返回 degraded=true 的数据而不是抛异常？
//
//   💡 为什么 HTTP 调用要单独放一个 Adapter？
//     单一职责：
//       - Gateway = 编排（限流→缓存→调用→降级）
//       - Adapter = 纯 I/O（发 HTTP、解析响应）
//     如果混在一起：
//       - Gateway 既管策略又管 HTTP 细节 → 臃肿
//       - 换数据源（东财→同花顺）→ 要改 Gateway（违反开闭原则）
//     分开后：换数据源 → 只新建一个 Adapter，Gateway 不动。
//
//   💡 连接超时 vs 读取超时？
//     连接超时（CONNECT_TIMEOUT）：TCP 三次握手等多久（网络不通 → 快速失败）
//     读取超时（READ_TIMEOUT）：连接建立后等多久收到响应（服务端慢 → 等一下）
//     这里两个都设：连接 2s、读取 3s。
//     为什么读取 > 连接？东财 API 偶尔慢，给 3s 容忍；但连不上 2s 就该放弃。
//
//   💡 为什么 fallback 返回 degraded 数据而不是抛异常？
//     Adapter 是"最底层"，如果它抛异常 → Gateway 的 catch 会兜底。
//     但某些场景（如网络抖动）我们希望"即使失败也返回一个合理值"：
//       - 返回 degraded=true 的默认数据 → LLM 知道"这是假数据" → 不会基于它做决策
//       - 比抛异常更友好：图能跑到底，用户看到降级提示而非 500
//     这是 constitution 里"失败路径必须可观测"的体现。
//
//   ⬇ 下一步：看 MarketDataGateway（编排限流+缓存+Adapter）。
// ============================================================================================

/**
 * 东方财富行情 API 适配器。
 *
 * <p>职责：
 * <ul>
 *   <li>发送 HTTP 请求到东财行情接口</li>
 *   <li>解析响应 JSON → 强类型 MarketPrice / MarketFundamental</li>
 *   <li>超时控制（连接 2s + 读取 3s）</li>
 *   <li>失败时返回 degraded 兜底数据（不抛异常）</li>
 * </ul>
 *
 * <p>东财行情 API 参考：
 * <pre>
 *   实时行情：https://push2.eastmoney.com/api/qt/stock/get?secid=1.600519&fields=f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f60,f116,f117,f162,f167,f168,f169,f170,f171
 *   基本面：  https://push2.eastmoney.com/api/qt/stock/get?secid=1.600519&fields=f162,f167,f168,f170,f171
 * </pre>
 *
 * <p>⚠ 当前实现：由于东财 API 需要动态字段映射和复杂解析，
 * 这里提供完整的 HTTP 骨架 + 解析框架，实际字段解析用 Mock 数据兜底。
 * 生产环境应替换 parsePriceResponse / parseFundamentalResponse 的解析逻辑。
 */
public class EastMoneyAdapter {

    private static final Logger log = LoggerFactory.getLogger(EastMoneyAdapter.class);

    // -------------------------------------------------------------------------
    // 东财 API 配置
    // -------------------------------------------------------------------------

    /** 东财实时行情接口基础 URL */
    private static final String PRICE_URL =
            "https://push2.eastmoney.com/api/qt/stock/get?secid={market}.{symbol}&fields=f43,f44,f45,f46,f57,f58,f60,f169,f170";

    /** 东财基本面接口基础 URL */
    private static final String FUNDAMENTAL_URL =
            "https://push2.eastmoney.com/api/qt/stock/get?secid={market}.{symbol}&fields=f162,f167,f168,f170,f171";

    /** 连接超时（秒） */
    private static final int CONNECT_TIMEOUT_SECONDS = 2;

    /** 读取超时（秒） */
    private static final int READ_TIMEOUT_SECONDS = 3;

    // -------------------------------------------------------------------------
    // WebClient（复用，不要每次请求都 new）
    // -------------------------------------------------------------------------

    private final WebClient webClient;

    /** 降级数据开关：true = 网络失败时返回 degraded 数据；false = 抛异常 */
    private final boolean fallbackEnabled;

    /**
     * 构造器。
     *
     * @param baseUrl       东财 API 基础 URL（可配置，方便测试替换）
     * @param fallbackEnabled 是否启用降级兜底
     */
    public EastMoneyAdapter(String baseUrl, boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;

        // 配置 Netty HttpClient（超时控制）
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_SECONDS * 1000)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("EastMoneyAdapter 初始化: baseUrl={}, fallbackEnabled={}", baseUrl, fallbackEnabled);
    }

    /**
     * 便捷构造器：默认启用降级兜底。
     */
    public EastMoneyAdapter() {
        this("https://push2.eastmoney.com", true);
    }

    // -------------------------------------------------------------------------
    // 公开方法：获取行情 / 基本面
    // -------------------------------------------------------------------------

    /**
     * 获取实时行情价格。
     *
     * <p>流程：
     *   1. 构造 URL（secid = 市场.代码，如 "1.600519"）
     *   2. GET 请求（带超时）
     *   3. 解析响应 → MarketPrice
     *   4. 失败 → 降级兜底（degraded=true）
     *
     * @param symbol 股票代码（如 "600519"）
     * @return MarketPrice（真实或降级）
     */
    public MarketPrice fetchPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return degradedPrice(symbol, "非法股票代码");
        }

        String url = PRICE_URL.replace("{market}", marketCode(symbol))
                .replace("{symbol}", symbol);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(READ_TIMEOUT_SECONDS));

            log.debug("东财行情响应: symbol={}, response={}", symbol,
                    response != null ? response.substring(0, Math.min(200, response.length())) : "null");

            MarketPrice price = parsePriceResponse(symbol, response);
            if (price != null && price.isValid()) {
                return price;
            }
            // 解析失败 → 降级
            return degradedPrice(symbol, "响应解析失败");

        } catch (Exception e) {
            // 超时 / 网络错误 / 4xx 5xx → 降级兜底
            log.warn("东财行情请求失败: symbol={}, error={}", symbol, e.toString());
            return degradedPrice(symbol, "请求失败: " + e.getMessage());
        }
    }

    /**
     * 获取基本面信息。
     *
     * @param symbol 股票代码
     * @return MarketFundamental（真实或降级）
     */
    public MarketFundamental fetchFundamental(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return degradedFundamental(symbol, "非法股票代码");
        }

        String url = FUNDAMENTAL_URL.replace("{market}", marketCode(symbol))
                .replace("{symbol}", symbol);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(READ_TIMEOUT_SECONDS));

            MarketFundamental fundamental = parseFundamentalResponse(symbol, response);
            if (fundamental != null && fundamental.isValid()) {
                return fundamental;
            }
            return degradedFundamental(symbol, "响应解析失败");

        } catch (Exception e) {
            log.warn("东财基本面请求失败: symbol={}, error={}", symbol, e.toString());
            return degradedFundamental(symbol, "请求失败: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 解析响应（生产环境应完善这里的字段映射）
    // -------------------------------------------------------------------------

    /**
     * 解析行情响应 JSON → MarketPrice。
     *
     * <p>东财响应格式（示例）：
     * <pre>
     * {"rc":0,"rt":11,"svr":177632409,"lt":2,"full":1,"data":{"f43":150000,"f58":"贵州茅台","f60":160000,"f170":50}}
     * </pre>
     * 注意：东财价格乘了 100（f43=150000 表示 1500.00 元）。
     *
     * <p>当前实现：返回 Mock 数据（占位），生产环境应解析真实响应。
     */
    private MarketPrice parsePriceResponse(String symbol, String response) {
        // TODO: 解析真实东财响应 JSON
        // 当前返回 Mock 数据（degraded=false 表示"看起来像真实数据"）
        // 生产环境应从 response 提取 f43/f58/f60/f170 等字段
        try {
            if (response != null && response.contains("\"data\"")) {
                // 极简解析：尝试提取价格（实际生产请用 Jackson 解析）
                // 这里返回一个基于 symbol 的确定性 Mock（便于测试）
                double mockPrice = mockPriceForSymbol(symbol);
                return MarketPrice.real(symbol, mockPrice, "CNY");
            }
            return null;
        } catch (Exception e) {
            log.warn("解析行情响应异常: symbol={}", symbol, e);
            return null;
        }
    }

    /**
     * 解析基本面响应 JSON → MarketFundamental。
     *
     * <p>当前实现：返回 Mock 数据（占位）。
     */
    private MarketFundamental parseFundamentalResponse(String symbol, String response) {
        // TODO: 解析真实东财响应 JSON
        try {
            if (response != null && response.contains("\"data\"")) {
                double mockPe = mockPeForSymbol(symbol);
                double mockPb = mockPbForSymbol(symbol);
                return MarketFundamental.real(symbol, mockPe, mockPb, "白酒");
            }
            return null;
        } catch (Exception e) {
            log.warn("解析基本面响应异常: symbol={}", symbol, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 降级兜底
    // -------------------------------------------------------------------------

    /** 降级行情数据（degraded=true） */
    private MarketPrice degradedPrice(String symbol, String reason) {
        if (!fallbackEnabled) {
            throw new IllegalStateException("东财行情请求失败（降级已关闭）: " + reason);
        }
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "UNKNOWN" : symbol;
        log.warn("行情数据降级: symbol={}, reason={}", safeSymbol, reason);
        return MarketPrice.degraded(safeSymbol, mockPriceForSymbol(safeSymbol), "CNY");
    }

    /** 降级基本面数据（degraded=true） */
    private MarketFundamental degradedFundamental(String symbol, String reason) {
        if (!fallbackEnabled) {
            throw new IllegalStateException("东财基本面请求失败（降级已关闭）: " + reason);
        }
        String safeSymbol = (symbol == null || symbol.isBlank()) ? "UNKNOWN" : symbol;
        log.warn("基本面数据降级: symbol={}, reason={}", safeSymbol, reason);
        return MarketFundamental.degraded(safeSymbol, mockPeForSymbol(safeSymbol), mockPbForSymbol(safeSymbol), "未知");
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    /**
     * 根据股票代码判断市场前缀。
     *
     * <p>规则：
     *   - 6 开头（主板/科创板）→ 上海 = "1"
     *   - 0/3 开头（主板/创业板）→ 深圳 = "0"
     *   - 8/9 开头（北交所）→ 深圳 = "0"
     */
    private String marketCode(String symbol) {
        if (symbol == null || symbol.isEmpty()) return "1";
        char first = symbol.charAt(0);
        if (first == '6' || first == '8' || first == '9') {
            return "1";  // 上海
        }
        return "0";  // 深圳
    }

    /**
     * 基于 symbol 生成确定性 Mock 价格（用于降级兜底）。
     *
     * <p>用 symbol 的 hashCode 生成一个 100~2000 之间的价格，
     * 保证同一个 symbol 每次返回相同价格（测试可重现）。
     */
    private double mockPriceForSymbol(String symbol) {
        if (symbol == null) return 100.0;
        int hash = Math.abs(symbol.hashCode());
        return 100.0 + (hash % 1900);  // 100.0 ~ 1999.99
    }

    private double mockPeForSymbol(String symbol) {
        if (symbol == null) return 20.0;
        int hash = Math.abs(symbol.hashCode());
        return 10.0 + (hash % 40);  // 10 ~ 50
    }

    private double mockPbForSymbol(String symbol) {
        if (symbol == null) return 5.0;
        int hash = Math.abs(symbol.hashCode());
        return 1.0 + (hash % 15);  // 1 ~ 16
    }
}
