# Day 17 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Realtime Agent Gateway

### Before
Day 16 后端 Agent 能跑，但前端看不到完整执行过程。

### After
Agent Runtime 产生统一事件流，前端可实时观察并交互。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 16 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
