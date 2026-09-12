package com.quant.agent.graph.handlers;

import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// ============================================================================================
// 【Day 5 · 阅读入口】ReportTaskHandler —— 处理 REPORT 类型任务的策略实现。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：TaskHandler 接口的一个实现，负责执行 REPORT 类型的 Task。
//   建议阅读时机：读完 DataFetchTaskHandler 后读它。
//   学完能回答：这个 Handler 为什么是「纯 Java 模板拼接」而不是调 LLM？
//
//   💡 为什么是纯 Java 模板拼接而不是调 LLM？
//     REPORT 的职责是「汇总已有的分析结果和数据」，生成一份可读的报告。
//     这个操作是确定性的：输入确定 → 输出确定，不需要 LLM 推理。
//
//     如果用 LLM：
//       - 慢（每次调 LLM 多 1~3 秒）
//       - 费钱（几百 token）
//       - 不可预测（LLM 可能每次格式不一样）
//
//     用模板拼接：
//       - 快（纯内存操作）
//       - 免费
//       - 格式固定、可测试
//
//     这是 Day 5 的设计哲学：「确定性操作 Java 做，概率性推理 LLM 做」。
//
//   ⚠ 注意：这个 Handler 是个简化版，实际生产可能需要更复杂的模板引擎（如 FreeMarker/Thymeleaf）。
//
//   ⬇ 下一步：看 ExecutorNode（组装所有 Handler，循环执行 Task）。
// ============================================================================================

/**
 * REPORT 类型任务的 Handler。
 *
 * <p>纯 Java 模板拼接，不调 LLM。把 State 里的分析结果 + 数据汇总成报告。
 */
@Component
public class ReportTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportTaskHandler.class);

    @Override
    public TaskType type() {
        return TaskType.REPORT;  // 我处理 REPORT 类型
    }

    @Override
    public String handle(Task task) {
        log.info("ReportTaskHandler 执行");

        // -----------------------------------------------------------------
        // 简化版：返回一个模板文本
        // -----------------------------------------------------------------
        // 实际生产应该：
        //   1. 从 params 取 format 参数（summary/detailed/...）
        //   2. 根据 format 选不同模板
        //   3. 从 State 取分析结果 + 数据填入模板
        //   4. 返回完整报告

        return String.format("""
                ====== 分析报告 ======
                目标: %s
                [此处为汇总的分析结果和数据]
                ======================
                """, task.target());
    }
}
