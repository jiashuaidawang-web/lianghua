package com.quant.agent.graph.handlers;

import com.quant.agent.application.sandbox.SandboxService;
import com.quant.agent.domain.sandbox.SandboxLimits;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 11 · 阅读入口】SandboxTaskHandler —— Day 11 新增 Handler，图的"沙盒工人"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 5 ExecutorNode 注册表的新成员。处理 SANDBOX 类型的 Task。
//   建议阅读时机：读完 SandboxService 后读它。
//   学完能回答：
//     1. 这个 Handler 怎么读 Task 里的脚本/语言/配额？
//     2. 为什么配额从 Task.params 解析，而不是写死？
//     3. 为什么异常要 catch 而不是上抛？
//
//   💡 怎么读 Task 里的脚本？
//     Task.params 是 Map<String, Object>，约定 key：
//       "script"    → 脚本内容（必填）
//       "language"  → 语言（可选，默认 "python"）
//       "inputData" → 输入数据（可选，Map）
//       "limits"    → 配额覆盖（可选，Map → SandboxLimits）
//     这样 Planner 生成的 SANDBOX 任务就能携带完整执行参数。
//
//   💡 为什么配额从 params 解析？
//     不同任务的资源需求不同：
//       - 简单因子计算 → 小配额（128MB）
//       - 复杂数据处理 → 大配额（512MB）
//     写死一个配额 → 要么浪费要么不够。
//     从 params 解析 → Planner 可按需指定，缺省回退 SandboxLimits.DEFAULT。
//
//   💡 为什么 catch 异常？
//     ExecutorNode 的循环里，一个 Task 失败不应阻塞后续 Task。
//     catch 后返回错误文本 → 写入 RESULTS → 继续下一个 Task。
//     这和 DataFetchTaskHandler / ReportTaskHandler 的风格一致。
//
//   ⬇ 到这里，Day 11 的工程代码完成。下一步：看测试。
// ============================================================================================

/**
 * SANDBOX 类型任务的 Handler。
 *
 * <p>复用 Day 11 的 SandboxService，把 LLM 生成的脚本安全地丢进隔离容器执行。
 */
@Component
public class SandboxTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SandboxTaskHandler.class);

    private final SandboxService sandboxService;

    public SandboxTaskHandler(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @Override
    public TaskType type() {
        return TaskType.SANDBOX;
    }

    @Override
    public String handle(Task task) {
        log.info("SandboxTaskHandler 执行: target={}", task.target());

        try {
            // 从 params 读执行参数
            String script = task.getParam("script", String.class);
            if (script == null || script.isBlank()) {
                return "[SANDBOX ERROR] 缺少 script 参数";
            }
            String language = task.getParam("language", String.class);
            if (language == null) {
                language = "python";
            }
            Map<String, Object> inputData = task.getParam("inputData", Map.class);
            if (inputData == null) {
                inputData = Map.of();
            }
            SandboxLimits limits = parseLimits(task.getParam("limits", Map.class));

            return sandboxService.run(script, language, inputData, limits);
        } catch (Exception e) {
            log.warn("SandboxTaskHandler 异常: {}", e.getMessage(), e);
            return "[SANDBOX ERROR] 执行异常: " + e.getMessage();
        }
    }

    /**
     * 从 params 的 limits Map 解析 SandboxLimits。
     *
     * <p>任何字段缺失/非法 → 回退 {@link SandboxLimits#DEFAULT}，不阻塞执行。
     */
    @SuppressWarnings("unchecked")
    private SandboxLimits parseLimits(Object limitsObj) {
        if (!(limitsObj instanceof Map)) {
            return null; // null → SandboxService 用 DEFAULT
        }
        try {
            Map<String, Object> map = (Map<String, Object>) limitsObj;
            long timeoutMs = toLong(map.get("timeoutMs"), SandboxLimits.DEFAULT.timeoutMs());
            int memoryMb = toInt(map.get("memoryMb"), SandboxLimits.DEFAULT.memoryMb());
            double cpu = toDouble(map.get("cpu"), SandboxLimits.DEFAULT.cpu());
            int tmpMb = toInt(map.get("tmpMb"), SandboxLimits.DEFAULT.tmpMb());
            int pidsLimit = toInt(map.get("pidsLimit"), SandboxLimits.DEFAULT.pidsLimit());
            boolean networkAllowed = toBoolean(map.get("networkAllowed"), SandboxLimits.DEFAULT.networkAllowed());
            int maxOutputBytes = toInt(map.get("maxOutputBytes"), SandboxLimits.DEFAULT.maxOutputBytes());
            return SandboxLimits.of(timeoutMs, memoryMb, cpu, tmpMb, pidsLimit, networkAllowed, maxOutputBytes);
        } catch (Exception e) {
            log.warn("解析沙盒配额失败，使用默认值: {}", e.getMessage());
            return null;
        }
    }

    private long toLong(Object value, long defaultVal) {
        if (value instanceof Number) return ((Number) value).longValue();
        return defaultVal;
    }

    private int toInt(Object value, int defaultVal) {
        if (value instanceof Number) return ((Number) value).intValue();
        return defaultVal;
    }

    private double toDouble(Object value, double defaultVal) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return defaultVal;
    }

    private boolean toBoolean(Object value, boolean defaultVal) {
        if (value instanceof Boolean) return (Boolean) value;
        return defaultVal;
    }

    // 抑制未使用警告（Task 接口的 target/params 可能暂时不用）
    @SuppressWarnings("unused")
    private void touch(Task task) {
        List<TaskType> types = List.of(task.type());
    }
}
