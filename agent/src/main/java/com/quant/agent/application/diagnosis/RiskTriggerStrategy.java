package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RISK_TRIGGER 归因策略：安全策略拦截（沙盒脚本命中黑名单）。
 *
 * <p>最严重的类别：涉及安全红线。定位命中了哪条危险模式。
 */
public class RiskTriggerStrategy implements DiagnosisStrategy {

    @Override
    public DiagnosisCategory category() {
        return DiagnosisCategory.RISK_TRIGGER;
    }

    @Override
    public List<Hypothesis> hypothesize(Map<String, String> results) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        int[] counter = {0};

        results.forEach((type, result) -> {
            if (result == null) {
                return;
            }
            if (result.contains("[SANDBOX REJECTED]")) {
                counter[0]++;
                hypotheses.add(new Hypothesis(
                        "H" + counter[0],
                        "LLM 生成的脚本命中安全黑名单，被 SandboxPolicy 拦截（禁止执行）",
                        List.of("[" + type + "] " + truncate(result)),
                        Likelihood.HIGH));
            }
        });

        if (hypotheses.isEmpty()) {
            hypotheses.add(new Hypothesis("H1", "检测到风控拦截信号，但证据不足",
                    List.of("RESULTS 含 [SANDBOX REJECTED]"), Likelihood.LOW));
        }
        return hypotheses;
    }

    @Override
    public List<String> recommend(Map<String, String> results) {
        List<String> actions = new ArrayList<>();
        actions.add("⚠ 安全红线：禁止绕过 SandboxPolicy 直接执行被拦截的脚本");
        actions.add("检查 Planner 的 prompt 是否明确约束“脚本不得含危险模式”");
        actions.add("若为误杀（合法因子代码被拦），人工复核后调整 SandboxPolicy 黑名单");
        return actions;
    }

    private String truncate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
