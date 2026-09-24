package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HEALTHY 策略：无异常。产出空假说 + 空推荐，由 Service 组装为 {@link com.quant.agent.domain.diagnosis.Diagnosis#HEALTHY}。
 */
public class HealthyStrategy implements DiagnosisStrategy {

    @Override
    public DiagnosisCategory category() {
        return DiagnosisCategory.HEALTHY;
    }

    @Override
    public List<Hypothesis> hypothesize(Map<String, String> results) {
        return Collections.emptyList();
    }

    @Override
    public List<String> recommend(Map<String, String> results) {
        return Collections.emptyList();
    }
}
