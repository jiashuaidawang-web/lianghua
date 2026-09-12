package com.quant.agent.application.planner;

import com.quant.agent.domain.task.Task;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

// ============================================================================================
// 【Day 5 · 阅读入口】PlannerAiService —— 声明式代理接口，LLM 返回 Task 列表。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 的「LLM 调用抽象」，和 Day 2 的 StockAnalysisAiService 是同类。
//   建议阅读时机：读完 Task/TaskType 后读它。
//   学完能回答：
//     1. 为什么返回 List<Task> 而不是 String？
//     2. 这里的 System Prompt 和 Day 2 的有什么不同？
//     3. responseFormat 在这里起什么作用？
//
//   💡 为什么返回 List<Task> 而不是 String？
//     Day 2 的接口返回 StockAnalysis（强类型 DTO），框架自动反序列化 JSON → DTO。
//     这里返回 List<Task>，框架自动反序列化 JSON 数组 → List<Task>。
//
//     如果返回 String → 拿到的是 JSON 字符串，还要自己解析。
//     返回 List<Task> → 框架帮你解析，直接拿到 Java 对象。
//
//   💡 System Prompt 和 Day 2 的区别：
//     Day 2："你是分析师，分析股票" → 让 LLM 做分析
//     Day 5："你是规划师，把用户请求拆成 Task 列表" → 让 LLM 做规划
//
//     关键区别：Day 5 的 prompt 里要列出 7 种 TaskType，告诉 LLM "只能从这里选"。
//
//   💡 responseFormat 在这里的作用：
//     强制 LLM 返回符合 List<Task> Schema 的 JSON。
//     如果 LLM 返回了 TaskType 枚举之外的值 → 反序列化失败 → PlannerService 重试。
//
//   ⬇ 下一步：看 PlannerService（调这个代理 + 校验）。
// ============================================================================================

/**
 * Planner 声明式代理接口。
 *
 * <p>你写接口，LangChain4j 自动实现：组装 SystemMessage + UserMessage → 调 LLM → 反序列化 → 返回 List<Task>。
 */
public interface PlannerAiService {

    // -------------------------------------------------------------------------
    // System Prompt：定义 LLM 是「规划师」，列出 7 种 TaskType 菜单
    // -------------------------------------------------------------------------
    // 这个 prompt 是 Day 5 最关键的「约束」：
    //   1. 角色：规划师（不是分析师）
    //   2. 任务：把用户请求拆成 Task 列表
    //   3. 约束：只能使用列出的 7 种 TaskType，不能发明新的
    //   4. 输出格式：JSON 数组
    String SYSTEM_PROMPT = """
            你是一个任务规划师。

            你的职责：把用户的自然语言请求，拆分成一个 Task 列表。

            你只能使用以下 7 种 Task 类型（不能发明新类型！）：

            1. ANALYSIS   —— 分析股票（调用 LLM 做分析推理）
            2. DATA_FETCH —— 获取数据（调用工具获取价格/基本面等）
            3. FILTER     —— 筛选/过滤（Java 代码做筛选）
            4. ALERT      —— 设置告警（Java 代码设置提醒）
            5. REPORT     —— 生成报告（生成分析总结）
            6. EXECUTE    —— 执行操作（如下单，但需要人工确认）
            7. NOTIFY     —— 通知用户（发送通知）

            输出格式：JSON 数组，每个元素是一个 Task：
            [
              { "type": "ANALYSIS", "target": "股票代码" },
              { "type": "DATA_FETCH", "target": "股票代码", "params": {"fields": ["price", "pe"]} },
              { "type": "REPORT", "params": {"format": "summary"} }
            ]

            规则：
            - type 只能是上面 7 种之一，不能发明新的！
            - target 通常是股票代码（如 "600519"）
            - params 是可选的扩展参数，不同 TaskType 可以用不同 key
            - 根据用户请求合理拆分，通常 2~5 个 Task
            - 考虑 Task 之间的依赖关系，有依赖的排在后面
            """;

    // -------------------------------------------------------------------------
    // 业务方法：给用户的自然语言请求，返回 Task 列表
    // -------------------------------------------------------------------------
    // 返回 List<Task>：告诉 LangChain4j "把 LLM 返回的 JSON 数组反序列化成 List<Task>"。
    // 如果 LLM 返回的 JSON 里 type 不是 7 种枚举之一 → 反序列化失败 → 由 PlannerService 处理。
    @UserMessage(SYSTEM_PROMPT + "\n\n请分析以下用户请求并生成 Task 列表：\n{{userRequest}}")
    List<Task> plan(@V("userRequest") String userRequest);
}
