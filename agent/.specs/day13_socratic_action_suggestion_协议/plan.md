# Day 13 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: Socratic Action Suggestion 协议.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Interactive Action Contract

### Before
Day 12 输出诊断文本，但前端无法稳定识别操作意图。

### After
Agent 输出统一 ActionSuggestion 协议，前端可渲染并提交 action_id。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 12 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
