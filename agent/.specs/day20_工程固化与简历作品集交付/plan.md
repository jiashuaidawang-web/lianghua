# Day 20 Plan

1. Read constitution.md and existing implementation.
2. Define domain/application/infrastructure boundaries.
3. Implement the smallest vertical slice for: 工程固化与简历作品集交付.
4. Add unit tests with deterministic fixtures.
5. Update docs/checklist and verify build.

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17

## Vertical Evolution Contract

### Capability
Production Release & Portfolio

### Before
Day 19 已有功能完整且通过 E2E/鲁棒性验证。

### After
形成可运行、可审计、可解释、可恢复、可展示的量化投研 Agent Release。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 19 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
