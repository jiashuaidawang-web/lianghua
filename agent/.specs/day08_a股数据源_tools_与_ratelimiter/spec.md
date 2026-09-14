# Day 8 Spec — A股数据源 Tools 与 RateLimiter

## Goal
把东方财富/同花顺等数据能力封装成受控 Tool，并加入限流/缓存/超时/降级兜底。工具层只返回结构化领域结果。

## Scope
- In scope: Day 8 在 `/lianghua/agent` 中实现 Finance Data Tooling Capability。
- Out of scope: 不新增第三方依赖（Caffeine/Resilience4j/Guava 均不引入，全部 JDK 原生实现）。

## Contract
- Inputs, outputs, errors and observability must be explicit.
- Untrusted model output is validated before side effects.
- 外部 HTTP 必须显式 timeout；限流/缓存/降级必须有明确边界。

## Definition of Done
见 checklist.md。

## Vertical Evolution Contract

### Capability
Finance Data Tooling

### Before
Day 7 Agent Runtime 已经稳定，但 StockTools 返回硬编码 Mock 数据（price=1500.0），LLM 基于假数据做分析。

### After
Agent 可以安全访问行情/基本面数据，外部依赖具备工程保护（限流 + 缓存 + 超时 + 降级兜底）。

### Reuse-first
本 Day 优先复用 Day 1~7 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。
具体复用：`@Tool` 注解边界（Day3）、`DataFetchTaskHandler` 分发逻辑（Day5）、`ToolNode` 确定性调用（Day4）、State 体系、WebClient/Reactor（pom 已有 webflux）。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
