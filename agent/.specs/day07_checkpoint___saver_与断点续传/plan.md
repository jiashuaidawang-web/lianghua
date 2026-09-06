# Day 7 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: Checkpoint / Saver 与断点续传.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

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
