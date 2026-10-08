# Day 12 Plan

1. Read constitution.md and existing implementation (done — see context map).
2. Define domain contract: `DiagnosisCategory`, `Likelihood`, `Hypothesis`, `Diagnosis` under `domain/diagnosis/`.
3. Implement Strategy pattern: `DiagnosisStrategy` interface + 5 strategies under `application/diagnosis/`.
4. Implement `DiagnosisService` decision-tree orchestrator.
5. Implement `DiagnosisNode`.
6. Extend `StateKeys` + `QuantAgentState` accessor for DIAGNOSIS.
7. Extend `RenderNode` to render a Diagnosis when present (else fallback to results).
8. Wire graph topology: insert DIAGNOSIS node + conditional edge in `compileDay5`.
9. Register beans in `AiServicesConfiguration`.
10. Add tests + run regression `mvn -o test`.
11. Update checklist.

## 包结构与接口边界

```
com.quant.agent
├── domain/diagnosis
│   ├── DiagnosisCategory.java   归因类别枚举（确定性分类结果）
│   ├── Likelihood.java          假说可信度枚举
│   ├── Hypothesis.java          单条假说（id/description/evidence/likelihood）
│   └── Diagnosis.java           证据模型（category/summary/hypotheses/recommendedActions/confidence）
├── application/diagnosis
│   ├── DiagnosisStrategy.java   策略接口
│   ├── DataMissingStrategy.java
│   ├── SandboxFailureStrategy.java
│   ├── RiskTriggerStrategy.java
│   ├── ExecutionErrorStrategy.java
│   ├── HealthyStrategy.java
│   └── DiagnosisService.java    决策树 + Strategy 编排
└── graph/nodes
    └── DiagnosisNode.java       读证据 → 调 Service → 写 DIAGNOSIS
```

### 数据流
```
executor 写入 RESULTS（含成功/失败文本）
  → DiagnosisNode.apply()
    → DiagnosisService.diagnose(state)
        → [1] 扫描 RESULTS 证据（确定性决策树，按严重度降序命中即停）
        → [2] 选 Strategy
        → [3] Strategy 产出 hypotheses + recommendedActions
        → [4] 组装 Diagnosis → 写 State.DIAGNOSIS
  → 条件边：HEALTHY → review ； ANOMALY → render(diagnosis) → END
```

### 关键设计约束
- 分类**不调 LLM**：决策树确定性完成，保证可审计、可单测。
- 严重度优先级：RISK_TRIGGER > SANDBOX_FAILURE > DATA_MISSING > EXECUTION_ERROR > HEALTHY。
- Diagnosis 实现 Serializable（写 State，服务 Day7 Checkpoint）。
- RenderNode 仅在"有非 HEALTHY 诊断"时渲染诊断，否则保持原 results 渲染行为（最小侵入）。

## 官方 API / 版本
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Socratic Diagnosis Capability

### Before
Day 11 已有可安全执行的因子环境；异常时只能盲目重规划或暴露原始错误。

### After
Agent 遇到异常时会进入可解释的诊断路径（证据 → 归因 → 推荐动作），而不是直接修改策略。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 11 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
