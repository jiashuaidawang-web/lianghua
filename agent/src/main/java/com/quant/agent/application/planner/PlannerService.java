package com.quant.agent.application.planner;

import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

// ============================================================================================
// 【Day 5 · 阅读入口】PlannerService —— 调 LLM 生成计划 + 校验合法性 + 重试。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 的「规划应用服务」，夹在 PlannerAiService 代理和 PlannerNode 之间。
//      PlannerNode → PlannerService → PlannerAiService（代理）→ LLM
//   建议阅读时机：读完 PlannerAiService 接口后读它。
//   学完能回答：
//     1. 为什么不直接暴露 PlannerAiService 给 PlannerNode，非要加这一层？
//     2. 校验逻辑是什么？校验什么？
//     3. 重试策略是什么？和 Day 2 的 StructuredAnalysisService 有什么异同？
//
//   💡 为什么不直接暴露 PlannerAiService？
//     PlannerAiService 是"裸调用"：LLM 返回什么就返回什么，不做校验。
//     但 LLM 可能：
//       - 返回非法 JSON（反序列化抛异常）
//       - 返回空列表（没理解用户意图）
//       - 返回包含非法 TaskType 的列表（幻觉）
//     PlannerService 做"校验 + 重试"的防御。
//
//   💡 校验逻辑是什么？
//     ① 反序列化成功？（框架层保证，失败会抛异常被 catch）
//     ② 列表非空？（空列表 = LLM 没理解，重试）
//     ③ 每个 Task 的 type 是合法枚举？（框架反序列化时已保证）
//     ④ 每个 Task 的 target 非空？（业务规则：没 target 没法执行）
//
//   💡 重试策略：和 Day 2 的 StructuredAnalysisService 一样
//     MAX_RETRY = 2，最多尝试 2 次。
//     失败原因：反序列化失败 / 空列表 / target 为空 → 都走重试。
//     2 次都失败 → 抛明确异常，PlannerNode 捕获后写 ERROR_MESSAGE。
//
//   ⬇ 下一步：看 PlannerNode（图的节点，调这个 service）。
// ============================================================================================

/**
 * 规划应用服务：调 LLM 生成 Task 列表 + 校验合法性 + 重试。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    // 最大尝试次数：LLM 可能不遵守 Schema 或返回空列表，允许有限重试
    private static final int MAX_RETRY = 2;

    // 注入 AiServices 代理接口（实现类是 LangChain4j 运行时生成的）
    private final PlannerAiService plannerAiService;

    public PlannerService(PlannerAiService plannerAiService) {
        this.plannerAiService = plannerAiService;
    }

    /**
     * 规划任务列表。
     *
     * @param userRequest 用户的自然语言请求
     * @return 合法的 Task 列表（非空，每个 Task 都有合法 type 和 target）
     * @throws IllegalStateException 重试耗尽仍无法获得合法结果
     */
    public List<Task> plan(String userRequest) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                log.info("规划请求: userRequest={}, attempt={}/{}", userRequest, attempt, MAX_RETRY);

                // -----------------------------------------------------------------
                // 调用 AiServices 代理（底层调 LLM，同步阻塞拿到完整结果）
                // -----------------------------------------------------------------
                List<Task> tasks = plannerAiService.plan(userRequest);

                // -----------------------------------------------------------------
                // 校验：LLM 输出视为不可信输入
                // -----------------------------------------------------------------
                if (isValidPlan(tasks)) {
                    log.info("规划成功: tasks={}", tasks);
                    return tasks;
                }

                // 非法：打 warn 日志，继续循环
                log.warn("规划结果非法，准备重试: tasks={}, attempt={}/{}", tasks, attempt, MAX_RETRY);

            } catch (Exception e) {
                // 反序列化失败（LLM 返回非法 JSON / 非法 TaskType）会抛异常
                log.warn("规划异常，准备重试: error={}, attempt={}/{}", e.getMessage(), attempt, MAX_RETRY);
            }
        }

        // 循环结束还没 return → 说明 MAX_RETRY 次全失败了 → 快速失败
        throw new IllegalStateException(
                "规划失败：重试 " + MAX_RETRY + " 次后仍无法获得合法结果, userRequest=" + userRequest);
    }

    /**
     * 校验 Task 列表的合法性。
     *
     * <p>校验规则：
     *   1. 列表非 null 非空
     *   2. 每个 Task 的 type 非 null（枚举合法性由反序列化保证）
     *   3. 每个 Task 的 target 非空（业务规则：没 target 没法执行）
     *
     * @param tasks LLM 返回的 Task 列表
     * @return 合法返回 true；任一规则不满足返回 false
     */
    private boolean isValidPlan(List<Task> tasks) {
        // 规则 1：列表非空
        if (tasks == null || tasks.isEmpty()) {
            log.warn("规划结果为空");
            return false;
        }

        // 规则 2 & 3：每个 Task 的 type 和 target 都非 null/非空
        for (Task task : tasks) {
            if (task.type() == null) {
                log.warn("Task type 为 null: {}", task);
                return false;
            }
            if (task.target() == null || task.target().isBlank()) {
                log.warn("Task target 为空: {}", task);
                return false;
            }
        }

        return true;
    }
}
