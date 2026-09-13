package com.quant.agent.domain.task;

import java.io.Serializable;
import java.util.List;

// ============================================================================================
// 【Day 5 · 修复】Plan —— List<Task> 的包装 POJO。
// --------------------------------------------------------------------------------------------
//   为什么需要这个包装？
//     LangChain4j 1.20.0 的 AiService 方法如果直接返回 List<POJO>，
//     框架会用 PojoCollectionOutputParser 去生成"输出格式指令"，
//     但该方法在当前版本是一个直接抛 IllegalStateException 的空桩（未实现）。
//     → 异常在组装 prompt 阶段就抛出，LLM 请求根本不会发出。
//
//     把 List<Task> 包进一个单 POJO，框架就改用正常工作的 PojoOutputParser。
//     （Day 2 的 StockAnalysis 单 POJO 能用，走的就是这条路径。）
//
//   教训：第三方框架的"声明式代理"并不是魔法，返回类型会决定框架走哪条解析路径，
//   遇到版本 bug 时，"加一层包装"往往比升级版本更安全、改动更小。
//
//   实现 Serializable：和 Task 一样，Plan 会作为 State 的一部分被序列化。
// ============================================================================================

/**
 * Planner 的输出：Task 列表的包装。
 *
 * @param tasks 规划出的任务列表
 */
public record Plan(List<Task> tasks) implements Serializable {
    private static final long serialVersionUID = 1L;
}
