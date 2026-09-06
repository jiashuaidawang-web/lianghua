# Day 9 Checklist

- [ ] Function works on the happy path.
- [ ] Error path is covered.
- [ ] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [ ] No beta/snapshot dependency added.
- [ ] Unit tests pass.
- [ ] Manual verification completed where applicable.

## Vertical Evolution Contract

### Capability
MCP Capability

### Before
Day 8 Tool 只通过本地 Tool registry 调用。

### After
同一工具能力可通过 MCP 标准协议被外部 Agent/Host 发现和调用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 8 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
