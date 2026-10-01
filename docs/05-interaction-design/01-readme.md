# 交互设计

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [01-system-management](01-system-management/01-readme.md)（模块：系统管理） | V1.0 | 2026-09-23 | 起草中 |
| [02-space-management](02-space-management/01-readme.md)（模块：空间管理） | V1.0 | 2026-09-23 | 已发布 |
| [03-function-test](03-function-test/01-readme.md)（模块：功能测试） | V1.0 | 2026-09-23 | 起草中 |
| [04-bug](04-bug/01-readme.md)（模块：缺陷管理） | V1.0 | 2026-09-23 | 起草中 |
| [05-api-test](05-api-test/01-readme.md)（模块：接口测试） | V1.0 | 2026-09-23 | 起草中 |
| [06-ai](06-ai/01-readme.md)（模块：AI 能力） | V1.0 | 2026-09-23 | 起草中 |
| [07-project-settings](07-project-settings/01-readme.md)（模块：项目设置） | V1.0 | 2026-09-23 | 起草中 |
| [09-global-assistant](09-global-assistant/01-readme.md)（模块：全局智能助手） | V1.0 | 2026-09-23 | 起草中 |
| [99-common](99-common/01-readme.md)（模块：公共与通用） | V1.0 | 2026-09-23 | 已发布 |

> 阅读顺序建议：接口管理 → 快速调试 → 测试场景 → 环境管理 → 定时任务 → Mock服务 → 公共组件 → 测试报告 → 项目设置。导航框架与色彩体系见《全局导航与菜单交互系统设计》（项目设置框架见其 3.5）《视觉设计》。
> 阅读顺序建议：先读《AI基础设施与管理端页面交互设计》（第 2 章为 AI 通用交互规范，其余 4 份均引用）。导航框架与色彩体系见《全局导航与菜单交互系统设计》《视觉设计》。
>
> 接口交互示例遵循 `docs/00-spec/20-contracts/01-api.md`：分页使用 `pageNo/pageSize` 和 `list/total`，错误码以 10 位 `ErrorCodeConstants` 为准；实时连接遵循 `docs/04-detailed-design/99-common/78-realtime-websocket.md` 和 `docs/00-spec/20-contracts/03-realtime-protocol.md`。
