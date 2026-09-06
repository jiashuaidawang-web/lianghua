# Day 4 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Agent State & Graph Runtime

### Before
Day 1~3 是单调用/Tool 闭环。

### After
Quant Agent 拥有显式 State、Node、Edge 和统一执行入口。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 3 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
