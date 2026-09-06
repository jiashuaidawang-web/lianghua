# Day 1 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: Spring Boot + LangChain4j 原生 StreamingChatModel + SSE.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

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
