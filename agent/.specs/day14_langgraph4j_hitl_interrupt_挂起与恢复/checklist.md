# Day 14 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Human-in-the-Loop Capability

### Before
Day 13 已有明确可操作的用户动作协议。

### After
Agent 能把关键决策移交人类，并从同一执行上下文恢复。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 13 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
