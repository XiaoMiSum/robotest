# 详细设计（执行端模块）

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [02-runner-core](02-runner-core.md)（分册：Runner 核心与通道协议） | V1.0 | 2026-10-14 | 起草中 |

> 本目录为执行端（Runner）模块的详细设计分册；Runner 为独立顶层目录 `runner/`（与 `web/`、`server/` 并列）承载的哑执行面组件，对应概设 `docs/02-high-level-design/03-function-testing/03-hld-ai-ui-testing.md` 第 2.1 节。
> **服务端 ↔ 执行端通道协议的单一事实源在本模块**：AI 执行服务侧分册只消费协议帧与原因码，不重复定义；与 `docs/04-detailed-design/03-realtime-websocket.md`（文档协作 WS 单一事实源）同一约定。
