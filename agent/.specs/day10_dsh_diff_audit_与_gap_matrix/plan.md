# Day 10 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: DSH Diff Audit 与 Gap Matrix.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
DSH Diff Audit Capability

### Before
Day 1~9 已提供 LLM、State、Planner、Tools、MCP 等基础能力。

### After
Agent 能在正式执行前识别“需求 vs 现有能力”的落差，并形成结构化审计结果。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 9 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
