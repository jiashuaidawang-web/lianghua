package com.quant.agent.graph.nodes;

import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.graph.handlers.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ============================================================================================
// 【Day 5 · 阅读入口】ExecutorNode —— Day 5 新增节点 2/3，图的第二个业务节点。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：图的第二个节点（plannerNode → executorNode）。
//   建议阅读时机：读完 PlannerNode 和 TaskHandler 接口后读它。
//   学完能回答：
//     1. ExecutorNode 怎么知道该用哪个 Handler？
//     2. 为什么用 Map 注册表而不是 switch-case？
//     3. 如果 Task 的 type 没有对应 Handler 怎么办？
//
//   💡 ExecutorNode 怎么知道该用哪个 Handler？
//     构造时注入 List<TaskHandler>（Spring 自动把所有 Handler 实现类注入）。
//     然后转成 Map<TaskType, TaskHandler>：
//       ANALYSIS   → AnalysisTaskHandler
//       DATA_FETCH → DataFetchTaskHandler
//       REPORT     → ReportTaskHandler
//
//     执行时：map.get(task.type()) → 找到对应 Handler → 调 handle(task)
//
//   💡 为什么用 Map 注册表而不是 switch-case？
//     switch-case：加新 TaskType → 必须改 switch → 违反开闭原则
//     Map 注册表：加新 TaskType → 新建 Handler 类 → Spring 自动注入 → Map 自动注册
//     → 不用改 ExecutorNode → 符合开闭原则
//
//   💡 如果 Task 的 type 没有对应 Handler 怎么办？
//     map.get(type) 返回 null → 记录警告 → 跳过该 Task → 继续执行下一个。
//     这保证了「某个 Task 失败不会阻塞整个执行」。
//
//   ⬇ 下一步：看 ReviewNode（审查 ExecutorNode 的执行结果）。
// ============================================================================================

/**
 * 执行节点：读 TASKS 列表，按 type 分发到对应 Handler 执行。
 *
 * <p>使用策略模式 + Map 注册表，符合开闭原则。
 */
public class ExecutorNode {

    private static final Logger log = LoggerFactory.getLogger(ExecutorNode.class);

    // -------------------------------------------------------------------------
    // Handler 注册表：TaskType → TaskHandler
    // -------------------------------------------------------------------------
    // 构造时由 List<TaskHandler> 转换而来。
    // Spring 自动注入所有 TaskHandler 实现类（AnalysisTaskHandler / DataFetchTaskHandler / ReportTaskHandler）。
    private final Map<TaskType, TaskHandler> handlerMap;

    /**
     * 构造器：注入所有 TaskHandler 实现，转成 Map 注册表。
     *
     * 这个方法妙就妙在用参数类型的集合注入,spring初始化这个类的时候,会先去找TaskHandler所有的实现类,发现所有的handlers后注入到集合中
     *
     * @param handlers Spring 自动注入的 List<TaskHandler>
     *
     */
    public ExecutorNode(List<TaskHandler> handlers) {
        // 把 List 转成 Map：TaskType → TaskHandler
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::type, h -> h));

        log.info("ExecutorNode 初始化: 注册了 {} 个 Handler: {}", handlerMap.size(), handlerMap.keySet());
    }

    /**
     * 执行节点逻辑。
     *
     * @param state 当前状态（不可变，只读）
     * @return 增量更新 Map（框架 merge 到 State）
     */
    public Map<String, Object> apply(QuantAgentState state) {
        List<Task> tasks = state.tasks();
        log.info("executorNode 执行: tasks={}", tasks);

        Map<String, Object> updates = new HashMap<>();
        Map<String, String> results = new HashMap<>();

        // -------------------------------------------------------------------------
        // 循环执行每个 Task
        // -------------------------------------------------------------------------
        for (Task task : tasks) {
            TaskType type = task.type();

            // 从注册表找对应的 Handler
            TaskHandler handler = handlerMap.get(type);

            if (handler != null) {
                // 找到 Handler → 执行
                log.info("执行 Task: type={}, target={}", type, task.target());
                try {
                    String result = handler.handle(task);
                    results.put(type.name(), result);  // key = TaskType 名（如 "ANALYSIS"）
                } catch (Exception e) {
                    log.warn("Task 执行失败: type={}, error={}", type, e.getMessage());
                    results.put(type.name(), "执行失败: " + e.getMessage());
                }
            } else {
                // 没找到 Handler → 跳过（不应该发生，因为 PlannerNode 只能生成 7 种 type）
                log.warn("未知 Task 类型，跳过: type={}", type);
                results.put(type.name(), "未知类型，未执行");
            }
        }

        // 写 RESULTS：所有 Task 的执行结果
        updates.put(StateKeys.RESULTS, results);
        return updates;
    }
}
