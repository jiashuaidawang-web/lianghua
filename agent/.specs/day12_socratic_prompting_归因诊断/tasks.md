# Day 12 Tasks — Socratic Diagnosis Capability

按 **domain → application → graph/state → topology → test** 顺序执行。
每完成一项打勾，并在 checklist.md 记录证据。

---

## Phase 1：Domain（领域模型）

- [ ] **T1.1** 新建 `domain/diagnosis/DiagnosisCategory.java`
  - 枚举：`DATA, STRATEGY, CODE, ENVIRONMENT`
  - 含 `fromValue(String)` 静态方法（校验用）
- [ ] **T1.2** 新建 `domain/diagnosis/DiagnosisStatus.java`
  - 枚举：`PENDING, DIAGNOSED, VALIDATION_FAILED, LLM_ERROR`
- [ ] **T1.3** 新建 `domain/diagnosis/DiagnosisResult.java`
  - `record` 类型（category, hypothesis, evidence, confidence, nextAction）
  - 实现 `Serializable`
  - 含 `EMPTY` 常量（空诊断）
  - 含 `isValid()` 方法（非空校验）

## Phase 2：Application（业务逻辑）

- [ ] **T2.1** 新建 `application/diagnosis/SocraticDiagnoser.java`
  - 构造器注入 `ChatLanguageModel`
  - `DiagnosisResult diagnose(String symptom, Map<String,String> context)` 方法
  - 拼 Socratic prompt（强制输出 JSON 格式）
  - 调 `chatLanguageModel.generate(prompt)`
  - 解析 JSON → DiagnosisResult
  - 校验 category/confidence/evidence
  - 异常处理：超时/格式错 → 返回带 `VALIDATION_FAILED` 状态的 result

## Phase 3：State 扩展

- [ ] **T3.1** 修改 `domain/state/StateKeys.java`
  - 新增 `SYMPTOM = "symptom"`
  - 新增 `DIAGNOSIS_RESULT = "diagnosisResult"`
  - 新增 `DIAGNOSIS_STATUS = "diagnosisStatus"`
- [ ] **T3.2** 修改 `domain/state/QuantAgentState.java`
  - 新增 `symptom()` accessor
  - 新增 `diagnosisResult()` accessor（缺省 `DiagnosisResult.EMPTY`）
  - 新增 `diagnosisStatus()` accessor

## Phase 4：Graph（节点 + 拓扑）

- [ ] **T4.1** 新建 `graph/nodes/SocraticDiagnosticNode.java`
  - 构造器注入 `SocraticDiagnoser`
  - `apply(QuantAgentState state)` 方法
  - 读 `state.symptom()` → 调 `diagnoser.diagnose()` → 写 State
  - 返回增量 Map（参照 DiffAuditNode 模式）
- [ ] **T4.2** 修改 `graph/topology/QuantAgentStateGraph.java`
  - 新增 `SOCRATIC_DIAGNOSTIC` 节点名常量
  - 新增构造器参数 `SocraticDiagnosticNode`
  - 在 `compileDay5()` 里注册节点
  - 修改 review 之后的条件边：fail → SOCRATIC_DIAGNOSTIC（而不是直接 planner）
  - 新增 socraticDiagnostic 之后的路由：
    - retryable & 高置信 → PLANNER
    - 否则 → END
  - 抽 `routeByDiagnosis(state)` 私有方法

## Phase 5：Test（测试）

- [ ] **T5.1** 新建 `graph/nodes/SocraticDiagnosticNodeTest.java`
  - mock `ChatLanguageModel`
  - 正常路径测试（DATA category）
  - 路由到 planner 测试
  - 路由到 HITL 测试
  - 非法 category 测试
  - LLM 超时测试
- [ ] **T5.2** 追加 `graph/topology/QuantAgentStateGraphTest.java`
  - 拓扑注册验证
  - 路由逻辑单测

## Phase 6：回归 + 验证

- [ ] **T6.1** 跑 `mvn test`，全绿
- [ ] **T6.2** 验证 Day 5 链路（planner→executor→review→render）仍通
- [ ] **T6.3** 验证 Day 10 链路（diffAudit）仍通
- [ ] **T6.4** 把回归证据写入 checklist.md

## Phase 7：文档 + 提交

- [ ] **T7.1** 更新 `checklist.md`（逐项打勾 + 证据）
- [ ] **T7.2** Git add + commit
  - 消息：`Day 12 竣工: Socratic Diagnosis Capability — 苏格拉底式归因诊断`

---

## 执行顺序（依赖关系）

```
T1.1 → T1.2 → T1.3
  ↓
T2.1（依赖 T1.x）
  ↓
T3.1 → T3.2
  ↓
T4.1（依赖 T2.1）→ T4.2（依赖 T3.2, T4.1）
  ↓
T5.1 → T5.2（依赖 T4.2）
  ↓
T6.1 → T6.2 → T6.3 → T6.4
  ↓
T7.1 → T7.2
```

**禁止跳步**：前一 Phase 未完成不得进入下一 Phase。

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
