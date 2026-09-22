# Day 12 Spec — Socratic Diagnosis Capability

## Goal

回测异常 / 风控触发 / review fail 时，Agent **不直接改代码或重规划**，而是走一条
**证据驱动的苏格拉底式归因路径**，把异常归因到 **数据 / 策略 / 代码 / 环境** 四类之一，
给出 `hypothesis + evidence + confidence + nextAction`，并据此决定：
自动重规划、还是进 HITL 人工确认。

## Non-Goals

- 不做"自动修复代码"（修复是 Day 14 HITL 之后的事）。
- 不做"多轮对话式追问"（Day 12 是单轮诊断，多轮是 Day 13 Action Contract）。
- 不引入 Spring AI / beta 依赖。

## Architecture Boundaries

严格复用 Day 1–11 的分层，**不新建第二套 Runtime**：

```
interfaces     ← 不动（诊断是内部节点，不暴露新 HTTP 接口）
   ↑
graph/nodes    ← 新增 SocraticDiagnosticNode（参照 DiffAuditNode 模式）
   ↑
application    ← 新增 SocraticDiagnoser（拼 prompt + 调 LLM + 校验）
   ↑
domain         ← 新增 DiagnosisResult + DiagnosisCategory + DiagnosisStatus
   ↑
infrastructure ← 不动（复用 ChatLanguageModel / QuantLlmService）
```

## Domain Contract

### DiagnosisCategory（枚举，Java 确定性）

| 值 | 含义 | 典型触发 |
|---|---|---|
| `DATA` | 数据问题（缺失 / 延迟 / 格式错） | 行情接口返回空、字段缺失 |
| `STRATEGY` | 策略问题（逻辑 / 参数） | 回测收益暴跌、过拟合 |
| `CODE` | 代码问题（bug / 异常） | NPE、超时、StackOverflow |
| `ENVIRONMENT` | 环境问题（网络 / 依赖） | 连接超时、Docker 不可用 |

**关键**：LLM 只能输出这四类之一，否则 Java 校验失败 → 进人工。

### DiagnosisResult（POJO，Serializable）

```java
public record DiagnosisResult(
    String category,        // 必须 ∈ DiagnosisCategory.name()
    String hypothesis,      // LLM 的假设（人类可读）
    List<String> evidence,  // 证据链（非空）
    double confidence,      // [0.0, 1.0]
    String nextAction       // 建议动作（人类可读）
) implements Serializable {}
```

### DiagnosisStatus（枚举）

| 值 | 含义 |
|---|---|
| `PENDING` | 待诊断 |
| `DIAGNOSED` | 诊断完成（可路由） |
| `VALIDATION_FAILED` | LLM 输出不合规（进人工） |
| `LLM_ERROR` | LLM 调用失败（超时 / 网络，进人工） |

## State Contract（扩展 QuantAgentState）

新增 3 个 key：

| Key | 类型 | 写入方 | 读取方 |
|---|---|---|---|
| `symptom` | String | ReviewNode（fail 时写） | SocraticDiagnosticNode |
| `diagnosisResult` | DiagnosisResult | SocraticDiagnosticNode | 条件边路由函数 |
| `diagnosisStatus` | String | SocraticDiagnosticNode | 条件边 / 测试断言 |

新增 accessor：
- `state.symptom()` → String
- `state.diagnosisResult()` → DiagnosisResult（缺省 `DiagnosisResult.EMPTY`）
- `state.diagnosisStatus()` → String

## Graph Topology Change

**Before（Day 11）**：
```
review ──[fail]──→ planner（直接重规划，无诊断）
```

**After（Day 12）**：
```
review ──[fail]──→ socraticDiagnostic ──[retryable & 高置信]──→ planner（重规划）
                                              ──[需人工]──→ END（needsHuman=true）
                                              ──[LLM错误]──→ END（needsHuman=true）
```

`retryable` 的判定（Java 确定性规则）：
- `category ∈ {DATA, ENVIRONMENT}` **且** `confidence >= 0.7` → 自动重规划
- 否则 → 进 HITL（END + needsHuman）

**为什么这样分**：
- 数据/环境问题是"外部因素"，重试可能自动恢复。
- 策略/代码问题是"内部逻辑错"，重规划可能重复犯错，必须人工介入。

## Runtime Flow（谁读谁写谁决定）

