# Day 18 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

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
