# Day 16 Spec — Research / Strategy / Risk 多 Agent 协同

## Goal
多个专职 Agent 通过 StateGraph 协作；Research→Strategy→Risk→Decision，明确数据与权限边界。

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
Multi-Agent Collaboration

### Before
Day 15 已有 RAG 研究上下文与 HITL。

### After
形成有职责边界的多 Agent 协同闭环，Risk 成为独立的确定性闸门。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 15 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
