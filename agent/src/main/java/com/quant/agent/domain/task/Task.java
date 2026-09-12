package com.quant.agent.domain.task;

import java.io.Serializable;
import java.util.Map;

// ============================================================================================
// 【Day 5 · 阅读入口】Task —— 一条「任务命令」，PlannerNode 生成它，ExecutorNode 执行它。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 的核心数据模型。State 里的 TASKS 字段就是 List<Task>。
//   建议阅读时机：读完 TaskType 后立刻读它。
//   学完能回答：
//     1. Task 和 TaskType 是什么关系？
//     2. params 字段是干什么的？为什么用 Map 不用具体字段？
//     3. 为什么 Task 用 record 不用 class？
//     4. 为什么 Task 要实现 Serializable？
//
//   💡 Task 和 TaskType 的关系：
//     TaskType = 枚举，只有 7 种，是「类别」。
//     Task     = 实例，每一条具体的任务，是「命令」。
//
//     类比：
//       TaskType = 菜单上的菜名（"宫保鸡丁"）
//     Task     = 具体的订单（"宫保鸡丁，微辣，不要花生"）
//
//   💡 params 字段是干什么的？
//     params 是「扩展参数」，让同一个 TaskType 能处理不同场景。
//
//     示例：
//       { type: DATA_FETCH, target: "600519", params: { fields: ["price"] } }
//       { type: DATA_FETCH, target: "600519", params: { fields: ["pe", "pb"] } }
//       { type: DATA_FETCH, target: "000001", params: { fields: ["sector"] } }
//
//        ↑ 都是 DATA_FETCH，但通过 params 查不同的数据。
//
//     为什么用 Map 不用具体字段？
//       如果用具体字段：
//         record Task(TaskType type, String target, List<String> fields, String format, ...)
//       问题：不同 TaskType 需要不同参数，加新 TaskType 就要改 Task 的定义。
//       用 Map：扩展参数灵活，加新 TaskType 不用改 Task 定义。
//
//   💡 为什么用 record？
//     Task 是不可变数据（创建后不改），record 天然适合。
//     自动生成：构造器 + getter + equals + hashCode + toString。
//
//   💡 为什么 Task 要实现 Serializable？
//     Task 存在 State 的 TASKS 字段里，而 LangGraph4j 的 StateSerializer 会序列化整个 State
//     （为 Day 7 Checkpoint 断点续传做准备）。
//     如果 Task 不可序列化 → 图执行时抛 NotSerializableException → 运行时才炸。
//     让 Task 实现 Serializable 是编译期可控的防御。
//     ⚠ 注意：params 的 value 也必须是 Serializable 类型（String/Integer/List 等），不能塞 POJO。
//
//   ⬇ 下一步：看 PlannerAiService（声明式代理接口，LLM 返回 List<Task>）。
// ============================================================================================

/**
 * 任务：PlannerNode 生成，ExecutorNode 执行。
 *
 * <p>类比：Command 模式里的 Command 对象 —— 一条待执行的命令。
 *
 * <p>实现 Serializable：因为 Task 存入 State，LangGraph4j 的 StateSerializer 会序列化 State
 * （服务于 Day 7 Checkpoint 断点续传）。
 *
 * @param type   任务类型（必须是 TaskType 枚举之一，不能自由发明）
 * @param target 操作对象（通常是股票代码，如 "600519"）
 * @param params 扩展参数（不同 TaskType 用不同 key，value 必须是 Serializable 类型）
 */
public record Task(
        TaskType type,
        String target,
        Map<String, Object> params
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 便捷构造器：没有 params 的 Task。
     * <p>用法：new Task(TaskType.ANALYSIS, "600519", Map.of())
     *        → 简化为 Task.of(TaskType.ANALYSIS, "600519")
     */
    public static Task of(TaskType type, String target) {
        return new Task(type, target, Map.of());
    }

    /**
     * 便捷构造器：只有一个 param 的 Task。
     * <p>用法：Task.of(TaskType.DATA_FETCH, "600519", "fields", List.of("price"))
     */
    public static Task of(TaskType type, String target, String key, Object value) {
        return new Task(type, target, Map.of(key, value));
    }

    /**
     * 从 params 安全取值。
     * <p>避免 handler 里写冗长的 map.get + 强转。
     *
     * @param key params 的 key
     * @param type 期望的类型
     * @return 值，不存在或类型不匹配返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, Class<T> type) {
        Object value = params.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
