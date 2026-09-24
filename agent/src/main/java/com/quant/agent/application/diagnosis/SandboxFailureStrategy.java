package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SANDBOX_FAILURE 归因策略：沙盒执行失败（超时 / 错误退出）。
 *
 * <p>从 SANDBOX 类任务的结果里提取 stdout/stderr 证据，区分"资源不足"和"脚本自身错误"。
 */
public class SandboxFailureStrategy implements DiagnosisStrategy {

    @Override
    public DiagnosisCategory category() {
        return DiagnosisCategory.SANDBOX_FAILURE;
    }

    @Override
    public List<Hypothesis> hypothesize(Map<String, String> results) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        int[] counter = {0};

        results.forEach((type, result) -> {
            if (result == null) {
                return;
            }
            // 超时
            if (result.contains("[SANDBOX TIMEOUT]")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "沙盒执行超时，可能是脚本死循环或计算量超出配额",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.HIGH));
            }
            // 错误退出
            if (result.contains("[SANDBOX ERROR]")) {
                counter[0]++;
                List<String> evidence = new ArrayList<>();
                evidence.add("[" + type + "] " + truncate(result));
                // 细分：语言不支持 vs 运行时错误
                if (result.contains("不支持的语言")) {
                    hypotheses.add(new Hypothesis(
                            "H" + counter[0],
                            "使用了沙盒不支持的语言（本期仅支持 python）",
                            evidence, Likelihood.HIGH));
                } else if (result.contains("缺少 script 参数")) {
                    hypotheses.add(new Hypothesis(
                            "H" + counter[0],
                            "SANDBOX 任务缺少 script 参数，Planner 未正确生成",
                            evidence, Likelihood.HIGH));
                } else {
                    hypotheses.add(new Hypothesis(
                            "H" + counter[0],
                            "沙盒执行出错，可能是脚本运行时异常或基础设施故障",
                            evidence, Likelihood.MEDIUM));
                }
            }
        });

        if (hypotheses.isEmpty()) {
            hypotheses.add(new Hypothesis("H1", "检测到沙盒失败信号，但证据不足，无法定位具体根因",
                    List.of("RESULTS 含 [SANDBOX ERROR/TIMEOUT]"), Likelihood.LOW));
        }
        return hypotheses;
    }

    @Override
    public List<String> recommend(Map<String, String> results) {
        List<String> actions = new ArrayList<>();
        actions.add("检查脚本是否有死循环或超大计算，必要时调大 limits.timeoutMs / memoryMb");
        actions.add("查看沙盒 stderr 定位运行时异常，修正脚本逻辑");
        actions.add("确认 Docker 守护进程与镜像是否正常（基础设施问题报运维）");
        return actions;
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
