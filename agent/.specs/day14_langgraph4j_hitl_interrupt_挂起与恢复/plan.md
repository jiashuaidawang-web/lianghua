# Day 14 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: LangGraph4j HITL interrupt 挂起与恢复.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Human-in-the-Loop Capability

### Before
Day 13 已有明确可操作的用户动作协议。

### After
Agent 能把关键决策移交人类，并从同一执行上下文恢复。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 13 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