```
1. ReviewNode 判定 fail
     ↓ 写 State: symptom="回测收益从15%跌到-40%", diagnosisStatus=PENDING

2. Graph 路由到 SocraticDiagnosticNode
     ↓ 读 State: symptom
     ↓ 调 SocraticDiagnoser.diagnose(symptom, context)
       ↓ 拼 Socratic prompt（"你是诊断专家，按 category/hypothesis/evidence/confidence/nextAction 输出 JSON"）
       ↓ 调 ChatLanguageModel.generate(prompt)（LLM 概率性行为）
       ↓ 解析 JSON → DiagnosisResult 草稿
       ↓ Java 校验：category 枚举 / confidence 范围 / evidence 非空（确定性行为）
     ↓ 写 State: diagnosisResult=..., diagnosisStatus=DIAGNOSED

3. 条件边路由函数（读 State: diagnosisResult）
     ↓ 如果 status=VALIDATION_FAILED 或 LLM_ERROR → END(needsHuman)
     ↓ 如果 category∈{DATA,ENV} 且 confidence≥0.7 → planner
     ↓ 否则 → END(needsHuman)
```

## Socratic Prompt 设计原则

- **强制推理链**：要求 LLM 先给 `hypothesis`（假设），再给 `evidence`（证据），最后给 `nextAction`（建议）。
- **禁止直接改代码**：prompt 里明确"只诊断，不生成代码"。
- **输出格式约束**：要求 JSON，字段固定（方便 Java 解析）。

## Validation Rules（Java 确定性，LLM 输出 = 不可信输入）

| 校验项 | 规则 | 失败处理 |
|---|---|---|
| JSON 可解析 | 能反序列化为 DiagnosisResult | status=VALIDATION_FAILED |
| category 枚举 | ∈ {DATA, STRATEGY, CODE, ENVIRONMENT} | status=VALIDATION_FAILED |
| confidence 范围 | [0.0, 1.0] | status=VALIDATION_FAILED |
| evidence 非空 | `evidence.size() >= 1` | status=VALIDATION_FAILED |
| LLM 调用 | 有超时（如 30s） | status=LLM_ERROR |

## Testing Strategy

| 测试 | 类型 | 断言重点 |
|---|---|---|
| `shouldDiagnoseDataIssue` | 单元（mock LLM） | category=DATA, status=DIAGNOSED, evidence 非空 |
| `shouldRouteToPlannerWhenRetryable` | 单元（mock LLM） | DATA + confidence=0.9 → 路由到 planner |
| `shouldRouteToHumanWhenStrategyAndLowConfidence` | 单元（mock LLM） | STRATEGY + confidence=0.5 → END(needsHuman) |
| `shouldHandleInvalidCategory` | 单元（mock LLM 返回非法 JSON） | status=VALIDATION_FAILED → END(needsHuman) |
| `shouldHandleLlmTimeout` | 单元（mock LLM 抛超时） | status=LLM_ERROR → END(needsHuman) |
| Regression: Day 5 planner→executor→review | 回归 | 旧链路仍通 |
| Regression: Day 10 diffAudit | 回归 | 审计仍通 |

**严格**：所有单元测试 **mock LLM**，不打真实端点。

## Failure Modes

| 场景 | 检测 | 处理 |
|---|---|---|
| LLM 输出格式错 | JSON 解析失败 | status=VALIDATION_FAILED → 人工 |
| LLM 编造 category | 枚举校验失败 | status=VALIDATION_FAILED → 人工 |
| LLM 调用超时 | timeout 异常 | status=LLM_ERROR → 人工 |
| LLM 返回 confidence=0.99 但归因错 | **工程无法检测**（LLM 会"自信地错"） | 靠 HITL 兜底（category=策略/代码时强制人工） |
| symptom 为空 | 前置校验 | 直接 END(needsHuman)，不浪费 LLM 调用 |

## Definition of Done

- [ ] 异常触发时走诊断路径，不直接重规划
- [ ] DiagnosisResult 强类型、可序列化（支持 Day 7 Checkpoint）
- [ ] category 枚举校验（Java 确定性）
- [ ] 路由逻辑：DATA/ENV + 高置信 → planner；否则 → HITL
- [ ] LLM 失败路径吞进 State（不抛到图外）
- [ ] 单元测试全通过（mock LLM）
- [ ] Day 5 / Day 10 回归通过
- [ ] 无 beta/snapshot 依赖

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
