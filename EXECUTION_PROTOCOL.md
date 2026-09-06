# 20 天双轮执行协议

每天固定顺序：

1. `/learn/phaseX/dayN/README.md`：看 Mermaid、Java 类比、Debug 实验。
2. `/learn/prompts/dayNN-learning.md`：完成 Learning Gate。
3. 进入 `/agent/prompts/dayNN-engineering.md`，让 Claude/Cursor 读取现有工程并做纵向增量。
4. AI 执行 `.specs/dayNN_*/spec.md → plan.md → tasks.md`。
5. 跑测试 + Regression。
6. 完成 `.specs/dayNN_*/checklist.md`。
7. Git commit，形成一条 Capability 演进历史。

核心原则：**Learn 负责你会不会；Engineering Prompt 负责工程怎么长；Spec 负责设计可追溯。**
