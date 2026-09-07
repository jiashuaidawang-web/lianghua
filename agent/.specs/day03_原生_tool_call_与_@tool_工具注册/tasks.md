# Day 3 Tasks

- [x] Confirm current API from official docs/Javadoc.
- [x] Implement domain contract.
- [x] Implement infrastructure adapter/node/tool.
- [x] Wire the application path.
- [x] Add normal-path JUnit 5 test.
- [x] Add error/boundary JUnit 5 test.
- [x] Run `mvn test`.
- [x] Update README/checklist with observed results.

## 实现说明

### 新增文件
- `application/tool/StockTools.java` —— @Tool 工具类（getStockPrice、getFundamental）
- `application/llm/StockAnalysisWithToolAiService.java` —— 支持工具调用的 AiService 接口
- `interfaces/http/StockToolController.java` —— 工具调用端点 `/api/v1/tool/analyze/{symbol}`
- `test/.../Day03StockToolsTest.java` —— 工具方法单元测试

### 修改文件
- `infrastructure/llm/AiServicesConfiguration.java` —— 新增 `stockAnalysisWithToolAiService` Bean，注入 `.tools(stockTools)`

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
