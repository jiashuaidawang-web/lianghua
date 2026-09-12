package com.quant.agent.domain.output;

// ============================================================================================
// 【Day 2 · 阅读入口】StockAnalysis —— LLM 的"输出契约"，整个工程的强类型核心 DTO。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 2/3/4 都依赖它。是 LLM 输出 → Java 对象的"翻译官"。
//   建议阅读时机：Day 2 最先读它（后面的服务都围绕它转）。
//   学完能回答：
//     1. 为什么 LLM 不能直接返回 Java 对象？中间需要什么"契约"？
//     2. record 类型在这里起了什么关键作用？
//     3. isValid() 为什么是 Day 2/3/4 都依赖的"守门员"？
//
//   💡 为什么需要这个 DTO？
//     LLM 返回的是纯文本（或 JSON 字符串），Java 需要强类型对象才能在代码里 .action() / .score()。
//     所以必须有一个"契约"规定：
//       - LLM 必须返回什么字段（action / symbol / score / reason）
//       - 每个字段什么类型（String / Double）
//       - 每个字段什么约束（action ∈ {BUY,SELL,HOLD}, score ∈ [0,10]）
//
//   💡 为什么用 record 不用 class？
//     record 是不可变（immutable）的，天生适合做 DTO：
//       - 自动生成 final 字段 + 构造器 + getter（这里叫 action() 不是 getAction()）
//       - 自动生成 equals / hashCode / toString
//       - 语义清晰：这就是个"数据载体"，没有行为，创建后不能改
//
//   💡 isValid() 为什么是守门员？
//     LLM 是概率性模型，可能：漏字段、拼错枚举（"buy"小写）、score 写成 999、多返回字段。
//     这些"非法输出"如果直接进下游（写数据库、做交易决策），会出大事故。
//     所以 StructuredAnalysisService / AnalysisNode 都要先 isValid() 校验，
//     非法就重试或走失败路径 —— 这就是 constitution 里"LLM 输出视为不可信输入"的实现。
//
//   ⬇ 下一步：看 StockAnalysisAiService 接口（声明式代理，定义 LLM 怎么调）。
// ============================================================================================

/**
 * 结构化分析结果 DTO。
 *
 * <p>这个 record 是 LLM 的「输出契约」：既是 Java 强类型对象，也是 LLM 要遵循的 JSON Schema。
 *
 * <p>LLM 必须返回：{"action":"BUY|SELL|HOLD","symbol":"600519","score":0.0~10.0,"reason":"..."}
 */
public record StockAnalysis(
        String action,   // 操作建议：BUY / SELL / HOLD（枚举约束）
        String symbol,   // 股票代码
        Double score,    // 推荐强度：0.0 ~ 10.0（包装类型 Double，允许 null 以便校验）
        String reason    // 分析理由（自由文本）
) {

    /**
     * 校验 DTO 合法性。LLM 输出视为不可信输入，必须校验后才进入下游。
     *
     * <p>校验规则（4 条全部通过才算合法）：
     *   1. 四个字段都不能为 null
     *   2. action 必须是 BUY / SELL / HOLD 之一（大小写敏感）
     *   3. score 必须在 0.0 ~ 10.0 之间
     *
     * @return 合法返回 true；任一条不满足返回 false
     */
    public boolean isValid() {
        // 规则1：任一字段为 null → 非法（LLM 漏字段）
        if (action == null || symbol == null || score == null || reason == null) {
            return false;
        }
        // 规则2：action 不在枚举里 → 非法（LLM 拼错/小写/多空格）
        if (!action.equals("BUY") && !action.equals("SELL") && !action.equals("HOLD")) {
            return false;
        }
        // 规则3：score 越界 → 非法（LLM 可能返回 999、-1、100）
        if (score < 0.0 || score > 10.0) {
            return false;
        }
        return true;
    }
}
