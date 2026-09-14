# Day 8 Tasks

- [x] 确认 pom 已有 webflux（WebClient/Reactor），无需新增依赖
- [x] 实现领域层：`MarketPrice` / `MarketFundamental`（强类型 + isValid + degraded + toJson）
- [x] 实现 `RateLimiter`（令牌桶，JDK 原生，快速拒绝非阻塞）
- [x] 实现 `MarketDataCache`（TTL，惰性过期 + 主动清理 + 命中率统计）
- [x] 实现 `EastMoneyAdapter`（WebClient + timeout + fallback）
- [x] 实现 `MarketDataGateway`（编排限流→缓存→Adapter→降级）
- [x] 重构 `StockTools` 注入 Gateway（保持 @Tool 签名不变）
- [x] 装配 Bean（AiServicesConfiguration 新增 Day 8 护栏链）
- [x] 新增正常路径测试（RateLimiter / Cache / Gateway / Domain / StockTools）
- [x] 新增异常/边界路径测试（限流 / 超时 / 降级 / null symbol / 并发）
- [x] 运行 `mvn test` 全绿（74 tests, 0 failures）
- [x] 更新 spec/plan/tasks/checklist
- [x] Git commit

## Vertical Evolution Contract

### Capability
Finance Data Tooling

### Before
Day 7 Agent Runtime 已经稳定，StockTools 返回硬编码 Mock。

### After
Agent 可以安全访问行情/基本面数据，外部依赖具备工程保护。

### Reuse-first
复用 Day 1~7 的 Runtime、State、Graph、Tool、Adapter、Event、测试基建。

### Regression
完成后验证历史能力不退化，关键回归证据记录到 checklist.md。
