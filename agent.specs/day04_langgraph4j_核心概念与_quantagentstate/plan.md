# Plan — Day 04

## 阶段 1：State 定义
1. 创建 `domain/state/StateKeys.java` — 集中管理 key 常量
2. 创建 `domain/state/QuantAgentState.java` — Map 底层 + 类型化 accessor + merge 方法

## 阶段 2：图拓扑
1. 创建 `graph/nodes/AnalysisNode.java` — 注入 `StructuredAnalysisService`
2. 创建 `graph/nodes/ToolNode.java` — 注入 `StockTools`
3. 创建 `graph/nodes/OutputNode.java` — 纯 Java 拼接
4. 创建 `graph/topology/QuantAgentStateGraph.java` — addNode + addEdge + addConditionalEdge + compile

## 阶段 3：运行入口
1. 创建 `graph/runtime/GraphRunner.java` — compile + invoke 封装
2. 创建 `interfaces/http/GraphController.java` — `GET /api/v1/graph/analyze?symbol=...`

## 阶段 4：测试与回归
1. `QuantAgentStateTest` — accessor / merge
2. `QuantAgentStateGraphTest` — 条件边路由
3. `AnalysisNodeTest` — mock 验证
4. 回归：Day 1 + Day 3 测试
5. 更新 checklist，git commit
