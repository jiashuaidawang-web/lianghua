package com.quant.agent.infrastructure.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// ============================================================================================
// 【Day 1 + Day 2 · 阅读入口】LlmConfiguration —— 把 LLM 连接"零件"装配成 Spring Bean 的工厂。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：基础设施层核心配置。向下读 LlmProperties，向上提供两个 Model Bean。
//   建议阅读时机：Day 1 读完 QuantLlmService 后回来、以及 Day 2 理解 Structured Output 时。
//   学完能回答：
//     1. 工程里有"两个"LLM（StreamingChatModel 和 ChatModel），为什么不能共用一个？
//     2. Day 2 的 Structured Output 到底靠什么"强制" LLM 返回 JSON？
//
//   跨 Day 说明：
//     - Day 1 贡献：streamingChatModel() —— 用于 SSE 流式（必须边生成边推）。
//     - Day 2 贡献：chatLanguageModel() + stockAnalysisResponseFormat() —— 用于结构化输出。
//
//   💡 为什么不能共用一个 Model？
//     流式模型（StreamingChatModel）是"边生成边吐"的，你永远拿不到"完整 JSON"；
//     而 Day 2 的 Structized Output 必须等 LLM 把整个 JSON 生成完，才能反序列化成 DTO。
//     所以：流式场景用 StreamingChatModel；结构化场景用 ChatModel（一次性拿完整结果）。
//
//   💡 Day 2 的"强制 JSON"到底靠什么？
//     不是靠 prompt 写"请返回 JSON"（LLM 不听话的）。
//     而是靠 responseFormat(...) 在 API 层给模型发一个 JSON Schema，
//     不符合 Schema 的输出会被 API 自己拒绝/重试 —— 这是协议级硬约束，不是 prompt 软约束。
//     类比：相当于在 HTTP 接口上声明了 @Valid @RequestBody StockAnalysis，
//     不符合就直接 400，而不是靠业务代码 if 判断。
//
//   ⬇ 下一步：Day 1 路线看 QuantLlmService（消费 StreamingChatModel）；
//            Day 2 路线看 StockAnalysisAiService + StructuredAnalysisService。
// ============================================================================================

@Configuration
// 启用 LlmProperties：没有这行，@ConfigurationProperties 不会生效，yml 就绑不上去
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    // -------------------------------------------------------------------------
    // Day 2 核心：StockAnalysis 的原生 JSON Schema 定义
    // -------------------------------------------------------------------------
    // 这个方法构建一个 ResponseFormat，告诉 LLM："你必须返回符合这个 Schema 的 JSON"。
    // 这是 API 级硬约束：结构错误由 API 层保证，不再依赖 prompt 文字"求 LLM 遵守格式"。
    //
    // 为什么不用 prompt 写 Schema？
    //   prompt 是"建议"，LLM 可能偷懒/改写/加多余字段；
    //   responseFormat 是"合同"，违反就直接被拒，100% 可靠。
    //
    // 类比 Spring AI 的 .entity(StockAnalysis.class) —— 效果一样，但这是 LangChain4j 原生写法。
    private static ResponseFormat stockAnalysisResponseFormat() {
        // 定义 JSON 对象 Schema：4 个字段 + 类型 + 枚举约束 + 必填
        JsonObjectSchema schema = JsonObjectSchema.builder()
                .description("股票分析结果")
                // action 只能是 BUY/SELL/HOLD 三个值之一（enum 约束）
                .addEnumProperty("action", List.of("BUY", "SELL", "HOLD"), "操作：BUY 买入 / SELL 卖出 / HOLD 持有")
                .addStringProperty("symbol", "股票代码")
                .addNumberProperty("score", "推荐强度 0.0~10.0")
                .addStringProperty("reason", "分析理由")
                // required：少一个字段 → API 视为非法
                .required(List.of("action", "symbol", "score", "reason"))
                // additionalProperties(false)：LLM 多返回一个字段 → 非法（严格模式）
                .additionalProperties(false)
                .build();

        // 把对象 Schema 包成 JsonSchema（给个名字 "StockAnalysis"，方便日志/报错识别）
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("StockAnalysis")
                .rootElement(schema)
                .build();

        // 最终返回：ResponseFormat 声明"类型=JSON，且必须符合这个 Schema"
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
    }

    // -------------------------------------------------------------------------
    // Day 1：流式 LLM（用于 SSE 边生成边推）
    // -------------------------------------------------------------------------
    // 为什么用 OpenAiStreamingChatModel？
    //   因为我们的 LLM 服务（LongCat）兼容 OpenAI API 协议，
    //   LangChain4j 的 OpenAi*Model 就是 OpenAI 协议的客户端。
    //   换其他厂商就换其他 Model（如 ZhipuAiChatModel、QwenChatModel），上层代码不用改。
    @Bean
    public StreamingChatModel streamingChatModel(LlmProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .build();
    }

    // -------------------------------------------------------------------------
    // Day 2：非流式 LLM（用于 Structured Output，必须拿完整 JSON）
    // -------------------------------------------------------------------------
    // 和 streamingChatModel 的唯一区别：
    //   1. 类型是 ChatModel（不是 StreamingChatModel）—— 一次性返回完整结果
    //   2. 多了一个 .responseFormat(stockAnalysisResponseFormat()) —— 强制 JSON Schema
    //
    // ⚠ 注意：responseFormat 是"模型级"配置，配了就对所有调用生效。
    //   所以这个 Bean 专门给"需要 JSON"的场景用；流式场景绝不能用它（流式拿不到完整 JSON）。
    @Bean
    public ChatModel chatLanguageModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .responseFormat(stockAnalysisResponseFormat())
                .build();
    }

    // -------------------------------------------------------------------------
    // Day 5：非流式 LLM（无 Schema 约束，供 Planner 使用）
    // -------------------------------------------------------------------------
    // 为什么不能复用 chatLanguageModel？
    //   chatLanguageModel 硬编码了 StockAnalysis 的 responseFormat（API 级硬约束），
    //   对这个 Bean 的每一次调用都强制返回 {action,symbol,score,reason}。
    //   但 Planner 需要返回 List<Task>（完全不同的 JSON 结构），
    //   复用同一个 Bean → 反序列化失败 → 异常（这就是 planner 20ms 报错 error=null 的根因）。
    //
    // 所以 planner 需要一个"裸"ChatModel：只负责把 prompt 送出去、拿回完整文本，
    // 返回什么格式由 prompt 决定，校验/重试由 PlannerService 负责。
    @Bean
    public ChatModel plainChatLanguageModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .build();
    }
}
