# Day 13 Spec — Socratic Action Suggestion 协议

## Goal
标准 JSON 卡片：reason、evidence、suggested_actions、risk、requires_confirmation；前后端松耦合。

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
Interactive Action Contract

### Before
Day 12 输出诊断文本，但前端无法稳定识别操作意图。

### After
Agent 输出统一 ActionSuggestion 协议，前端可渲染并提交 action_id。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 12 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
