# Day 12 Checklist — Socratic Diagnosis Capability

## 功能验收

- [x] **正常路径**：review fail → SocraticDiagnosticNode → 产出 DiagnosisResult 写入 State
- [x] **category 枚举校验**：LLM 输出非法 category → status=VALIDATION_FAILED → 进 HITL
- [x] **confidence 范围校验**：confidence 不在 [0,1] → VALIDATION_FAILED
- [x] **evidence 非空校验**：evidence 为空 → VALIDATION_FAILED
- [x] **路由：DATA/ENV + 高置信 → planner**（自动重规划）
- [x] **路由：STRATEGY/CODE 或 低置信 → END(needsHuman)**（进 HITL）
- [x] **路由：LLM 超时/异常 → END(needsHuman)**（失败兜底）
- [x] **symptom 为空**：直接 END(needsHuman)，不浪费 LLM 调用

## 工程质量

- [x] **LLM 输出先校验再写 State**（constitution 合规）
- [x] **失败路径吞进 State**（不抛到图外）
- [x] **DiagnosisResult 实现 Serializable**（支持 Day 7 Checkpoint）
- [x] **路由逻辑抽成独立方法** `routeByDiagnosis`（可单测）
- [x] **Socratic prompt 禁止直接输出代码**（只诊断）

## 测试验收

- [x] **正常路径单元测试**（mock LLM，category=DATA）
- [x] **路由到 planner 测试**（DATA + confidence≥0.7）
- [x] **路由到 HITL 测试**（STRATEGY 或 confidence<0.7）
- [x] **非法 category 测试**（VALIDATION_FAILED）
- [x] **LLM 超时测试**（LLM_ERROR）
- [x] **Day 5 回归**（planner→executor→review 仍通）
- [x] **Day 10 回归**（diffAudit 仍通）
- [x] **`mvn test` 全绿**（141 tests, 0 failures）

## 安全 + 合规

- [x] **无真实 API Key 进入源码/Git**
- [x] **无 beta/snapshot 依赖**
- [x] **LLM 调用有显式 timeout**（TODO Day 19 迁移到异步超时）
- [x] **高风险动作保留 HITL 闸门**

## 文档 + 可追溯

- [x] **Spec 四件套已更新**（spec/plan/tasks/checklist）
- [x] **Git diff 可解释**（每个 commit 一个 Capability）
- [x] **Git commit 消息规范**：`Day 12 竣工: Socratic Diagnosis Capability — 苏格拉底式归因诊断`

## 回归证据记录

```
Day 5 回归：
  - PlannerServiceTest: PASS (11 tests)
  - QuantAgentStateGraphTest: PASS (9 tests)

Day 10 回归：
  - DiffAuditServiceTest: PASS
  - DiffAuditNodeTest: PASS

Day 12 新增：
  - SocraticDiagnosticNodeTest: PASS (8 tests)
```

## 未解决风险

```
- LLM "自信地错" 无法工程检测，靠 HITL 兜底（category=策略/代码时强制人工）
- Socratic prompt 可能需要根据实际 LLM 输出调优
- LLM 调用超时控制待 Day 19 迁移到异步（当前依赖 HTTP 客户端层）
```

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
