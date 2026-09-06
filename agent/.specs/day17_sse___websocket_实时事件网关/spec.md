# Day 17 Spec — SSE / WebSocket 实时事件网关

## Goal
Task 状态、日志、token、Socratic card、interrupt/resume 作为统一 AgentEvent 流。

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
Realtime Agent Gateway

### Before
Day 16 后端 Agent 能跑，但前端看不到完整执行过程。

### After
Agent Runtime 产生统一事件流，前端可实时观察并交互。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 16 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
