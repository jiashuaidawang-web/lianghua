# Day 12 Spec — Socratic Prompting 归因诊断

## Goal
当数据缺失、沙盒执行失败或风控触发时，Agent **不猜代码、不盲目重规划**，而是进入一条可解释的诊断路径：收集证据 → 归因分类 → 生成假说 → 提出证据驱动的推荐动作。

## Scope
- In scope: 在现有 `/lianghua/agent` 中增量插入 Socratic Diagnosis Capability。
- Out of scope: 无关基础设施重构；不引入新的 LLM 调用到核心分类路径（分类必须确定性）。

## Contract
- 输入 / 输出 / 错误 / 可观测性必须显式。
- 归因**分类**由 Java 决策树确定性完成，不允许 LLM 猜"严不严重"（constitution: deterministic policy checks）。
- LLM 输出视为不可信输入；本能力本身不调 LLM 做分类（保持单测无 LLM、行为可审计）。
- 失败必须可审计：每种异常类别有证据链（evidence）+ 假说（hypothesis）+ 推荐动作（recommended action）。

## 设计边界（纵向演进）

### 新增最小元素

**域模型** `domain/diagnosis/`
- `DiagnosisCategory.java` —— 归因类别枚举（HEALTHY / DATA_MISSING / SANDBOX_FAILURE / RISK_TRIGGER / EXECUTION_ERROR）。
- `Likelihood.java` —— 假说可信度（HIGH / MEDIUM / LOW）。
- `Hypothesis.java` —— 单条假说（id、描述、evidence 列表、likelihood），Serializable。
- `Diagnosis.java` —— 证据模型（category、summary、hypotheses、recommendedActions、confidence）， Serializable（写 State，支持 Day7 Checkpoint）。

**应用层** `application/diagnosis/`
- `DiagnosisStrategy.java` —— 策略接口：给定证据产出假说 + 推荐动作。
- `DataMissingStrategy.java` —— DATA_MISSING 归因（数据缺失：源不可用 / 字段空 / 超时）。
- `SandboxFailureStrategy.java` —— SANDBOX_FAILURE 归因（超时 / 错误退出）。
- `RiskTriggerStrategy.java` —— RISK_TRIGGER 归因（安全策略拦截）。
- `ExecutionErrorStrategy.java` —— EXECUTION_ERROR 归因（通用执行失败兜底）。
- `HealthyStrategy.java` —— HEALTHY（无异常，空假说）。
- `DiagnosisService.java` —— 决策树 + Strategy 编排：扫描 results 证据 → 选最高严重类别 → 委托 Strategy → 组装 Diagnosis。

**图节点** `graph/nodes/`
- `DiagnosisNode.java` —— 读 State 证据，调 DiagnosisService，写 DIAGNOSIS。

### 复用（不新建第二套 Runtime）
- `QuantAgentState` / `StateKeys`：新增 DIAGNOSIS key，既有 accessor 模式。
- `ExecutorNode` 的 RESULTS：诊断的**证据源**，不重复采集。
- `RenderNode`：增强为"有异常诊断时渲染诊断，否则渲染 results"（复用既有渲染位）。
- `QuantAgentStateGraph`：在 executor→review 之间插入 diagnosis 节点 + 一条条件边。
- `AiServicesConfiguration` 装配车间：注册 DiagnosisService / Strategies / DiagnosisNode。

### 拓扑变化
```
planner → executor → diagnosis ─┬─[HEALTHY]──→ review → diffAudit →(pass)→ render → END
                                └─[ANOMALY]───→ render(diagnosis) → END
```
- HEALTHY：走原流程（review 审查、diffAudit 审计、render 汇总 results）。
- ANOMALY：跳过 review 的盲目重规划循环，直接 render 诊断（证据→归因→推荐动作），落 State 供 HITL/用户决策。

### 决策树分类规则（确定性，按严重度降序命中即停）
1. 任一 SANDBOX 结果含 `[SANDBOX REJECTED]` → `RISK_TRIGGER`
2. 任一 SANDBOX 结果含 `[SANDBOX TIMEOUT]` → `SANDBOX_FAILURE`
3. 任一 SANDBOX 结果含 `[SANDBOX ERROR]` → `SANDBOX_FAILURE`
4. 任一结果含数据缺失信号（"数据获取失败"/"无结果"/"缺失"/"网络超时"/"不支持的语言"）→ `DATA_MISSING`
5. 任一结果含通用失败信号（"失败"/"未知类型，未执行"）→ `EXECUTION_ERROR`
6. 全空 → `DATA_MISSING`
7. 全无上述信号 → `HEALTHY`

## 插入点
- 图拓扑：executor 之后新增 diagnosis 节点与条件边；Day4 拓扑不受影响。
- Controller：无新增端点（复用既有 render 输出）。

## 测试策略
- `DiagnosisServiceTest`（决策树）：覆盖 6 条分类规则 + 严重度优先级（如同时有 REJECTED 和 TIMEOUT，应命中 RISK_TRIGGER）。
- `DiagnosisNodeTest`：全成功→HEALTHY；含沙盒拒绝→RISK_TRIGGER 且写入 DIAGNOSIS + 假说/推荐动作非空。
- 策略单测归入 Service（每条规则对应一个 Strategy 路径）。
- 回归：全量 `mvn -o test`，Day1~Day11 历史测试不退化。

## Definition of Done
见 checklist.md。

## Vertical Evolution Contract

### Capability
Socratic Diagnosis Capability

### Before
Day 11 已有可安全执行的因子环境；异常时只能盲目重规划或暴露原始错误。

### After
Agent 遇到异常时会进入可解释的诊断路径（证据 → 归因 → 推荐动作），而不是直接修改策略或盲目重规划。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 11 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
