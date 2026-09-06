# Engineering Evolution Prompt — Day 10

> Capability: **DSH Diff Audit Capability**

## 今日目标

自动对比需求、现有因子库和 DB Schema，输出可审计 Gap Matrix。

## 本日工程边界

- **Before**：Day 1~9 已提供 LLM、State、Planner、Tools、MCP 等基础能力。
- **After**：Agent 能在正式执行前识别“需求 vs 现有能力”的落差，并形成结构化审计结果。
- **建议触点**：`diff-audit node + domain model + existing tool/mcp reuse`
- **核心概念**：Requirement model、Capability inventory、Diff、Gap、severity、evidence
- **Java Mental Model**：Git Diff + DB migration/schema audit + code review

## 本阶段强化

本阶段重点：外部世界通过 Tool / MCP / Adapter 进入 Agent；任何第三方数据和代码执行都必须经过工程边界和审计。

## 0. 角色与任务边界

你现在不是在创建一个独立 Demo。
你正在维护并持续演进唯一运行工程：

`/lianghua/agent`

本次任务必须在现有 Day 1~9 工程能力基础上做**增量演进**，而不是重建第二套 Runtime。

## 1. 执行前必须建立上下文

开始编码前必须依次检查：

1. `/lianghua/agent/constitution.md`
2. 当前 Git status / diff（若目录不是 Git 仓库，则说明并继续）
3. `/lianghua/agent/src/main/java/`
4. `/lianghua/agent/src/test/java/`
5. 当前 `.specs/` 中 Day 1~Day 9 的 `spec.md/plan.md/tasks.md/checklist.md`
6. 当前已有模块、接口、State、Graph、Tool、LLM Gateway、Event 和持久化边界

在动代码前先输出“当前能力地图”：已有能力、可复用点、Day 10 插入点。

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

**Before**：Day 1~Day 9 当前链路。

**Change**：今天新增 `DSH Diff Audit Capability`，说明新增 Node / Service / Adapter / Contract 中哪些最小元素。

**After**：完整的新链路，并标明复用的旧能力。

如果发现工程实际结构与假设不一致，先停下来说明差异和最小变更方案，不得自行推倒重来。

## 4. Spec Driven

严格读取：

`/lianghua/agent/.specs/day10_*/spec.md`
`/lianghua/agent/.specs/day10_*/plan.md`
`/lianghua/agent/.specs/day10_*/tasks.md`
`/lianghua/agent/.specs/day10_*/checklist.md`

执行顺序：

`Spec → Plan → Tasks → Code → Test → Checklist`

如果 Spec 与现有工程冲突，优先报告“现状 / Spec / 差异 / 最小修改方案”。

## 5. 回归保护

Day 10 完成后，必须至少回归检查 Day 1、Day 3、Day 4、Day 7 的核心能力；并根据前置依赖追加本阶段关键回归。

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

只有 checklist 中的 DoD 全部满足，才能说“Day 10 完成”。
## 8. Day 10 特殊验收

> **不要创建 DiffAuditDemo。必须在当前 Agent Runtime 中新增 Diff Audit Capability。**

执行前必须明确复用：Day 4 State、Day 5 Planner、Day 8 Finance Tool、Day 9 MCP（按实际工程可用能力取舍）。

Before：`User → Planner → Existing Agent Flow`

After：`User → Planner → DiffAuditNode → Existing Agent Flow`（最终以实际 Graph 为准）。

Gap Matrix 至少包含：`gap_id / category / current / required / severity / evidence / recommended_action`。

## 9. Day-specific Definition of Done

- [ ] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [ ] 旧能力保持可用
- [ ] Spec / Plan / Tasks / Checklist 已更新
- [ ] 新增测试通过
- [ ] 关键历史能力 Regression 通过
- [ ] Git diff 可解释
