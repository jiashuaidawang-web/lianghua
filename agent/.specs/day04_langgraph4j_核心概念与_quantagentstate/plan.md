# Day 4 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: LangGraph4j 核心概念与 QuantAgentState.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

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
