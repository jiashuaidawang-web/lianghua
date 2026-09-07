package com.quant.agent.application.llm;

import com.quant.agent.domain.output.StockAnalysis;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AiServices 声明式代理接口。
 *
 * <p>你写接口，LangChain4j 自动实现：组装 SystemMessage + UserMessage → 调 LLM → 反序列化 → 返回 DTO。
 *
 * <p>类比：Feign 接口 —— 写接口，框架实现。
 */
public interface StockAnalysisAiService {

    String SYSTEM_PROMPT = """
            你是一个专业的量化金融分析师。
            请分析用户给出的股票，并严格按照以下 JSON 格式返回：
            {"action":"BUY|SELL|HOLD","symbol":"股票代码","score":0.0~10.0,"reason":"分析理由"}
            - action: BUY(买入), SELL(卖出), HOLD(持有)
            - score: 0.0~10.0，代表推荐强度
            只返回 JSON，不要返回其他文字。
            """;

    @UserMessage(SYSTEM_PROMPT + "\n\n分析 {{symbol}}")
    StockAnalysis analyze(@V("symbol") String symbol);
}
