# Day 15 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
Research RAG Capability

### Before
Day 14 Agent 可协同工具和人工。

### After
Research Node 能基于可追溯资料增强上下文，并把证据带入策略决策。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 14 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
