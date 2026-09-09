# Spec — Day 04 / LangGraph4j StateGraph + QuantAgentState

> Capability: **Agent State & Graph Runtime**

## 1. 目标

把 Day 1~3 的"单次 LLM 调用"提升为**可编排的 StateGraph**，并定义强类型状态 `QuantAgentState`。

## 2. 设计决策

### 2.1 State 设计：强类型包装 + Map 存储

```java
public class QuantAgentState {
    private final Map<String,Object> data;   // 底层存储（支撑 Day 7 Checkpoint 序列化）
    // + 类型化 accessor
    public String symbol() { return (String) data.get("symbol"); }
    public String analysisResult() { return (String) data.get("analysisResult"); }
    ...
}
```

**为什么这样设计？**
- Map 存储 → Day 7 可直接序列化快照；Day 14 HITL 可开放修改任意字段
- 强类型 accessor → 编译期保护高频字段，减少 `（String）` 强转散落
- 不可变快照语义 → Node 返回增量 Map，框架合并成新 State

### 2.2 StateGraph 拓扑

```
START
  └→ analysisNode（调 LLM 分析，写 analysisResult）
       ├─[条件: needsTool=true]→ toolNode（调 @Tool，写 toolData）
       │                            └→ outputNode
       └─[条件: needsTool=false]→ outputNode（写 finalResult）
                                    └→ END
```

**节点职责**：
| Node | 读 | 写 | 复用旧能力 |
|------|---|---|-----------|
| `analysisNode` | symbol | analysisResult, needsTool | `StructuredAnalysisService` |
| `toolNode` | symbol | toolData | `StockTools` |
| `outputNode` | analysisResult, toolData | finalResult | 纯 Java 拼接 |

### 2.3 分层边界

```
interfaces/http  →  GraphController（新增）
                      ↓
graph/runtime    →  GraphRunner.invoke(state)（新增，统一执行入口）
                      ↓
graph/topology   →  QuantAgentStateGraph（新增，图定义）
                      ↓
graph/nodes      →  analysisNode / toolNode / outputNode（新增）
                      ↓
application/llm  →  StructuredAnalysisService（复用）
application/tool →  StockTools（复用）
                      ↓
infrastructure/llm → QuantLlmService / LlmConfiguration（复用）
```

**铁律**：Node 不直接调第三方 SDK，通过既有 Application Service 接入。

## 3. 新增文件清单

| 文件 | 包 | 职责 |
|------|---|------|
| `QuantAgentState.java` | domain/state | 强类型 State，Map 底层 + accessor |
| `QuantAgentStateGraph.java` | graph/topology | StateGraph 定义（节点 + 边） |
| `AnalysisNode.java` | graph/nodes | 分析节点 |
| `ToolNode.java` | graph/nodes | 工具节点 |
| `OutputNode.java` | graph/nodes | 输出节点 |
| `GraphRunner.java` | graph/runtime | 统一执行入口（compile + invoke） |
| `GraphController.java` | interfaces/http | HTTP 端点 |

## 4. 测试策略

| 测试 | 类型 | 覆盖 |
|------|------|------|
| `QuantAgentStateTest` | Unit | accessor / merge / 序列化 |
| `QuantAgentStateGraphTest` | Unit | 图编译成功、条件边路由正确 |
| `AnalysisNodeTest` | Unit | mock StructuredAnalysisService，验证写 State |
| `Day04GraphRuntimeTest` | Integration | 全链路 invoke，mock LLM |

**回归保护**：Day 1（流式）、Day 3（工具调用）测试必须仍通过。

## 5. 风险与权衡

| 风险 | 缓解 |
|------|------|
| Map 存储丢失类型安全 | accessor 保护高频字段；低频字段文档化 |
| Node 间 key 拼写错误 | 用常量类 `StateKeys` 集中管理 key 名 |
| 条件边路由错误 | 单元测试覆盖每个分支 |
