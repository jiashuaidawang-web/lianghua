package com.quant.agent.application.audit;

import com.quant.agent.domain.audit.Capability;
import com.quant.agent.domain.audit.GapItem;
import com.quant.agent.domain.audit.GapMatrix;
import com.quant.agent.domain.audit.Severity;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import com.quant.agent.infrastructure.audit.CapabilityInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// ============================================================================================
// 【Day 10 · 阅读入口】DiffAuditService —— 差分审计的"编排中心"，Day 10 的核心应用服务。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 Diff 引擎的编排层。夹在 DiffAuditNode 和 CapabilityInventory 之间。
//   学完能回答：
//     1. Diff 的核心逻辑是什么？
//     2. severity 是怎么判定的？
//     3. 为什么 DiffAuditService 不调 LLM？
//
//   💡 Diff 核心逻辑：
//     输入：List<Task>（Planner 规划的任务）
//     输出：GapMatrix（任务 vs 能力的缺口矩阵）
//
//     步骤：
//       1. scanCapabilities() → 现有能力清单
//       2. 对每个 Task，找对应 Capability：
//          - 找到精确匹配 → 无缺口
//          - 找到近似匹配（同 type 或 tag 重叠）→ LOW/MEDIUM 缺口
//          - 完全找不到 → HIGH/CRITICAL 缺口
//       3. 汇总所有 GapItem → GapMatrix
//
//   💡 severity 怎么判定？
//     由 Java 规则确定性判定（见 determineSeverity）：
//       - 完全没能力 + 是核心数据类（DATA_FETCH）→ CRITICAL
//       - 完全没能力 + 非核心类 → HIGH
//       - 有近似替代 → MEDIUM
//       - 有能力但护栏可能拦截 → LOW
//
//   💡 为什么 DiffAuditService 不调 LLM？
//     Constitution 规定："Risk-sensitive actions require deterministic policy checks"。
//     "有没有能力"是事实判断，不是观点判断——不能靠 LLM 猜。
//     LLM 的"护犊子"问题：让 LLM 审自己规划的任务 → 倾向说"能做"。
//
//   ⬇ 下一步：看 DiffAuditNode（怎么把这个 Service 接入图）。
// ============================================================================================

/**
 * 差分审计服务：对比"任务需求"vs"现有能力"，产出 Gap Matrix。
 *
 * <p>核心职责：
 * <ul>
 *   <li>扫描现有能力（CapabilityInventory）</li>
 *   <li>逐 Task 匹配能力，发现缺口</li>
 *   <li>按 severity 分级，给出推荐动作</li>
 * </ul>
 */
public class DiffAuditService {

    private static final Logger log = LoggerFactory.getLogger(DiffAuditService.class);

    private final CapabilityInventory capabilityInventory;

    public DiffAuditService(CapabilityInventory capabilityInventory) {
        this.capabilityInventory = capabilityInventory;
    }

    /**
     * 执行差分审计。
     *
     * @param tasks Planner 规划的任务列表
     * @return 缺口矩阵（GapMatrix）
     */
    public GapMatrix audit(List<Task> tasks) {
        log.info("DiffAuditService 开始审计: tasks={}", tasks.size());

        // 第 1 步：扫描现有能力
        List<Capability> capabilities = capabilityInventory.scanAll();
        log.info("现有能力: {}", capabilities.size());

        // 第 2 步：逐 Task 匹配，发现缺口
        List<GapItem> gaps = new ArrayList<>();
        int gapCounter = 0;

        for (Task task : tasks) {
            // 找精确匹配：能力名 == Task 需要的工具名
            boolean exactMatch = capabilities.stream()
                    .anyMatch(cap -> matchCapability(cap, task));

            if (exactMatch) {
                continue; // 有精确能力，无缺口
            }

            // 没精确匹配 → 找近似匹配
            boolean fuzzyMatch = capabilities.stream()
                    .anyMatch(cap -> fuzzyMatchCapability(cap, task));

            // 判定 severity
            Severity severity = determineSeverity(task, fuzzyMatch);
            String current = describeCapabilities(capabilities, task.type());
            String required = describeTask(task);
            String evidence = buildEvidence(task, capabilities, exactMatch, fuzzyMatch);
            String recommendedAction = recommendAction(task, capabilities, fuzzyMatch);

            gapCounter++;
            gaps.add(new GapItem(
                    String.format("GAP-%03d", gapCounter),
                    task.type().name(),
                    current,
                    required,
                    severity,
                    evidence,
                    recommendedAction));
        }

        GapMatrix matrix = gaps.isEmpty() ? GapMatrix.EMPTY : new GapMatrix(gaps);
        log.info("审计完成: gaps={}, hasCritical={}", matrix.size(), matrix.hasCritical());

        return matrix;
    }

