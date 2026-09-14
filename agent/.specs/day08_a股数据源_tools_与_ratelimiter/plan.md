# Day 8 Plan

## 目标
把 StockTools 从 Mock 升级为「受控真实数据访问」，新增限流 + 缓存 + 超时 + 降级兜底。

## 设计

### 新增组件（6 个）
1. `domain/tool/MarketPrice` —— 行情领域对象（强类型 + isValid + degraded 标记 + toJson）
2. `domain/tool/MarketFundamental` —— 基本面领域对象（pe/pb/sector 允许 null）
3. `infrastructure/tool/RateLimiter` —— 令牌桶限流（JDK 原生，快速拒绝非阻塞）
4. `infrastructure/tool/MarketDataCache` —— TTL 缓存（惰性过期 + 主动清理 + 命中率统计）
5. `infrastructure/tool/EastMoneyAdapter` —— 东财行情 API 适配器（WebClient + 2s 连接超时 + 3s 读取超时 + 降级兜底）
6. `infrastructure/tool/MarketDataGateway` —— 编排中心（限流→缓存→Adapter→降级漏斗）

### 重构组件（1 个）
- `application/tool/StockTools` —— 注入 MarketDataGateway，保持 `@Tool` 签名不变（DataFetchTaskHandler 无感）

### 编排顺序（漏斗）
```
StockTools → MarketDataGateway
               → 第1道：RateLimiter.tryAcquire()（超限→降级）
               → 第2道：Cache.get()（命中→直接返回）
               → 第3道：Adapter.fetch()（真正 I/O）
               → 第4道：降级兜底（lastKnownGood / Mock）
```

### 数据流
```
LLM → tool_use(getStockPrice) → StockTools.getStockPrice()  [签名不变]
                              → MarketDataGateway.getPrice()
                                  → RateLimiter 限流
                                  → Cache 命中→直接返回
                                  → EastMoneyAdapter HTTP（带 timeout）
                                  → 成功→解析校验→写缓存→返回
                                  → 失败→fallback 缓存/Mock→返回
                              → 写 State.TOOL_DATA
```

## 关键设计决策
- **不新增第三方依赖**：限流/缓存全部 JDK 原生（AtomicLong + ConcurrentHashMap + ScheduledExecutorService）
- **快速拒绝（非阻塞）**：RateLimiter 超限立刻返回 false，Gateway 走降级，不卡 Node 线程
- **degraded 标记**：降级数据标记 degraded=true，让 LLM 知道数据可信度
- **lastKnownGood**：Adapter 失败时优先返回上次成功数据（比全新 Mock 更合理）

## Technical Notes
- LangChain4j: 1.20.0
- LangGraph4j: 1.8.26
- Spring Boot: 4.1.1
- Java: 17
- 新增依赖：无

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
