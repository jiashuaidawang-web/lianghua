# Day 19 Spec — E2E 闭环与鲁棒性压测

## Goal
输入→审计→策略→回测→风险→HITL→报告；覆盖超时、重复执行、幂等、断点恢复、限流。

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
Production E2E & Resilience

### Before
Day 18 各模块基本可用，但缺少全局系统验证。

### After
得到可重复执行的 E2E 场景和性能/鲁棒性基线。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 18 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
