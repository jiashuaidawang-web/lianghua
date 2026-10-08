package com.quant.agent.application.diagnosis;

import com.quant.agent.domain.diagnosis.Diagnosis;
import com.quant.agent.domain.diagnosis.DiagnosisCategory;
import com.quant.agent.domain.diagnosis.Hypothesis;
import com.quant.agent.domain.diagnosis.Likelihood;
import com.quant.agent.domain.state.QuantAgentState;
import com.quant.agent.domain.state.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ============================================================================================
// 【Day 12 · 阅读入口】DiagnosisService —— 诊断的"决策树 + Strategy 编排中心"。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 12 的核心应用服务。夹在 DiagnosisNode 和 5 个 Strategy 之间。
//     DiagnosisNode → DiagnosisService → [决策树选类] → Strategy → Diagnosis
//   建议阅读时机：读完 Diagnosis + DiagnosisStrategy 后读它。
//   学完能回答：
//     1. 决策树是怎么分类的？为什么按这个顺序？
//     2. 为什么分类结果不直接给答案，还要走 Strategy？
//     3. 为什么这个服务不调 LLM？
//
//   💡 决策树怎么分类？
//     扫描 RESULTS 里所有结果文本，按严重度降序匹配信号，命中即停：
//       REJECTED → RISK_TRIGGER
//       TIMEOUT  → SANDBOX_FAILURE
//       ERROR    → SANDBOX_FAILURE
//       数据缺失  → DATA_MISSING
//       通用失败  → EXECUTION_ERROR
//       全空      → DATA_MISSING
//       全无信号  → HEALTHY
//
//     命中即停保证"最严重的那个问题"被优先呈现（安全 > 环境 > 数据 > 通用）。
//
//   💡 为什么分类后还要走 Strategy？
//     分类只回答"是哪类问题"；Strategy 回答"具体哪个根因 + 怎么做"。
//     两者职责分离：决策树管"定性"（哪类），Strategy 管"定量"（具体假说+动作）。
//     这样加新类别只需加一个 Strategy，不用改决策树。
//
//   💡 为什么不调 LLM？
//     归因必须是确定性的、可审计的（constitution: deterministic policy checks）。
//     让 LLM 自由解释失败原因 → 幻觉风险。全部用 Java 规则，行为可预测、可单测。
//
//   ⬇ 下一步：看 DiagnosisNode（怎么把这个 Service 接入图）。
// ============================================================================================

/**
 * 诊断应用服务：决策树分类 + Strategy 编排。
 *
 * <p>扫描 RESULTS 证据，确定性分类归因类别，委托对应 Strategy 产出假说 + 推荐动作，
 * 组装成 {@link Diagnosis}。
 */
