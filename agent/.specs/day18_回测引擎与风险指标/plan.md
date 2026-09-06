# Day 18 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: 回测引擎与风险指标.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Backtest Evaluation Capability

### Before
Day 17 可以实时展示 Agent，但策略质量缺乏统一评估闭环。

### After
Agent 的策略输出可以进入回测并产生可审计指标。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 17 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
