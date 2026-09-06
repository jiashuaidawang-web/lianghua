# Day 14 Spec — LangGraph4j HITL interrupt 挂起与恢复

## Goal
人工确认节点、GraphInterruptException/恢复语义、checkpoint 绑定 thread_id。

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
Human-in-the-Loop Capability

### Before
Day 13 已有明确可操作的用户动作协议。

### After
Agent 能把关键决策移交人类，并从同一执行上下文恢复。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 13 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
