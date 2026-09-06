# Day 5 Spec — PlannerNode 与 7 Task 动态拆解

## Goal
LLM 负责计划生成，Java 负责 State 写入与结构校验；形成 7 个标准原子任务。

## Scope
- In scope: learning + implementation in `/lianghua/agent`.
- Out of scope: unrelated infrastructure refactors.

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output is validated before side effects.

## Definition of Done
See checklist.md.

## Vertical Evolution Contract

### Capability
Planner Capability

### Before
Day 4 已有图和 State，但任务流是固定的。

### After
Agent 可以动态规划 7 类标准原子任务，并将计划纳入后续执行。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 4 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
