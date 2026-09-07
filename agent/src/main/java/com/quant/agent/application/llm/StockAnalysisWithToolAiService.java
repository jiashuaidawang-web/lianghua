package com.quant.agent.application.llm;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 支持工具调用的股票分析 AiService 接口。
 *
 * <p>与 StockAnalysisAiService 的区别：
 * <ul>
 *   <li>StockAnalysisAiService：LLM 直接输出结构化 JSON（Day 2）</li>
 *   <li>StockAnalysisWithToolAiService：LLM 可以调用 @Tool 工具获取数据，再生成分析（Day 3）</li>
 * </ul>
 *
 * <p>LLM 会自主决定：是否需要调工具、调哪个工具、传什么参数。
 */
public interface StockAnalysisWithToolAiService {

    String SYSTEM_PROMPT = """
            你是一个专业的量化金融分析师。
            你可以调用工具获取股票的实时数据和基本面信息，然后基于真实数据给出分析。
            如果用户的问题需要数据支持，请先调用工具获取数据，再给出分析。
            """;

    @UserMessage(SYSTEM_PROMPT + "\n\n请分析股票：{{symbol}}")
    String analyzeWithTools(@V("symbol") String symbol);
}
