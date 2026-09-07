package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    /**
     * 构建 StockAnalysis 的原生 JSON Schema。
     *
     * <p>这是 API 级硬约束：模型输出会被强制符合此 Schema，不再依赖 prompt 文字"求 LLM 遵守格式"。
     * 等价于 Spring AI 的 .entity(StockAnalysis.class)。
     */
    private static ResponseFormat stockAnalysisResponseFormat() {
        JsonObjectSchema schema = JsonObjectSchema.builder()
                .description("股票分析结果")
                .addEnumProperty("action", List.of("BUY", "SELL", "HOLD"), "操作：BUY 买入 / SELL 卖出 / HOLD 持有")
                .addStringProperty("symbol", "股票代码")
                .addNumberProperty("score", "推荐强度 0.0~10.0")
                .addStringProperty("reason", "分析理由")
                .required(List.of("action", "symbol", "score", "reason"))
                .additionalProperties(false)
                .build();

        JsonSchema jsonSchema = JsonSchema.builder()
                .name("StockAnalysis")
                .rootElement(schema)
                .build();

        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(LlmProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .build();
    }

    /**
     * 非流式 LLM：用于 Structured Output。
     * 必须拿到完整 JSON 才能反序列化，不能使用流式。
     *
     * <p>通过 responseFormat 强制模型输出符合 StockAnalysis Schema 的 JSON，
     * 结构错误由 API 层保证，不再依赖 prompt + retry 兜底。
     */
    @Bean
    public ChatModel chatLanguageModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .responseFormat(stockAnalysisResponseFormat())
                .build();
    }
}
