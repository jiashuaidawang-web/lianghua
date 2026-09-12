package com.quant.agent.application.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

// ============================================================================================
// 【Day 3 · 阅读入口】StockTools —— LLM 能"伸手"调的 Java 方法集合（工具工具箱）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 3 的"能力仓库"。LLM 通过它获取真实数据（价格/基本面）。
//   建议阅读时机：Day 3 最先读它（先搞清楚"LLM 能调什么"）。
//   学完能回答：
//     1. @Tool 注解到底做了什么？LLM 是怎么"看见"这些 Java 方法的？
//     2. @P 注解是干什么的？
//     3. 为什么 Tool 方法要返回 String 而不是强类型对象？
//
//   💡 核心魔法：@Tool 是怎么让 LLM"看见" Java 方法的？
//     1. 你在 Java 方法上加 @Tool("描述") 和 @P("参数描述")
//     2. 运行时，LangChain4j 的 AiServices 代理在启动时扫描这些注解
//     3. 把每个 @Tool 方法翻译成一份"工具说明书"（JSON）：
//          {
//            "name": "getStockPrice",
//            "description": "获取股票当前价格，输入股票代码，返回价格信息",
//            "parameters": {
//              "type": "object",
//              "properties": {
//                "symbol": {"type": "string", "description": "股票代码，例如 600519"}
//              }
//            }
//          }
//     4. 这份"说明书"会作为 SystemMessage 的一部分发给 LLM
//     5. LLM 看到说明书，就知道"哦，我可以调用 getStockPrice(symbol=...) 获取数据"
//     6. 当 LLM 需要数据时，它返回一个"工具调用请求"（tool_use），而不是纯文本
//     7. LangChain4j 拦截这个请求，找到对应的 Java 方法，执行，拿到返回值
//     8. 把返回值作为"工具结果"（tool_result）塞回对话，让 LLM 继续推理
//     9. LLM 拿到工具结果后，生成最终文本回复
//
//   💡 关于工具调用循环（重点！）：
//     LLM 调用工具不是一次请求完成的，而是多轮对话：
//
//     第 1 轮：用户问"分析贵州茅台" + 工具说明书
//              → LLM 想："我需要真实价格" → 返回 tool_use(getStockPrice, symbol="600519")
//     第 2 轮：系统把 tool_use 转成 Java 调用 getStockPrice("600519")
//              → 拿到返回值 "{\"symbol\":\"600519\",\"price\":1500.0,...}"
//              → 把返回值作为 tool_result 塞回对话
//     第 3 轮：LLM 看到 tool_result，终于有数据了 → 生成最终分析文本
//
//     这一切都是 LangChain4j 在 AiServices 代理内部自动完成的！
//     你的代码只需要写 @Tool 方法 + 调 analyzeWithTools()，循环被框架托管。
//
//   💡 为什么 Tool 方法返回 String 而不是 StockAnalysis 这样的强类型？
//     因为工具返回值会被塞进对话作为"一段文本"让 LLM 阅读。
//     LLM 只懂文本，不懂 Java 对象。所以返回 JSON 字符串最灵活：
//       - LLM 能读懂 JSON 结构
//       - 框架不需要为每种返回类型做反序列化
//       - 加新工具不用改任何类型定义
//
//   💡 @Tool 方法的命名和描述很重要！
//     LLM 根据 description 决定"什么时候调这个工具"。
//     描述越清晰，LLM 越能正确选择工具。
//     比如"获取股票当前价格，输入股票代码，返回价格信息"就比"查价格"好 100 倍。
//
//   ⬇ 下一步：看 StockAnalysisWithToolAiService（带工具能力的 AiService 接口）。
// ============================================================================================

/**
 * 股票分析工具集。
 *
 * <p>每个 @Tool 方法 = LLM 可以调用的一个"能力"。
 * LLM 决定调哪个工具、传什么参数；Java 真正执行。
 *
 * <p>类比：Command 模式 —— 每个方法是一个 Command，LLM 是调用者，Java 是执行者。
 */
@Component  // 必须是 Spring Bean，AiServices.builder().tools(stockTools) 才能注入
public class StockTools {

    /**
     * 获取股票当前价格。
     *
     * <p>当前返回硬编码值（Mock），后续可替换为真实行情 API。
     *
     * @Tool 注解：
     *   参数就是"工具说明书"的 description。
     *   LLM 读这段话，就知道"什么时候该调这个方法"。
     *
     * @P 注解：
     *   参数的说明书。symbol 是入参，告诉 LLM "这要传个股票代码，例如 600519"。
     *   没有 @P 的话，LLM 不知道这个参数该传什么。
     */
    @Tool("获取股票当前价格，输入股票代码，返回价格信息")
    public String getStockPrice(@P("股票代码，例如 600519") String symbol) {
        // TODO: 接入真实行情 API，当前返回 Mock 数据
        // 返回 JSON 字符串：LLM 能读懂，框架也能直接塞进对话
        return "{\"symbol\":\"" + symbol + "\",\"price\":1500.0,\"currency\":\"CNY\"}";
    }

    /**
     * 获取股票基本面信息。
     *
     * 和 getStockPrice 结构一模一样：@Tool 声明能力，@P 声明参数，返回 JSON 字符串。
     */
    @Tool("获取股票基本面信息，包括市盈率、市净率、行业")
    public String getFundamental(@P("股票代码，例如 600519") String symbol) {
        // TODO: 接入真实数据源，当前返回 Mock 数据
        return "{\"symbol\":\"" + symbol + "\",\"pe\":30.5,\"pb\":8.2,\"sector\":\"白酒\"}";
    }
}
