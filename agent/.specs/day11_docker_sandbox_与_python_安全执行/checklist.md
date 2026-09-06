# Day 11 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Sandboxed Execution Capability

### Before
Day 10 可以审计缺口，但不能安全执行生成代码。

### After
Agent 可以在受控沙盒中执行计算任务，违规/超时可被终止并审计。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 10 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
