# Day 8 Spec — A股数据源 Tools 与 RateLimiter

## Goal
东方财富/同花顺适配层、缓存、限流、超时、熔断思想；工具层只返回结构化领域结果。

## Scope
- In scope: learning + implementation in `/lianghua/agent`.
- Out of scope: unrelated infrastructure refactors.

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output is validated before side effects.

## Definition of Done
See checklist.md.

## Vertical Evolution Contract

### Capability
Finance Data Tooling

### Before
Day 7 Agent Runtime 已经稳定。

### After
Agent 可以安全访问行情/基本面数据，外部依赖具备工程保护。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 7 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
