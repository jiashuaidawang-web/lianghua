package com.quant.agent.domain.state;

// ============================================================================================
// 【Day 4 · 阅读入口】StateKeys —— State 的"字段名注册表"，一份防拼写错误的常量清单。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 4 状态体系的"基石中的基石"。所有节点读写 State 都靠它。
//   建议阅读时机：Day 4 最先读它（1 分钟，但理解它为啥存在要 5 分钟）。
//   学完能回答：为什么不能直接用字符串 "symbol" 而要写 StateKeys.SYMBOL？
//
//   💡 问题背景：QuantAgentState 底层是个 Map<String, Object>
//     Map 的 key 是字符串，但 Java 编译器检查不了字符串拼写：
//       state.put("symbo", "600519");   // 编译通过！运行时才出错（或者不出错，只是读不到）
//       state.get("symbol");            // 返回 null，因为 key 是 "symbo"
//     这种 bug 非常难排查。
//
//   💡 解决方案：把所有 key 集中到 StateKeys 类，变成常量
//       state.put(StateKeys.SYMBOL, "600519");   // 拼错 → 编译报错，IDE 还能自动补全
//       state.get(StateKeys.SYMBOL);             // 万无一失
//
//   💡 为什么是 final class + private 构造器？
//     - final：禁止被继承（常量类不需要子类化）
//     - private 构造器：禁止被实例化（常量类不需要 new StateKeys()）
//     这就是《Effective Java》里的"实用工具类"模式。
//
//   💡 这 6 个 key 分别代表什么？
//     - SYMBOL：用户输入的股票代码（起点）
//     - ANALYSIS_RESULT：LLM 分析结果文本（analysisNode 写入）
//     - TOOL_DATA：工具返回的真实数据（toolNode 写入）
//     - FINAL_RESULT：最终汇总结果（outputNode 写入，终点）
//     - NEEDS_TOOL：是否需要调用工具（analysisNode 写入，条件边据此路由）
//     - ERROR_MESSAGE：错误信息（任一出错时写入）
//
//   ⬇ 下一步：看 QuantAgentState，它继承 AgentState 并提供这些 key 的类型化访问。
// ============================================================================================

/**
 * State 键名常量。
 *
 * <p>集中管理所有 key，避免 Node 间拼写错误（Map 没有编译期检查）。
 */
public final class StateKeys {  // final：禁止继承
    private StateKeys() {}      // private 构造器：禁止实例化

    public static final String SYMBOL = "symbol";
    public static final String ANALYSIS_RESULT = "analysisResult";
    public static final String TOOL_DATA = "toolData";
    public static final String FINAL_RESULT = "finalResult";
    public static final String NEEDS_TOOL = "needsTool";
    public static final String ERROR_MESSAGE = "errorMessage";
}
