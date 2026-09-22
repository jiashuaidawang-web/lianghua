# Day 12 Plan — Socratic Diagnosis Capability

## 1. 总体策略

按 Spec 的分层边界，**自底向上**实现：

```
domain（枚举 + POJO）
  → application（SocraticDiagnoser）
    → graph/nodes（SocraticDiagnosticNode）
      → domain/state（StateKeys + QuantAgentState accessor）
        → graph/topology（QuantAgentStateGraph 加节点 + 条件边）
          → test（unit + regression）
```

严格参照 Day 10 的 `DiffAuditNode + DiffAuditService + GapMatrix` 模式——**同构复用**。

## 2. 依赖清单（全部已存在于工程）

| 依赖 | 用途 | 来源 |
|---|---|---|
| `org.bsc.langgraph4j.state.AgentState` | 状态基类 | Day 4 |
| `dev.langchain4j.model.chat.ChatLanguageModel` | LLM 调用 | Day 1 / Day 2 |
| `com.quant.agent.domain.state.QuantAgentState` | 扩展 state | Day 4 |
| `com.quant.agent.domain.audit.GapMatrix`（参照） | 同构模式 | Day 10 |
| `com.quant.agent.infrastructure.llm.LlmConfiguration` | LLM 配置 | Day 1 |

**不引入任何新依赖**。

## 3. 文件变更清单

### 新增文件（7 个）

| 文件 | 层 | 职责 |
|---|---|---|
| `domain/diagnosis/DiagnosisCategory.java` | domain | 枚举：DATA / STRATEGY / CODE / ENVIRONMENT |
| `domain/diagnosis/DiagnosisStatus.java` | domain | 枚举：PENDING / DIAGNOSED / VALIDATION_FAILED / LLM_ERROR |
| `domain/diagnosis/DiagnosisResult.java` | domain | POJO（category/hypothesis/evidence/confidence/nextAction） |
| `application/diagnosis/SocraticDiagnoser.java` | application | 拼 prompt + 调 LLM + 校验 |
| `graph/nodes/SocraticDiagnosticNode.java` | graph | 读 symptom → 调 Diagnoser → 写 State |
| `graph/nodes/SocraticDiagnosticNodeTest.java` | test | 单元测试（mock LLM） |
| `graph/topology/QuantAgentStateGraphTest.java`（追加） | test | 拓扑路由测试 |

### 修改文件（3 个）

| 文件 | 改动 |
|---|---|
| `domain/state/StateKeys.java` | 新增 SYMPTOM / DIAGNOSIS_RESULT / DIAGNOSIS_STATUS 常量 |
| `domain/state/QuantAgentState.java` | 新增 symptom() / diagnosisResult() / diagnosisStatus() accessor |
| `graph/topology/QuantAgentStateGraph.java` | 新增 SOCRATIC_DIAGNOSTIC 节点 + 条件边 |

## 4. 关键设计决策

### 4.1 DiagnosisResult 放 domain 还是 application？

**放 domain**——理由：
- 它是"诊断"这个领域概念的核心数据结构，不依赖任何框架。
- 和 `GapMatrix` 同构（都是 domain 层的"产出物"）。
- 未来 Day 13 Action Contract、Day 14 HITL 都会复用它。

### 4.2 SocraticDiagnoser 用 AiServices 接口还是裸 ChatLanguageModel？

**用裸 ChatLanguageModel**——理由：
- Socratic 诊断的 prompt 是**一次性生成 JSON**，不需要 AiServices 的"系统提示 + 多轮对话"抽象。
- 参照 `StructuredAnalysisService` 的 `generateJson` 模式（如果存在），或直接用 `chatLanguageModel.generate(prompt)`。
- 更容易 mock（测试时只 mock `ChatLanguageModel`）。

### 4.3 为什么不在 Node 里直接调 LLM？

Constitution 规定："LLM output is untrusted input: validate before state mutation"。
- Node 负责"读 State → 调 Service → 写 State"（编排）。
- Service 负责"拼 prompt + 调 LLM + 校验"（业务逻辑）。
- 这样校验逻辑可以**独立于图**测试。

### 4.4 路由逻辑放哪？

放在 `QuantAgentStateGraph` 的条件边路由函数里（和 Day 10 的 `reviewRouteMap` 同位置）。
- **不放在 Node 里**：Node 只产出诊断结果，不决定下一步。
- **不放在 Service 里**：Service 是纯业务逻辑，不该知道图拓扑。
- **放图里**：路由是图的职责（和 review→planner / review→diffAudit 同构）。

## 5. 数据流（端到端一次调用）

```
用户请求（回测异常）
  ↓
PlannerNode → ExecutorNode → ReviewNode（判定 fail）
  ↓ 写 State: symptom="回测收益从15%跌到-40%"
  ↓
SocraticDiagnosticNode.apply(state)
  ↓ 读 state.symptom()
  ↓ 调 SocraticDiagnoser.diagnose(symptom, context)
    ↓ 拼 prompt（含 symptom + 历史 context + 输出格式要求）
    ↓ chatLanguageModel.generate(prompt)  ← LLM 概率性
    ↓ 解析 JSON → DiagnosisResult 草稿
    ↓ 校验 category/confidence/evidence  ← Java 确定性
  ↓ 写 State: diagnosisResult=..., diagnosisStatus=DIAGNOSED
  ↓
条件边路由函数（读 state.diagnosisResult()）
  ↓ category=DATA + confidence=0.85 → planner（重规划）
  ↓ category=STRATEGY + confidence=0.6 → END(needsHuman=true)
```

## 6. 测试计划

### 6.1 新增测试（SocraticDiagnosticNodeTest）

| 测试方法 | mock 行为 | 断言 |
|---|---|---|
| `shouldDiagnoseDataIssue` | LLM 返回合法 JSON（category=DATA） | status=DIAGNOSED, result.category=DATA |
| `shouldRouteToPlannerWhenRetryable` | DATA + confidence=0.9 | 条件边返回 PLANNER |
| `shouldRouteToHumanWhenStrategyLowConfidence` | STRATEGY + confidence=0.5 | 条件边返回 END |
| `shouldHandleInvalidCategory` | LLM 返回 "category":"玄学问题" | status=VALIDATION_FAILED |
| `shouldHandleLlmTimeout` | LLM 抛 RuntimeException | status=LLM_ERROR |

### 6.2 回归测试

- `QuantAgentStateGraphTest`（Day 5 拓扑仍通）
- `DiffAuditNodeTest`（Day 10 审计仍通）
- `PlannerServiceTest`（Day 5 规划仍通）

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| LLM 输出不符合 JSON 格式 | Java 校验 + status=VALIDATION_FAILED → 人工 |
| LLM "自信地错" | category=策略/代码时强制人工兜底 |
| 路由逻辑复杂 | 抽成独立方法 `routeByDiagnosis`，单测覆盖 |
| 旧拓扑受影响 | 只在 review fail 分支插入，pass 分支不动 |

## Vertical Evolution Contract

### Capability
Socratic Diagnosis Capability

### Before
Day 11 已有可安全执行的因子环境（Docker 沙盒）。

### After
Agent 遇到异常时会进入可解释的诊断路径，而不是直接修改策略。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 11 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
