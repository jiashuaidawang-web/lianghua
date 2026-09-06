# Day 18 Tasks

- [ ] Confirm current API from official docs/Javadoc.
- [ ] Implement domain contract.
- [ ] Implement infrastructure adapter/node/tool.
- [ ] Wire the application path.
- [ ] Add normal-path JUnit 5 test.
- [ ] Add error/boundary JUnit 5 test.
- [ ] Run `mvn test`.
- [ ] Update README/checklist with observed results.

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
