package com.quant.agent.application.planner;

import com.quant.agent.domain.task.Plan;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ============================================================================================
// 【Day 5 · 阅读入口】PlannerService —— 从自然语言提取股票代码 + 调 LLM 生成计划 + 校验 + 重试。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 的「规划应用服务」，夹在 PlannerAiService 代理和 PlannerNode 之间。
//      PlannerNode → PlannerService → PlannerAiService（代理）→ LLM
//   建议阅读时机：读完 PlannerAiService 接口后读它。
//   学完能回答：
//     1. 为什么不直接暴露 PlannerAiService 给 PlannerNode，非要加这一层？
//     2. 校验逻辑是什么？校验什么？
//     3. 重试策略是什么？和 Day 2 的 StructuredAnalysisService 有什么异同？
//     4. 为什么要用正则先提取股票代码，再交给 LLM？
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
//   💡 为什么要用正则先提取股票代码，再交给 LLM？（重要设计决策）
//     用户输入是自然语言："这个票值不值得买002909"。
//     如果整句丢给 LLM 让它自己提取代码 → 概率性、不可靠（尤其弱模型容易提取失败）。
//     按项目哲学「确定性操作 Java 做，概率性推理 LLM 做」：
//       - 股票代码提取 = 确定性 → Java 正则做（6 位数字 = A 股代码）
//       - 任务规划 = 概率性 → LLM 做
//     提取成功后，把代码拼进请求里给 LLM："用户请求: ...\n股票代码: 002909"
//     → LLM 看到明确的代码，规划准确率大幅提升。
//     如果 LLM 仍没填 target，还会在 fillTargetIfNeeded 里用提取的代码兜底。
//
//   ⬇ 下一步：看 PlannerNode（图的节点，调这个 service）。
// ============================================================================================

/**
 * 规划应用服务：从自然语言提取股票代码 → 调 LLM 生成 Task 列表 → target 兜底 → 校验 → 重试。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    // 最大尝试次数：LLM 可能不遵守 Schema 或返回空列表，允许有限重试
    private static final int MAX_RETRY = 2;

    // A 股代码正则：6 位数字。覆盖：
    //   上海主板 60xxxx、科创板 688xxx、深证主板 00xxxx、中小板 002xxx、创业板 30xxxx、北交所 8xxxxx/9xxxxx
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\b([6-9]\\d{5}|0[03]\\d{4})\\b");

    // 注入 AiServices 代理接口（实现类是 LangChain4j 运行时生成的）
    private final PlannerAiService plannerAiService;

    public PlannerService(PlannerAiService plannerAiService) {
        this.plannerAiService = plannerAiService;
    }

    /**
     * 规划任务列表。
     *
     * <p>流程：
     *   1. 用正则从 userRequest 里确定性提取股票代码
     *   2. 把（原始请求 + 提取到的代码）拼成增强请求给 LLM
     *   3. LLM 返回 Task 列表 → target 兜底 → 校验 → 不合法则重试
     *
     * @param userRequest 用户的自然语言请求（如 "分析茅台" "这个票值不值得买002909"）
     * @return 合法的 Task 列表（非空，每个 Task 都有合法 type 和 target）
     * @throws IllegalStateException 重试耗尽仍无法获得合法结果
     */
    public List<Task> plan(String userRequest) {
        // -----------------------------------------------------------------
        // 步骤 1：确定性提取股票代码（Java 做，不靠 LLM）
        // -----------------------------------------------------------------
        String extractedCode = extractStockCode(userRequest);

        // -----------------------------------------------------------------
        // 步骤 2：构造增强请求 —— 把提取到的代码明确告诉 LLM
        // -----------------------------------------------------------------
        String enhancedRequest = buildEnhancedRequest(userRequest, extractedCode);

        log.info("规划请求: userRequest={}, extractedCode={}", userRequest, extractedCode);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                log.info("规划调用 LLM: attempt={}/{}", attempt, MAX_RETRY);

                // -----------------------------------------------------------------
                // 步骤 3：调 LLM 生成 Task 列表
                // -----------------------------------------------------------------
                // PlannerAiService.plan() 返回 Plan（包装了 List<Task> 的 POJO），
                // 拆开拿到真正的任务列表后，后续逻辑和以前完全一致。
                Plan plan = plannerAiService.plan(enhancedRequest);
                List<Task> tasks = plan.tasks();

                // -----------------------------------------------------------------
                // 步骤 4：target 兜底 —— LLM 没填 target 就用提取的代码补
                // -----------------------------------------------------------------
                tasks = fillTargetIfNeeded(tasks, extractedCode);

                // -----------------------------------------------------------------
                // 步骤 5：校验
                // -----------------------------------------------------------------
                if (isValidPlan(tasks)) {
                    log.info("规划成功: tasks={}", tasks);
                    return tasks;
                }

                log.warn("规划结果非法，准备重试: tasks={}, attempt={}/{}", tasks, attempt, MAX_RETRY);

            } catch (Exception e) {
                // 反序列化失败（LLM 返回非法 JSON / 非法 TaskType）会抛异常
                // 打 full stack trace：e.getMessage() 可能为 null（如某些 LangChain4j 内部异常），
                // 只看 message 会丢失真正的错误原因
                log.warn("规划异常，准备重试: error={}, attempt={}/{}", e, attempt, MAX_RETRY, e);
            }
        }

        // 循环结束还没 return → 说明 MAX_RETRY 次全失败了 → 快速失败
        throw new IllegalStateException(
                "规划失败：重试 " + MAX_RETRY + " 次后仍无法获得合法结果, userRequest=" + userRequest);
    }

    /**
     * 从自然语言里提取 A 股代码（确定性正则）。
     *
     * @return 提取到的 6 位代码；没找到返回 null
     */
    private String extractStockCode(String userRequest) {
        if (userRequest == null) {
            return null;
        }
        Matcher matcher = STOCK_CODE_PATTERN.matcher(userRequest);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 构造增强请求：把提取到的股票代码明确拼进文本，给 LLM 一个确定性锚点。
     *
     * <p>LLM 看到 "股票代码: 002909" 后，target 字段就不容易再填错。
     */
    private String buildEnhancedRequest(String userRequest, String extractedCode) {
        if (extractedCode != null) {
            return userRequest + "\n股票代码: " + extractedCode;
        }
        return userRequest;
    }

    /**
     * 如果 LLM 返回的 Task 没填 target，用 Java 提取的代码兜底填充。
     *
     * <p>这是防御性策略：LLM 可能忘了填 target，但我们已经从正则拿到了代码，
     * 直接补上比直接判「非法 → 重试」更友好。
     */
    private List<Task> fillTargetIfNeeded(List<Task> tasks, String extractedCode) {
        if (extractedCode == null || tasks == null) {
            return tasks;
        }
        List<Task> filled = new ArrayList<>();
        for (Task task : tasks) {
            if (task.target() == null || task.target().isBlank()) {
                // LLM 没填 target → 用正则提取的代码兜底
                log.info("Task target 为空，用提取的代码兜底: type={}, code={}", task.type(), extractedCode);
                filled.add(new Task(task.type(), extractedCode, task.params()));
            } else {
                filled.add(task);
            }
        }
        return filled;
    }

    /**
     * 校验 Task 列表的合法性。
     *
     * <p>校验规则：
     *   1. 列表非 null 非空
     *   2. 每个 Task 的 type 非 null（枚举合法性由反序列化保证）
     *   3. 每个 Task 的 target 非空（业务规则：没 target 没法执行）
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
