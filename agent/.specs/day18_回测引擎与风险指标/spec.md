# Day 18 Spec — 回测引擎与风险指标

## Goal
Backtrader/Java 模拟盘适配；收益率、Sharpe、Max Drawdown、胜率、换手等指标形成结构化结果。

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
Backtest Evaluation Capability

### Before
Day 17 可以实时展示 Agent，但策略质量缺乏统一评估闭环。

### After
Agent 的策略输出可以进入回测并产生可审计指标。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 17 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
