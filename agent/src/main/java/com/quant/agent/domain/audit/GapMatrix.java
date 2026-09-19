package com.quant.agent.domain.audit;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// ============================================================================================
// 【Day 10 · 阅读入口】GapMatrix —— "缺口矩阵"，DiffAuditNode 的核心产出。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 10 差分审计的"最终报告"。写入 QuantAgentState，
//   由 RenderNode/OutputNode 渲染成人类可读的审计报告。
//   学完能回答：
//     1. GapMatrix 和 List<GapItem> 有什么区别？为什么要包一层？
//     2. hasCritical() 为什么重要？
//     3. 为什么 GapMatrix 要实现 Serializable？
//
//   💡 为什么要包一层而不是直接用 List<GapItem>？
//     包一层能附加"汇总方法"：
//       - hasCritical()：有没有阻断性缺口
//       - criticalCount()：阻断性缺口数量
//       - summary()：人类可读的审计摘要
//     如果只传 List，这些逻辑会散落在 Node/Service 里，不好复用。
//
//   💡 hasCritical() 为什么重要？
//     DiffAuditNode 的路由依据：
//       hasCritical() = true  → auditPassed = false → 阻断执行
//       hasCritical() = false → auditPassed = true  → 继续执行
//
//   💡 为什么实现 Serializable？
//     GapMatrix 存入 State（GAP_MATRIX 字段），LangGraph4j 的 StateSerializer 会序列化整个 State
//     （为 Day 7 Checkpoint 断点续传做准备）。
//     如果 GapMatrix 不可序列化 → 图执行时抛 NotSerializableException → 运行时才炸。
//     这是编译期可控的防御（和 Day 5 的 Task 实现 Serializable 同理）。
//
//   ⬇ 下一步：看 DiffAuditService（怎么产出这个 GapMatrix）。
// ============================================================================================

/**
 * 缺口矩阵：DiffAuditNode 的核心产出。
 *
 * <p>包含所有 GapItem + 汇总方法。实现 Serializable 以支持 Day 7 Checkpoint 序列化。
 */
public class GapMatrix implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<GapItem> gaps;

    /** 空矩阵（无缺口）。 */
    public static final GapMatrix EMPTY = new GapMatrix(Collections.emptyList());

    public GapMatrix(List<GapItem> gaps) {
        // 防御性拷贝 + 不可修改：保证不可变性
        this.gaps = List.copyOf(gaps);
    }

    public List<GapItem> gaps() {
        return gaps;
    }

    public boolean isEmpty() {
        return gaps.isEmpty();
    }

    public int size() {
        return gaps.size();
    }

    /**
     * 是否有阻断性缺口（CRITICAL）。
     * <p>DiffAuditNode 据此决定 auditPassed。
     */
    public boolean hasCritical() {
        return gaps.stream().anyMatch(GapItem::isCritical);
    }

    public long criticalCount() {
        return gaps.stream().filter(GapItem::isCritical).count();
    }

    public long highCount() {
        return gaps.stream().filter(g -> g.severity() == Severity.HIGH).count();
    }

    /**
     * 获取第一个阻断性缺口（用于错误信息/推荐动作）。
     */
    public Optional<GapItem> firstCritical() {
        return gaps.stream().filter(GapItem::isCritical).findFirst();
    }

    /**
     * 人类可读的审计摘要（给 RenderNode 用）。
     */
    public String summary() {
        if (gaps.isEmpty()) {
            return "审计通过：所有任务都有对应能力。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("审计发现 ").append(gaps.size()).append(" 个缺口：");
        if (hasCritical()) {
            sb.append("（").append(criticalCount()).append(" 个 CRITICAL，执行阻断)");
        }
        gaps.forEach(g -> sb.append("\n  - [").append(g.severity()).append("] ")
                .append(g.required()).append(" → 建议：").append(g.recommendedAction()));
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GapMatrix{gaps=" + gaps.size() + ", critical=" + criticalCount() + "}";
    }
}
