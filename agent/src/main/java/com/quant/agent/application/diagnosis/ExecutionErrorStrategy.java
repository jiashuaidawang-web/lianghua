package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EXECUTION_ERROR 归因策略：通用执行失败兜底（未知类型、未执行、其他异常）。
 */
public class ExecutionErrorStrategy implements DiagnosisStrategy {

    @Override
    public DiagnosisCategory category() {
        return DiagnosisCategory.EXECUTION_ERROR;
    }

    @Override
    public List<Hypothesis> hypothesize(Map<String, String> results) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        int[] counter = {0};

        results.forEach((type, result) -> {
            if (result == null) {
                return;
            }
            if (result.contains("未知类型，未执行")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "Planner 生成了系统没有 Handler 的 TaskType，任务被跳过",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.HIGH));
            }
            if (result.contains("分析失败") || result.contains("执行失败") || result.contains("失败")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "任务执行过程中抛出异常",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.MEDIUM));
            }
        });

        if (hypotheses.isEmpty()) {
            hypotheses.add(new Hypothesis("H1", "检测到通用执行失败信号，但证据不足",
                    List.of("RESULTS 含失败信号"), Likelihood.LOW));
        }
        return hypotheses;
    }

    @Override
    public List<String> recommend(Map<String, String> results) {
        List<String> actions = new ArrayList<>();
        actions.add("查看具体任务的错误详情，定位是 Planner 生成错误还是 Handler 执行异常");
        actions.add("若为未知 TaskType，检查 Planner prompt 是否列出完整 TaskType 菜单");
        actions.add("重试当前计划，或人工修正后重新提交");
        return actions;
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
