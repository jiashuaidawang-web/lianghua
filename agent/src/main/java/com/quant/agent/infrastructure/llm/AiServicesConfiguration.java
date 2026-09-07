package com.quant.agent.infrastructure.llm;

import com.quant.agent.application.llm.StockAnalysisAiService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServicesConfiguration {

    /**
     * 创建 AiServices 代理：将 StockAnalysisAiService 接口变成 LLM 调用。
     *
     * <p>类比：Feign 的 @EnableFeignClients —— 接口自动变成远程调用代理。
     */
    @Bean
    public StockAnalysisAiService stockAnalysisAiService(ChatModel chatLanguageModel) {
        return AiServices.create(StockAnalysisAiService.class, chatLanguageModel);
    }
}
