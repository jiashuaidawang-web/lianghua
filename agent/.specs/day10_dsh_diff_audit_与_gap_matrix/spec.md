# Day 10 Spec — DSH Diff Audit 与 Gap Matrix

## Goal
需求→现有因子库→DB Schema→Gap Matrix；输出证据、缺失字段、影响面、推荐动作。

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
DSH Diff Audit Capability

### Before
Day 1~9 已提供 LLM、State、Planner、Tools、MCP 等基础能力。

### After
Agent 能在正式执行前识别“需求 vs 现有能力”的落差，并形成结构化审计结果。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 9 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
