# Day 1 Spec — Spring Boot + LangChain4j 原生 StreamingChatModel + SSE

## Goal
OpenAiStreamingChatModel、StreamingChatResponseHandler、Flux、SSE。生产代码走 handler → Flux 桥接，不使用 Spring AI。

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
LLM Streaming Capability

### Before
项目仅有基础 Spring Boot 工程/LLM adapter 骨架。

### After
系统具备统一 LLM Streaming 能力，可持续向前端推送模型增量输出。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 0 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