@Service
public class DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

    /** Strategy 注册表：DiagnosisCategory → DiagnosisStrategy */
    private final Map<DiagnosisCategory, DiagnosisStrategy> strategyMap;

    /** LLM 深度归因兜底（确定性分析 LOW 可信度时启用；可为 null 表示禁用） */
    private final LlmDeepAnalysisService llmDeepAnalysisService;

    public DiagnosisService(List<DiagnosisStrategy> strategies) {
        this(strategies, null);
    }

    /**
     * Day 12 分层混合构造器：注入 Strategy 列表 + LLM 兜底服务。
     *
     * @param strategies           确定性归因策略列表（5 个）
     * @param llmDeepAnalysisService LLM 深度归因兜底（null 则禁用 LLM 兜底）
     */
    public DiagnosisService(List<DiagnosisStrategy> strategies,
                            LlmDeepAnalysisService llmDeepAnalysisService) {
        this.strategyMap = new java.util.HashMap<>();
        for (DiagnosisStrategy strategy : strategies) {
            strategyMap.put(strategy.category(), strategy);
        }
        this.llmDeepAnalysisService = llmDeepAnalysisService;
        log.info("DiagnosisService 初始化: 注册了 {} 个 Strategy, LLM 兜底: {}",
                strategyMap.size(), llmDeepAnalysisService != null ? "已启用" : "未启用");
    }

    /**
     * 对当前 State 执行诊断。
     *
     * <p>流程：
     * <ol>
     *   <li>从 State 取 RESULTS 作为证据</li>
     *   <li>决策树分类（按严重度降序命中即停）</li>
     *   <li>选 Strategy → 产出假说 + 推荐动作</li>
     *   <li>组装 Diagnosis</li>
     * </ol>
     *
     * @param state 当前状态（含 RESULTS）
     * @return 诊断证据模型（HEALTHY 表示无异常）
     */
    public Diagnosis diagnose(QuantAgentState state) {
        Map<String, String> results = state.results();
        log.info("DiagnosisService 开始诊断: taskCount={}", results.size());

        // 第 1 步：决策树分类
        DiagnosisCategory category = classify(results);
        log.info("诊断分类: category={}", category);

        // 第 2 步：HEALTHY 直接返回，无需走 Strategy
        if (category == DiagnosisCategory.HEALTHY) {
            return Diagnosis.HEALTHY;
        }

        // 第 3 步：选 Strategy
        DiagnosisStrategy strategy = strategyMap.get(category);
        if (strategy == null) {
            // 防御：没有对应 Strategy 时降级为通用诊断
            log.warn("无对应 Strategy，降级处理: category={}", category);
            return new Diagnosis(category, "检测到异常，但无对应归因策略",
                    List.of(new Hypothesis("H1", "异常类型: " + category, List.of("RESULTS 非空"), Likelihood.LOW)),
                    List.of("人工复核 RESULTS 定位问题"),
                    Likelihood.LOW);
        }

        // 第 4 步：Strategy 产出假说 + 推荐动作
        List<Hypothesis> hypotheses = strategy.hypothesize(results);
        List<String> recommendedActions = strategy.recommend(results);

        // -------------------------------------------------------------------------
        // Day 12 分层混合：确定性分析 LOW 可信度时，调 LLM 做深度归因补充
        // -------------------------------------------------------------------------
        // 为什么在这里升级：
        //   - 决策树 + Strategy 已经做了"能做的"（确定性、零成本）
        //   - 但证据太模糊 → 假说可信度 LOW → 吃不准
        //   - 这时才请 LLM 分析证据、补充假说（发挥 LLM 推理优势）
        //   - LLM 结果只追加，不替换 → 双保险
        if (topLikelihood(hypotheses) == Likelihood.LOW && llmDeepAnalysisService != null) {
            log.info("确定性分析可信度 LOW，启动 LLM 深度归因兜底");
            List<Hypothesis> llmHypotheses = llmDeepAnalysisService.deepAnalyze(results, category);
            if (!llmHypotheses.isEmpty()) {
                // 合并：确定性假说在前，LLM 补充在后
                List<Hypothesis> merged = new ArrayList<>(hypotheses);
                merged.addAll(llmHypotheses);
                hypotheses = merged;
                log.info("LLM 兜底补充了 {} 个假说，总假说数: {}", llmHypotheses.size(), hypotheses.size());
            }
        }

        // 第 5 步：组装 Diagnosis
        Diagnosis diagnosis = new Diagnosis(
                category,
                buildSummary(category, hypotheses),
                hypotheses,
                recommendedActions,
                topLikelihood(hypotheses));

        log.info("诊断完成: category={}, hypotheses={}, actions={}",
                diagnosis.category(), diagnosis.hypotheses().size(), diagnosis.recommendedActions().size());
        return diagnosis;
    }

    /**
     * 决策树分类：按严重度降序扫描信号，命中即停。
     *
     * <p>优先级：RISK_TRIGGER > SANDBOX_FAILURE > DATA_MISSING > EXECUTION_ERROR > HEALTHY。
     */
    private DiagnosisCategory classify(Map<String, String> results) {
        if (results == null || results.isEmpty()) {
            return DiagnosisCategory.DATA_MISSING; // 全空 = 没有任何数据产出
        }

        // 收集所有结果文本（一次性遍历，按优先级判断）
        boolean hasReject = false;
        boolean hasSandboxTimeout = false;
        boolean hasSandboxError = false;
        boolean hasDataMissing = false;
        boolean hasGenericFailure = false;

        for (String result : results.values()) {
            if (result == null) {
                continue;
            }
            // 风控拦截（最严重，一旦发现直接返回）
            if (result.contains("[SANDBOX REJECTED]")) {
                hasReject = true;
            }
            if (result.contains("[SANDBOX TIMEOUT]")) {
                hasSandboxTimeout = true;
            }
            if (result.contains("[SANDBOX ERROR]")) {
                hasSandboxError = true;
            }
            if (result.contains("数据获取失败") || result.contains("获取失败")
                    || result.contains("无结果") || result.contains("缺失")
                    || result.contains("网络超时") || result.contains("不支持的语言")
                    || result.contains("超时")) {
                hasDataMissing = true;
            }
            if (result.contains("失败") || result.contains("未知类型，未执行")) {
                hasGenericFailure = true;
            }
        }

        // 按严重度降序命中即停
        if (hasReject) {
            return DiagnosisCategory.RISK_TRIGGER;
        }
        if (hasSandboxTimeout || hasSandboxError) {
            return DiagnosisCategory.SANDBOX_FAILURE;
        }
        if (hasDataMissing) {
            return DiagnosisCategory.DATA_MISSING;
        }
        if (hasGenericFailure) {
            return DiagnosisCategory.EXECUTION_ERROR;
        }
        return DiagnosisCategory.HEALTHY;
    }

    /**
     * 取最高假说可信度作为整体可信度。
     */
    private Likelihood topLikelihood(List<Hypothesis> hypotheses) {
        if (hypotheses.isEmpty()) {
            return Likelihood.LOW;
        }
        // 列表已按可信度降序排列（Strategy 保证），取第一个
        return hypotheses.get(0).likelihood();
    }

    /**
     * 构建归因摘要（一句话）。
     */
    private String buildSummary(DiagnosisCategory category, List<Hypothesis> hypotheses) {
        String topDesc = hypotheses.isEmpty() ? "证据不足" : hypotheses.get(0).description();
        return switch (category) {
            case RISK_TRIGGER -> "安全策略拦截：LLM 生成的脚本命中黑名单。" + topDesc;
            case SANDBOX_FAILURE -> "沙盒执行失败：" + topDesc;
            case DATA_MISSING -> "数据缺失：" + topDesc;
            case EXECUTION_ERROR -> "执行错误：" + topDesc;
            case HEALTHY -> "未检测到异常。";
        };
    }
}
