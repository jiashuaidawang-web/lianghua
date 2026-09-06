# Engineering Evolution Prompt 使用说明

这里的 20 个 Prompt 专门用于让 Claude/Cursor **增量演进同一个 `/lianghua/agent`**。

每次执行前，AI 必须先读 `constitution.md`、当前工程、历史 Specs 和 Git diff，然后回答 Before → Change → After，再编码。

目标不是“20 个 Demo”，而是“20 次 Capability Commit”。
