# Engineering Evolution Prompt — Day 15

> Capability: **Research RAG Capability**

## 今日目标

接入研报私有知识库，用向量/关键词混合检索补充研究上下文。

## 本日工程边界

- **Before**：Day 14 Agent 可协同工具和人工。
- **After**：Research Node 能基于可追溯资料增强上下文，并把证据带入策略决策。
- **建议触点**：`document ingestion + vector store + retriever + citations`
- **核心概念**：Embedding、VectorStore、Retriever、Hybrid Search、chunk、citation
- **Java Mental Model**：Elasticsearch inverted index + document repository

## 本阶段强化

本阶段重点：不确定性必须转化为可解释诊断、可操作 Action 和可恢复 HITL 状态；高风险动作不允许模型直接落地。

## 0. 角色与任务边界

你现在不是在创建一个独立 Demo。
你正在维护并持续演进唯一运行工程：

`/lianghua/agent`

本次任务必须在现有 Day 1~14 工程能力基础上做**增量演进**，而不是重建第二套 Runtime。

## 1. 执行前必须建立上下文

开始编码前必须依次检查：

1. `/lianghua/agent/constitution.md`
2. 当前 Git status / diff（若目录不是 Git 仓库，则说明并继续）
3. `/lianghua/agent/src/main/java/`
4. `/lianghua/agent/src/test/java/`
5. 当前 `.specs/` 中 Day 1~Day 14 的 `spec.md/plan.md/tasks.md/checklist.md`
6. 当前已有模块、接口、State、Graph、Tool、LLM Gateway、Event 和持久化边界

在动代码前先输出“当前能力地图”：已有能力、可复用点、Day 15 插入点。

## 2. 纵向演进铁律

### 禁止

- 新建第二套 Agent Runtime
- 新建独立 Demo 工程
- 重复实现 LLM Client / Tool Runtime / State / Graph / MCP / RAG 基础设施
- 绕过已有 Application Service 直接在 Node 中调用第三方 SDK
- 为了实现今天功能进行无关大重构
- 用复制旧代码的方式“快速做一套新的”

### 必须

- 复用已有能力
- 明确今天新增的唯一 Capability
- 明确 Capability 插入现有架构的哪个位置
- 通过既有边界接入外部依赖
- 保持旧 Day 能力可运行
- 为新增能力补 Unit / Integration / Contract 测试

## 3. Before → Change → After

编码前必须明确写出：

**Before**：Day 1~Day 14 当前链路。

**Change**：今天新增 `Research RAG Capability`，说明新增 Node / Service / Adapter / Contract 中哪些最小元素。

**After**：完整的新链路，并标明复用的旧能力。

如果发现工程实际结构与假设不一致，先停下来说明差异和最小变更方案，不得自行推倒重来。

## 4. Spec Driven

严格读取：

`/lianghua/agent/.specs/day15_*/spec.md`
`/lianghua/agent/.specs/day15_*/plan.md`
`/lianghua/agent/.specs/day15_*/tasks.md`
`/lianghua/agent/.specs/day15_*/checklist.md`

执行顺序：

`Spec → Plan → Tasks → Code → Test → Checklist`

如果 Spec 与现有工程冲突，优先报告“现状 / Spec / 差异 / 最小修改方案”。

## 5. 回归保护

Day 15 完成后，必须至少回归检查 Day 1、Day 3、Day 4、Day 7 的核心能力；并根据前置依赖追加本阶段关键回归。

禁止因为今天功能通过而接受旧功能退化。

## 6. 测试与安全

- Unit Test 不调用真实 LLM / 外部金融接口
- 能使用 fixture/mock 的地方必须使用
- 外部 I/O 必须显式 timeout
- LLM 输出视为不可信输入
- Tool / persistence / side effect 必须有明确边界
- 高风险动作必须保留 policy / HITL 闸门

## 7. 完成汇报

最终必须输出：
1. 当前已有能力摘要
2. 本次新增 Capability
3. 新增/修改文件
4. 复用了哪些旧能力
5. Before → Change → After
6. 测试与 Regression 结果
7. 未解决风险
8. Git diff 摘要

只有 checklist 中的 DoD 全部满足，才能说“Day 15 完成”。
## 8. Day 15 特殊验收

RAG 返回结果必须携带可追溯 metadata/citation；Hybrid Search 不得成为另一个孤立 Demo，必须接入 Research Node/State。

## 9. Day-specific Definition of Done

- [ ] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [ ] 旧能力保持可用
- [ ] Spec / Plan / Tasks / Checklist 已更新
- [ ] 新增测试通过
- [ ] 关键历史能力 Regression 通过
- [ ] Git diff 可解释
