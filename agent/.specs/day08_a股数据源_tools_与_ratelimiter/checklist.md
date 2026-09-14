# Day 8 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [x] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## Test Results（实测证据）

```
Tests run: 74, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

新增测试（33 个，5 个测试类）：
- `RateLimiterTest` (5 tests) —— 令牌桶突发、等待恢复、非法参数、多令牌、并发安全
- `MarketDataCacheTest` (7 tests) —— 存取、未命中、TTL 过期、自定义 TTL、最大条目、invalidate、命中率
- `MarketDataGatewayTest` (9 tests) —— 缓存命中、缓存未命中调 Adapter、限流返回缓存、限流无缓存降级、Adapter 异常降级、非法数据降级、基本面、null/空 symbol
- `MarketDataDomainTest` (11 tests) —— MarketPrice/MarketFundamental 的 isValid、toJson、degraded、null 字段
- `Day03StockToolsTest` (重构为 3 tests) —— 委托 gateway、异常降级

核心改动（6 个新增 + 2 个修改）：
- 新增 `domain/tool/MarketPrice.java` —— 行情领域对象
- 新增 `domain/tool/MarketFundamental.java` —— 基本面领域对象
- 新增 `infrastructure/tool/RateLimiter.java` —— 令牌桶限流（JDK 原生）
- 新增 `infrastructure/tool/MarketDataCache.java` —— TTL 缓存（JDK 原生）
- 新增 `infrastructure/tool/EastMoneyAdapter.java` —— 东财适配器（WebClient + timeout + fallback）
- 新增 `infrastructure/tool/MarketDataGateway.java` —— 编排中心
- 修改 `application/tool/StockTools.java` —— 注入 Gateway，保持 @Tool 签名
- 修改 `infrastructure/llm/AiServicesConfiguration.java` —— 装配 Day 8 护栏链

关键设计决策：
- **零新增依赖**：限流/缓存全部 JDK 原生实现（不引入 Caffeine/Resilience4j/Guava）
- **快速拒绝**：RateLimiter 超限不阻塞，Gateway 走降级
- **degraded 标记**：降级数据带 degraded=true，LLM 可识别可信度
- **lastKnownGood**：Adapter 失败时优先返回上次成功数据
- **接口稳定**：StockTools @Tool 签名不变 → DataFetchTaskHandler 无感

## Vertical Evolution Contract

### Capability
Finance Data Tooling

### Before
Day 7 Agent Runtime 已经稳定，StockTools 返回硬编码 Mock 数据。

### After
Agent 可以安全访问行情/基本面数据，外部依赖具备工程保护（限流 + 缓存 + 超时 + 降级兜底）。

### Reuse-first
本 Day 优先复用 Day 1~7 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。

### Regression Results（回归证据）

| Day | 核心能力 | 测试类 | 结果 |
|-----|---------|--------|------|
| Day1 | LLM Streaming | `Day01QuantLlmServiceTest` (2 tests) | ✅ PASS |
| Day2 | Structured Output | `Day02StructuredAnalysisServiceTest` (4 tests) | ✅ PASS |
| Day3 | Tool Calling | `Day03StockToolsTest` (3 tests，重构) | ✅ PASS |
| Day4 | Graph Runtime + 条件边 | `QuantAgentStateGraphTest` Day4 (3 tests) | ✅ PASS |
| Day4 | State 体系 | `QuantAgentStateTest` (4 tests) | ✅ PASS |
| Day5 | Planner + 重规划循环 | `QuantAgentStateGraphTest` Day5 (4 tests) + `PlannerServiceTest` (8 tests) | ✅ PASS |
| Day6 | 终止策略显式化 | `ReviewNodeTest` (3 tests) + `QuantAgentStateGraphTest` Day6 (1 test) | ✅ PASS |
| Day7 | Checkpoint 快照持久化 | `CheckpointTest` (3 tests) | ✅ PASS |
| **8** | **Finance Data Tooling** | **5 个新测试类 (33 tests)** | ✅ PASS |

全部历史能力无退化。74 tests, 0 failures。

## Day-specific Definition of Done（engineering prompt §9）

- [x] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [x] 旧能力保持可用（Day1~7 测试全绿）
- [x] Spec / Plan / Tasks / Checklist 已更新
- [x] 新增测试通过（74 tests, 0 failures）
- [x] 关键历史能力 Regression 通过（见上表）
- [x] Git diff 可解释（待 commit）

## Day 08 特殊验收（engineering prompt §8）

金融数据访问必须走 Adapter + Tool；至少有 timeout、rate limit、cache/fallback 中的工程保护组合。

- [x] 金融数据访问走 `EastMoneyAdapter` + `StockTools`（@Tool 边界）
- [x] timeout：EastMoneyAdapter 连接 2s + 读取 3s
- [x] rate limit：RateLimiter 令牌桶（1/s，桶容量 2）
- [x] cache：MarketDataCache TTL 5s + 惰性过期 + 主动清理
- [x] fallback：Adapter 失败 → lastKnownGood → Mock（degraded=true）
