package com.quant.agent.application.llm;

import com.quant.agent.domain.output.StockAnalysis;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

// ============================================================================================
// 【Day 2 · 阅读入口】StockAnalysisAiService —— LangChain4j 的"声明式代理接口"（核心魔法）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 2 的"LLM 调用抽象"。
//     你只写接口 → LangChain4j 自动生成实现 → 调 LLM → 反序列化成 StockAnalysis
//   建议阅读时机：Day 2 读完 StockAnalysis 后立刻读它。
//   学完能回答：
//     1. 为什么"写个接口"就能调 LLM？实现类在哪？
//     2. @UserMessage / @V 注解做了什么？
//     3. 结构化输出到底由谁保证 —— prompt 还是别的？
//
//   💡 核心魔法：AiServices.create() 在运行时动态生成这个接口的代理实现
//     类比：OpenFeign 的 @FeignClient 接口 —— 你写接口 + 注解，框架在运行时生成 HTTP 调用代码。
//     这里一样：你写接口 + LangChain4j 注解，框架在运行时生成"调 LLM + 反序列化"代码。
//
//     生成过程（AiServicesConfiguration.stockAnalysisAiService() 里完成）：
//       AiServices.create(StockAnalysisAiService.class, chatLanguageModel)
//         → JDK 动态代理 / ByteBuddy 生成实现类
//           → 拦截 analyze() 方法调用：
//             ① 读 @UserMessage 模板，把 {{symbol}} 替换成入参
//             ② 组装 SystemMessage + UserMessage
//             ③ 调 chatLanguageModel.chat(...)
//             ④ 拿到 LLM 返回的 JSON 字符串
//             ⑤ 反序列化成 StockAnalysis record
//             ⑥ 返回给调用方
//
//   💡 @UserMessage 里的 {{symbol}} 是什么？
//     这是 LangChain4j 的模板占位符，运行时用 @V("symbol") 标注的参数替换。
//     最终发给 LLM 的 UserMessage 是：
//       "你是一个专业的量化金融分析师...\n\n分析 贵州茅台"
//
//   💡 结构化输出由谁保证？
//     不是 prompt 里写"请返回 JSON"（那是 Day 0 的做法，不可靠）。
//     而是 LlmConfiguration 里给 ChatModel 配了 responseFormat(JSON Schema)，
//     API 层强制 LLM 输出符合 StockAnalysis 字段的 JSON —— 协议级硬约束。
//     prompt 只负责"角色定义 + 指令"，不再重复 JSON Schema 描述。
//
//   ⚠ 为什么返回类型是 StockAnalysis 而不是 String？
//     因为 chatLanguageModel 配了 responseFormat，LangChain4j 知道要把 JSON 反序列化成这个类型。
//     如果你用流式模型（StreamingChatModel），是拿不到完整 JSON 的，就没法反序列化。
//
//   ⬇ 下一步：看 StructuredAnalysisService，它包装这个接口，加了校验 + 重试。
// ============================================================================================

/**
 * AiServices 声明式代理接口。
 *
 * <p>你写接口，LangChain4j 自动实现：组装 SystemMessage + UserMessage → 调 LLM → 反序列化 → 返回 DTO。
 *
 * <p>类比：Feign 的接口 —— 写接口，框架实现。
 *
 * <p><b>结构化输出由 API 级 responseFormat 强制约束</b>（见 LlmConfiguration），
 * prompt 只负责角色定义和指令，不再重复 JSON Schema 描述。
 */
public interface StockAnalysisAiService {

    // -------------------------------------------------------------------------
    // System Prompt：定义 LLM 是谁、要做什么
    // -------------------------------------------------------------------------
    // 注意：这里用的是 Java 17 的文本块（Text Block，三引号），多行字符串不用 + 拼接。
    // 这个常量会被 @UserMessage 引用，作为每次调用的"系统指令"。
    String SYSTEM_PROMPT = """
            你是一个专业的量化金融分析师。
            请分析用户给出的股票，给出操作建议、推荐强度和分析理由。
            """;

    // -------------------------------------------------------------------------
    // 唯一的业务方法：给一个股票代码，返回结构化分析结果
    // -------------------------------------------------------------------------
    // @UserMessage：这是 LangChain4j 的核心注解，把"模板 + 参数"组合成 UserMessage。
    //   模板 = SYSTEM_PROMPT + "\n\n分析 {{symbol}}"
    //   运行时 {{symbol}} 被 @V("symbol") 标注的参数替换。
    //
    // 返回类型 StockAnalysis：告诉 LangChain4j "把 LLM 返回的 JSON 反序列化成这个 record"。
    @UserMessage(SYSTEM_PROMPT + "\n\n分析 {{symbol}}")
    StockAnalysis analyze(@V("symbol") String symbol);
    //          ↑
    //          @V("symbol")：把入参绑定到模板里的 {{symbol}} 占位符
}
