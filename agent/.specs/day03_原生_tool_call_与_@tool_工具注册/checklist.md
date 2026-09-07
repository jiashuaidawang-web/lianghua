# Day 3 Checklist

- [x] Function works on the happy path.
- [x] Error path is covered.
- [x] State/DTO/Tool result is validated.
- [ ] No real credentials committed.
- [x] No beta/snapshot dependency added.
- [x] Unit tests pass.
- [x] Manual verification completed where applicable.

## 测试证据（2026-09-07）

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- Day 1 回归：`Day01QuantLlmServiceTest` 2/2 通过
- Day 2 回归：`Day02StructuredAnalysisServiceTest` 4/4 通过
- Day 3 新增：`Day03StockToolsTest` 2/2 通过
  - `shouldReturnStockPrice`：工具返回正确价格 JSON
  - `shouldReturnFundamental`：工具返回正确基本面 JSON

## 工程决策记录

### 工具返回类型选择 String
- **决策**：`@Tool` 方法返回 `String`，而非强类型 DTO
- **原因**：String 直接作为工具结果喂回 LLM，无需 JSON 序列化；LLM 直接理解文本内容
- **后续**：如果需要强类型，可改为返回 POJO，框架会自动序列化为 JSON

### 工具数据当前为 Mock
- `getStockPrice` 和 `getFundamental` 返回硬编码值
- 后续接入真实行情 API 时，只需修改 `StockTools` 方法体，不影响接口和调用链路

## ⚠️ 未解决风险

- `application.yml` 中存在硬编码 API Key（同 Day 1），建议迁移为 `${QUANT_LLM_API_KEY}` 环境变量占位符。
- 工具当前返回 Mock 数据，尚未接入真实行情 API。
- 未测试"LLM 自主决定调工具"的完整链路（需要真实 LLM 调用，当前测试只验证工具方法本身）。

## Vertical Evolution Contract

### Capability
Tool Calling Capability

### Before
Day 1~2 只有 LLM 文本/结构化输出能力。

### After
Agent 可以通过注册的 Java Tool 获取外部数据，不允许模型直接产生副作用。

### Reuse-first
本 Day 必须优先复用 Day 1~Day 2 的既有 Runtime、State、Graph、Tool、Adapter、Event、测试基建；禁止创建第二套 Agent Runtime。

### Regression
完成后必须验证历史能力不退化，并把关键回归证据记录到 `checklist.md`。
