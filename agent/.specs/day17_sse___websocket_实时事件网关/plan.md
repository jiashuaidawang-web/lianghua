# Day 17 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: SSE / WebSocket 实时事件网关.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

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
