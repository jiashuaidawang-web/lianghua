package com.quant.agent.graph.handlers;

import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;

// ============================================================================================
// 【Day 5 · 阅读入口】TaskHandler —— 策略模式接口，每种 TaskType 一个实现类。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 ExecutorNode 的「执行策略」接口。
//   建议阅读时机：Day 5 理解 ExecutorNode 的执行机制时读它。
//   学完能回答：
//     1. 为什么用策略模式而不是 switch-case？
//     2. 这个接口怎么实现「开闭原则」？
//     3. Spring 怎么知道该用哪个 Handler？
//
//   💡 为什么用策略模式而不是 switch-case？
//     switch-case 写法：
//       switch (task.type()) {
//           case ANALYSIS -> handleAnalysis(task);
//           case DATA_FETCH -> handleDataFetch(task);
//           // 加新 TaskType → 必须改这个 switch ← 违反开闭原则
//       }
//
//     策略模式写法：
//       每种 TaskType 一个 Handler 类（AnalysisTaskHandler / DataFetchTaskHandler / ...）
//       ExecutorNode 用 Map<TaskType, TaskHandler> 注册表
//       加新 TaskType → 新建一个 Handler 类 → Spring 自动注入 → Map 自动注册
//       → 不用改 ExecutorNode ← 符合开闭原则
//
//   💡 怎么实现「开闭原则」？
//     对扩展开放：加新 TaskType → 新建 Handler 类（扩展）
//     对修改关闭：ExecutorNode 不用改（不修改已有代码）
//
//   💡 Spring 怎么知道该用哪个 Handler？
//      每个 Handler 实现类加 @Component → Spring 自动扫描并注入到 List<TaskHandler>
//      ExecutorNode 构造时注入 List<TaskHandler> → 转成 Map<TaskType, TaskHandler>
//      执行时：map.get(task.type()) → 找到对应的 Handler → 调 handle()
//
//   ⬇ 下一步：看具体的 Handler 实现（AnalysisTaskHandler / DataFetchTaskHandler / ReportTaskHandler）。
// ============================================================================================

/**
 * 任务执行策略接口。
 *
 * <p>每种 TaskType 一个实现类，Spring 自动注入到 ExecutorNode。
 *
 * <p>类比：策略模式里的 Strategy 接口 —— 定义「怎么做」的契约。
 */
public interface TaskHandler {

    /**
     * 这个 Handler 处理哪种 TaskType。
     *
     * <p>用于 ExecutorNode 构建 Map<TaskType, TaskHandler> 注册表。
     *
     * @return 对应的 TaskType 枚举值
     */
    TaskType type();

    /**
     * 执行任务。
     *
     * @param task 要执行的任务
     * @return 执行结果文本（写入 State 的 RESULTS）
     */
    String handle(Task task);
}
