# Day 6 Spec — Conditional Edges 与 Re-planning Loop

## Goal
根据 State 中的风险/执行结果选择路径；建立 planner→executor→review→replan 循环。

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
Re-planning Capability

### Before
Day 5 Planner 一次性生成计划后执行。

### After
Agent 能发现失败/缺口，有限次数回到 Planner 重规划。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 5 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
