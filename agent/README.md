# lianghua-agent

唯一 Java 运行工程。这里的目标是：**每天在当前工程上增加一个生产级 Capability**。

## 工程演进纪律

- `constitution.md`：全局不可违背的架构/安全/测试规则。
- `prompts/dayXX-engineering.md`：当天纵向演进指令，防止 AI 造第二套 Demo。
- `.specs/dayXX_*/`：当天 4-Spec 设计记忆。
- `src/`：唯一真实运行代码。

## Coding 执行前

1. 读 constitution。
2. 读历史 Spec 和当前代码。
3. 输出 Before → Change → After。
4. 找到复用点和最小插入点。
5. 执行 Tasks。
6. 跑新测试 + Regression。
7. 更新 Checklist。

## 运行

```bash
mvn clean test
mvn spring-boot:run
```

真实 API Key 必须通过环境变量注入，禁止进入源码或 Git。
