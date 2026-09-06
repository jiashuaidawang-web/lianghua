# Day 7 Spec — Checkpoint / Saver 与断点续传

## Goal
thread_id / RunnableConfig / checkpoint 快照；优先学习 SQLite/文件级验证，再接 PG/Redis。

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
Durable Execution Capability

### Before
Day 6 的图只能依赖进程内内存 State。

### After
Agent 可以保存/恢复执行上下文，并在进程重启后继续。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 6 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
