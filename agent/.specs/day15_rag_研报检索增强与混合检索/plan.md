# Day 15 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: RAG：研报检索增强与混合检索.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Research RAG Capability

### Before
Day 14 Agent 可协同工具和人工。

### After
Research Node 能基于可追溯资料增强上下文，并把证据带入策略决策。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 14 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
