package com.quant.agent.application.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 股票分析工具集。
 *
 * <p>每个 @Tool 方法 = LLM 可以调用的一个"能力"。
 * LLM 决定调哪个工具、传什么参数；Java 真正执行。
 *
 * <p>类比：Command 模式 —— 每个方法是一个 Command，LLM 是调用者，Java 是执行者。
 */
@Component
public class StockTools {

    /**
     * 获取股票当前价格。
     *
     * <p>当前返回硬编码值（Mock），后续可替换为真实行情 API。
     */
    @Tool("获取股票当前价格，输入股票代码，返回价格信息")
    public String getStockPrice(@P("股票代码，例如 600519") String symbol) {
        // TODO: 接入真实行情 API，当前返回 Mock 数据
        return "{\"symbol\":\"" + symbol + "\",\"price\":1500.0,\"currency\":\"CNY\"}";
    }

    /**
     * 获取股票基本面信息。
     */
    @Tool("获取股票基本面信息，包括市盈率、市净率、行业")
    public String getFundamental(@P("股票代码，例如 600519") String symbol) {
        // TODO: 接入真实数据源，当前返回 Mock 数据
        return "{\"symbol\":\"" + symbol + "\",\"pe\":30.5,\"pb\":8.2,\"sector\":\"白酒\"}";
    }
}
