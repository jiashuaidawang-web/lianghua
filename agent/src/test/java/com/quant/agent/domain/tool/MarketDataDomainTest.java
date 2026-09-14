package com.quant.agent.domain.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarketPrice / MarketFundamental 领域对象单元测试。
 *
 * <p>验证：强类型、isValid() 校验、toJson() 序列化、degraded 标记。
 */
class MarketDataDomainTest {

    // ========================================================================
    // MarketPrice
    // ========================================================================

    @Test
    void shouldCreateValidMarketPrice() {
        MarketPrice price = MarketPrice.real("600519", 1500.0, "CNY");

        assertEquals("600519", price.symbol());
        assertEquals(1500.0, price.price());
        assertEquals("CNY", price.currency());
        assertFalse(price.degraded());
        assertTrue(price.isValid());
    }

    @Test
    void shouldRejectInvalidPrice() {
        // price <= 0 → 非法
        MarketPrice invalid = new MarketPrice("600519", 0.0, "CNY", false);
        assertFalse(invalid.isValid());

        MarketPrice negative = new MarketPrice("600519", -100.0, "CNY", false);
        assertFalse(negative.isValid());
    }

    @Test
    void shouldRejectNullSymbol() {
        MarketPrice invalid = new MarketPrice(null, 1500.0, "CNY", false);
        assertFalse(invalid.isValid());
    }

    @Test
    void shouldSerializeToJson() {
        MarketPrice price = MarketPrice.real("600519", 1500.0, "CNY");
        String json = price.toJson();

        assertTrue(json.contains("\"symbol\":\"600519\""));
        assertTrue(json.contains("\"price\":1500.0"));
        assertTrue(json.contains("\"currency\":\"CNY\""));
        assertTrue(json.contains("\"degraded\":false"));
    }

    @Test
    void shouldMarkDegradedData() {
        MarketPrice degraded = MarketPrice.degraded("600519", 1500.0, "CNY");

        assertTrue(degraded.degraded());
        assertTrue(degraded.isValid());  // 降级数据也可以是合法的（只是标记非实时）
        assertTrue(degraded.toJson().contains("\"degraded\":true"));
    }

    // ========================================================================
    // MarketFundamental
    // ========================================================================

    @Test
    void shouldCreateValidFundamental() {
        MarketFundamental fund = MarketFundamental.real("600519", 30.5, 8.2, "白酒");

        assertEquals("600519", fund.symbol());
        assertEquals(30.5, fund.pe());
        assertEquals(8.2, fund.pb());
        assertEquals("白酒", fund.sector());
        assertFalse(fund.degraded());
        assertTrue(fund.isValid());
    }

    @Test
    void shouldAllowNullFields() {
        // pe/pb/sector 可以 null（某些字段东财不返回）
        MarketFundamental partial = new MarketFundamental("600519", null, null, "白酒", false);
        assertTrue(partial.isValid());  // 至少 sector 有值
    }

    @Test
    void shouldRejectAllNullFields() {
        MarketFundamental empty = new MarketFundamental("600519", null, null, null, false);
        assertFalse(empty.isValid());  // 全 null → 非法
    }

    @Test
    void shouldRejectInvalidPe() {
        MarketFundamental invalid = new MarketFundamental("600519", -5.0, 8.2, "白酒", false);
        assertFalse(invalid.isValid());  // pe < 0
    }

    @Test
    void shouldSerializeFundamentalToJson() {
        MarketFundamental fund = MarketFundamental.real("600519", 30.5, 8.2, "白酒");
        String json = fund.toJson();

        assertTrue(json.contains("\"symbol\":\"600519\""));
        assertTrue(json.contains("\"pe\":30.5"));
        assertTrue(json.contains("\"pb\":8.2"));
        assertTrue(json.contains("\"sector\":\"白酒\""));
    }

    @Test
    void shouldOmitNullFieldsInJson() {
        MarketFundamental partial = new MarketFundamental("600519", null, 8.2, null, false);
        String json = partial.toJson();

        assertTrue(json.contains("\"pb\":8.2"));
        assertFalse(json.contains("\"pe\""), "null pe 不应输出");
        assertFalse(json.contains("\"sector\""), "null sector 不应输出");
    }
}
