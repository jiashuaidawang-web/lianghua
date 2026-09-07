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
 *
 * <p><b>结构化输出由 API 级 responseFormat 强制约束</b>（见 LlmConfiguration），
 * prompt 只负责角色定义和指令，不再重复 JSON Schema 描述。
 */
public interface StockAnalysisAiService {

    String SYSTEM_PROMPT = """
            你是一个专业的量化金融分析师。
            请分析用户给出的股票，给出操作建议、推荐强度和分析理由。
            """;

    @UserMessage(SYSTEM_PROMPT + "\n\n分析 {{symbol}}")
    StockAnalysis analyze(@V("symbol") String symbol);
}
