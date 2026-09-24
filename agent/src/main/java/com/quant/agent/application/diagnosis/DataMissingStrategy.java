package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DATA_MISSING 归因策略：数据缺失（源不可用 / 字段空 / 超时）。
 *
 * <p>从 RESULTS 里提取数据获取类任务的结果，定位是"源挂了"还是"字段空"还是"超时"。
 */
public class DataMissingStrategy implements DiagnosisStrategy {

    @Override
    public DiagnosisCategory category() {
        return DiagnosisCategory.DATA_MISSING;
    }

    @Override
    public List<Hypothesis> hypothesize(Map<String, String> results) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        int[] counter = {0};

        results.forEach((type, result) -> {
            if (result == null) {
                return;
            }
            // 超时信号
            if (result.contains("超时") || result.contains("timeout")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "数据源响应超时，可能是网络抖动或源端限流",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.HIGH));
            }
            // 源不可用 / 连接失败
            if (result.contains("连接失败") || result.contains("Connection") || result.contains("refused")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "数据源不可用（连接被拒绝或网络不通）",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.HIGH));
            }
            // 字段空 / 无结果
            if (result.contains("无结果") || result.contains("缺失") || result.contains("为空")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "请求成功但返回数据为空，可能是代码不存在或字段已下线",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.MEDIUM));
            }
            // 通用数据获取失败
            if (result.contains("数据获取失败") || result.contains("获取失败")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "数据获取流程失败（原因见证据）",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.MEDIUM));
            }
        });

        if (hypotheses.isEmpty()) {
            hypotheses.add(new Hypothesis("H1", "检测到数据缺失信号，但证据不足，无法定位具体根因",
                    List.of("RESULTS 含缺失信号"), Likelihood.LOW));
        }
        return hypotheses;
    }

    @Override
    public List<String> recommend(Map<String, String> results) {
        List<String> actions = new ArrayList<>();
        actions.add("确认数据源是否可用（如东财行情接口），必要时切换到备用数据源");
        actions.add("检查目标股票代码是否存在或已退市");
        actions.add("若为临时网络抖动，可重试当前计划");
        return actions;
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
