package com.quant.agent.graph.handlers;

import com.quant.agent.application.llm.StructuredAnalysisService;
import com.quant.agent.domain.task.Task;
import com.quant.agent.domain.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// ============================================================================================
// 【Day 5 · 阅读入口】AnalysisTaskHandler —— 处理 ANALYSIS 类型任务的策略实现。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：TaskHandler 接口的一个实现，负责执行 ANALYSIS 类型的 Task。
//   建议阅读时机：读完 TaskHandler 接口后读它。
//   学完能回答：这个 Handler 内部复用了 Day 2 的什么能力？
//
//   💡 复用了 Day 2 的什么？
//     这个 Handler 内部调的是 StructuredAnalysisService（Day 2 的结构化分析服务）。
//     这就是「纵向演进、复用历史能力」—— Day 5 没有再造分析能力，而是把 Day 2 的
//     StructuredAnalysisService 塞进了一个 Handler 里。
//
//   ⬇ 下一步：看 DataFetchTaskHandler（处理 DATA_FETCH 类型的 Handler）。
// ============================================================================================

/**
 * ANALYSIS 类型任务的 Handler。
 *
 * <p>复用 Day 2 的 StructuredAnalysisService（带校验 + 重试的 LLM 分析）。
 */
@Component  // Spring 自动扫描注入到 List<TaskHandler>
public class AnalysisTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskHandler.class);

    // 注入 Day 2 的结构化分析服务
    private final StructuredAnalysisService analysisService;

    public AnalysisTaskHandler(StructuredAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Override
    public TaskType type() {
        return TaskType.ANALYSIS;  // 我处理 ANALYSIS 类型
    }

    @Override
    public String handle(Task task) {
        String target = task.target();
        log.info("AnalysisTaskHandler 执行: target={}", target);

        try {
            // -----------------------------------------------------------------
            // 复用 Day 2 的结构化分析（含校验 + 重试）
            // -----------------------------------------------------------------
            var analysis = analysisService.analyze(target);

            // 拼装成一段可读的文本写入 RESULTS
            return String.format("操作=%s, 评分=%s, 理由=%s",
                    analysis.action(), analysis.score(), analysis.reason());

        } catch (Exception e) {
            log.warn("AnalysisTaskHandler 失败: target={}, error={}", target, e.getMessage());
            return "分析失败: " + e.getMessage();
        }
    }
}
