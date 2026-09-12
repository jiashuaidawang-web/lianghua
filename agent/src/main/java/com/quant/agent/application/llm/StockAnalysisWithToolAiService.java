package com.quant.agent.application.llm;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

// ============================================================================================
// 【Day 3 · 阅读入口】StockAnalysisWithToolAiService —— "能调工具"的 AiService 接口。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 3 的"LLM 调用抽象"，和 Day 2 的 StockAnalysisAiService 是"双胞胎"。
//   建议阅读时机：读完 StockTools 后立刻读它。
//   学完能回答：
//     1. 它和 StockAnalysisAiService 到底差在哪？
//     2. 为什么返回 String 而不是 StockAnalysis？
//     3. ".tools(stockTools)"是怎么让这个接口"获得工具"的？
//
//   💡 和 StockAnalysisAiService 的关键差异：
//
//     ┌──────────────────────┬────────────────────────────┬────────────────────────────┐
//     │ 维度                  │ StockAnalysisAiService     │ StockAnalysisWithToolAiService │
//     ├──────────────────────┼────────────────────────────┼────────────────────────────┤
//     │ 用途                  │ LLM 直接输出 JSON（Day 2）  │ LLM 可调用 @Tool 获取数据（Day 3）│
//     │ 返回类型              │ StockAnalysis（强类型 DTO） │ String（纯文本）             │
//     │ 用的 Model            │ ChatModel（非流式）         │ ChatModel（非流式）          │
//     │ 是否注入 tools        │ 否                          │ 是（.tools(stockTools)）     │
//     │ 结构化输出保证        │ responseFormat(JSON Schema) │ 无（LLM 自由输出文本）        │
//     └──────────────────────┴────────────────────────────┴────────────────────────────┘
//
//   💡 为什么返回 String 而不是 StockAnalysis？
//     因为 Day 3 的 LLM 要"先调工具再分析"，它的输出是"一段结合工具数据的分析文本"，
//     不是固定结构的 JSON。LLM 自由发挥的空间更大。
//     代价：下游不能直接用 .action() / .score()，得再解析文本（或再调一次 Day 2 接口）。
//
//   💡 ".tools(stockTools)"的魔法（在 AiServicesConfiguration 里完成）：
//     AiServices.builder(Xxx.class)
//         .chatModel(chatLanguageModel)
//         .tools(stockTools)   // ← 关键：把 StockTools 这个 Bean 的所有 @Tool 方法注册给代理
//         .build();
//
//     注册后，代理在运行时：
//       1. 扫描 stockTools 对象里所有 @Tool 方法
//       2. 生成"工具说明书"列表（每个方法一份 JSON）
//       3. 每次调 analyzeWithTools() 时，把说明书塞进发给 LLM 的消息里
//       4. 如果 LLM 返回 tool_use，代理自动找对应 Java 方法执行
//       5. 把执行结果塞回对话，让 LLM 继续
//       6. 循环直到 LLM 返回纯文本（不再调工具）
//
//   💡 System Prompt 的差异：
//     Day 2 的 prompt 只说"你是分析师，分析股票"。
//     Day 3 的 prompt 多了一句："你可以调用工具获取实时数据和基本面信息"。
//     这不是废话 —— LLM 读到这句，才知道"我有工具可以用"。
//
//   ⬇ 下一步：看 StockToolController（Day 3 的 HTTP 入口）。
// ============================================================================================

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

    // System Prompt：明确告诉 LLM "你有工具可以用，需要数据时先调工具"
    // 这句"如果用户的问题需要数据支持，请先调用工具获取数据"是关键指令
    String SYSTEM_PROMPT = """
            你是一个专业的量化金融分析师。
            你可以调用工具获取股票的实时数据和基本面信息，然后基于真实数据给出分析。
            如果用户的问题需要数据支持，请先调用工具获取数据，再给出分析。
            """;

    // 返回 String：LLM 自由发挥的分析文本（不是固定结构 JSON）
    // 为什么不像 Day 2 那样返回 StockAnalysis？
    //   因为 Day 3 的 LLM 要结合工具返回的自由文本数据再分析，输出形式不固定，
    //   强行要求 JSON 反而限制 LLM，且工具调用本身就破坏了"一次返回"的假设。
    @UserMessage(SYSTEM_PROMPT + "\n\n请分析股票：{{symbol}}")
    String analyzeWithTools(@V("symbol") String symbol);
}
