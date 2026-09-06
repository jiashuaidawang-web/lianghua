# Day 7 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Durable Execution Capability

### Before
Day 6 的图只能依赖进程内内存 State。

### After
Agent 可以保存/恢复执行上下文，并在进程重启后继续。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 6 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
