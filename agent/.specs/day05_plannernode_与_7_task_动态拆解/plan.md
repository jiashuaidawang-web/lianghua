# Day 5 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: PlannerNode 与 7 Task 动态拆解.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Planner Capability

### Before
Day 4 已有图和 State，但任务流是固定的。

### After
Agent 可以动态规划 7 类标准原子任务，并将计划纳入后续执行。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 4 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