    /**
     * 精确匹配：能力名与 Task 目标一致，或能力能处理该 TaskType。
     */
    private boolean matchCapability(Capability cap, Task task) {
        // DATA_FETCH 任务：看有没有对应的数据工具
        if (task.type() == TaskType.DATA_FETCH) {
            return cap.type() == Capability.CapabilityType.DATA
                    || cap.tags().contains("market-data")
                    || cap.tags().contains("price")
                    || cap.tags().contains("fundamental");
        }
        // ANALYSIS 任务：看有没有分析能力
        if (task.type() == TaskType.ANALYSIS) {
            return cap.type() == Capability.CapabilityType.ANALYSIS;
        }
        // REPORT 任务：看有没有报告能力
        if (task.type() == TaskType.REPORT) {
            return cap.type() == Capability.CapabilityType.REPORT;
        }
        // EXECUTE 任务：看有没有执行能力（高风险，必须精确）
        if (task.type() == TaskType.EXECUTE) {
            return cap.type() == Capability.CapabilityType.EXECUTE;
        }
        // NOTIFY 任务：看有没有通知能力
        if (task.type() == TaskType.NOTIFY) {
            return cap.type() == Capability.CapabilityType.NOTIFY;
        }
        // SANDBOX 任务：看有没有沙盒执行能力（Day 11）
        if (task.type() == TaskType.SANDBOX) {
            return cap.type() == Capability.CapabilityType.SANDBOX;
        }
        return false;
    }

    /**
     * 模糊匹配：能力与 Task 有部分标签重叠。
     */
    private boolean fuzzyMatchCapability(Capability cap, Task task) {
        // 同类型即模糊匹配
        return cap.type().name().equals(task.type().name());
    }

    /**
     * 判定 severity（确定性规则，不调 LLM）。
     */
    private Severity determineSeverity(Task task, boolean hasFuzzyMatch) {
        // 完全没匹配
        if (!hasFuzzyMatch) {
            // DATA_FETCH 和 EXECUTE 是核心能力，缺失 = CRITICAL
            if (task.type() == TaskType.DATA_FETCH || task.type() == TaskType.EXECUTE) {
                return Severity.CRITICAL;
            }
            return Severity.HIGH;
        }
        // 有模糊匹配（同类型但可能不完全）
        return Severity.MEDIUM;
    }

    /**
     * 描述现有能力（用于 evidence）。
     */
    private String describeCapabilities(List<Capability> capabilities, TaskType type) {
        String available = capabilities.stream()
                .filter(c -> c.type().name().equals(type.name()))
                .map(Capability::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("无对应类型能力");
        return "现有: " + available;
    }

    /**
     * 描述任务需求。
     */
    private String describeTask(Task task) {
        return "需要: " + task.type() + (task.target() != null ? "(" + task.target() + ")" : "");
    }

    /**
     * 构建证据（可追溯）。
     */
    private String buildEvidence(Task task, List<Capability> capabilities,
                                  boolean exactMatch, boolean fuzzyMatch) {
        long matchCount = capabilities.stream()
                .filter(c -> c.type().name().equals(task.type().name()))
                .count();
        if (exactMatch) {
            return "精确匹配";
        }
        if (fuzzyMatch) {
            return "部分匹配（同类型能力 " + matchCount + " 个，但无精确工具）";
        }
        return "无匹配能力（扫描 " + capabilities.size() + " 个能力，无 " + task.type() + " 类型）";
    }

    /**
     * 推荐动作。
     */
    private String recommendAction(Task task, List<Capability> capabilities, boolean fuzzyMatch) {
        if (!fuzzyMatch) {
            return "新增 @" + task.type() + " 类型工具，或通过 MCP 连接远端服务";
        }
        return "用现有 " + task.type() + " 能力近似替代";
    }
}
