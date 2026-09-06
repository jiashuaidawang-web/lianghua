# Day 1 Tasks

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
LLM Streaming Capability

### Before
项目仅有基础 Spring Boot 工程/LLM adapter 骨架。

### After
系统具备统一 LLM Streaming 能力，可持续向前端推送模型增量输出。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 0 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
