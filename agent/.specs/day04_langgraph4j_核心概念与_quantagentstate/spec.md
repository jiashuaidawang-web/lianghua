# Day 4 Spec — LangGraph4j 核心概念与 QuantAgentState

## Goal
StateGraph、Node、Edge、CompiledGraph、RunnableConfig；设计不可变/可复制状态并容纳 LangChain4j ChatMessage。

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
Agent State & Graph Runtime

### Before
Day 1~3 是单调用/Tool 闭环。

### After
Quant Agent 拥有显式 State、Node、Edge 和统一执行入口。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 3 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
