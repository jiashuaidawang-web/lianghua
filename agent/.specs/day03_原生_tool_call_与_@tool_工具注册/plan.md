# Day 3 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: 原生 Tool Call 与 @Tool 工具注册.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Tool Calling Capability

### Before
Day 1~2 只有 LLM 文本/结构化输出能力。

### After
Agent 可以通过注册的 Java Tool 获取外部数据，不允许模型直接产生副作用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 2 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
