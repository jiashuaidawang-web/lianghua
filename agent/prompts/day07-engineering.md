# Engineering Evolution Prompt — Day 07

> Capability: **Durable Execution Capability**

## 今日目标

把 Agent 执行状态做成可持久化快照，支持故障恢复和线程级继续执行。

## 本日工程边界

- **Before**：Day 6 的图只能依赖进程内内存 State。
- **After**：Agent 可以保存/恢复执行上下文，并在进程重启后继续。
- **建议触点**：`checkpoint + persistence adapter + execution context`
- **核心概念**：Checkpoint、Thread/Execution ID、Snapshot、Resume
- **Java Mental Model**：WAL/Saga snapshot + workflow recovery

## 本阶段强化

本阶段重点：把 StateGraph 当作唯一 Agent Runtime。所有动态流程都通过 State + Node + Edge 表达，禁止把流程重新塞回一个巨型 Service。

## 0. 角色与任务边界

你现在不是在创建一个独立 Demo。
你正在维护并持续演进唯一运行工程：

`/lianghua/agent`

本次任务必须在现有 Day 1~6 工程能力基础上做**增量演进**，而不是重建第二套 Runtime。

## 1. 执行前必须建立上下文

开始编码前必须依次检查：

1. `/lianghua/agent/constitution.md`
2. 当前 Git status / diff（若目录不是 Git 仓库，则说明并继续）
3. `/lianghua/agent/src/main/java/`
4. `/lianghua/agent/src/test/java/`
5. 当前 `.specs/` 中 Day 1~Day 6 的 `spec.md/plan.md/tasks.md/checklist.md`
6. 当前已有模块、接口、State、Graph、Tool、LLM Gateway、Event 和持久化边界

在动代码前先输出“当前能力地图”：已有能力、可复用点、Day 07 插入点。

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

**Before**：Day 1~Day 6 当前链路。

**Change**：今天新增 `Durable Execution Capability`，说明新增 Node / Service / Adapter / Contract 中哪些最小元素。

**After**：完整的新链路，并标明复用的旧能力。

如果发现工程实际结构与假设不一致，先停下来说明差异和最小变更方案，不得自行推倒重来。

## 4. Spec Driven

严格读取：

`/lianghua/agent/.specs/day07_*/spec.md`
`/lianghua/agent/.specs/day07_*/plan.md`
`/lianghua/agent/.specs/day07_*/tasks.md`
`/lianghua/agent/.specs/day07_*/checklist.md`

执行顺序：

`Spec → Plan → Tasks → Code → Test → Checklist`

如果 Spec 与现有工程冲突，优先报告“现状 / Spec / 差异 / 最小修改方案”。

## 5. 回归保护

Day 07 完成后，必须至少回归检查 Day 1、Day 3、Day 4、Day 7 的核心能力；并根据前置依赖追加本阶段关键回归。

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

只有 checklist 中的 DoD 全部满足，才能说“Day 07 完成”。
## 8. Day 07 特殊验收

Checkpoint 必须绑定稳定的 execution/thread identity，并验证“进程中断 → 恢复 → 继续执行”。

## 9. Day-specific Definition of Done

- [ ] 今日 Capability 已插入现有 `/lianghua/agent`，不是独立 Demo
- [ ] 旧能力保持可用
- [ ] Spec / Plan / Tasks / Checklist 已更新
- [ ] 新增测试通过
- [ ] 关键历史能力 Regression 通过
- [ ] Git diff 可解释
