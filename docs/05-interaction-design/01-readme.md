# 交互设计

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [01-system-management](01-system-management/01-readme.md)（模块：系统管理） | V1.0 | 2026-09-23 | 起草中 |
| [02-space-management](02-space-management/01-readme.md)（模块：空间管理） | V1.0 | 2026-09-23 | 已发布 |
| [03-function-testing](03-function-testing/01-readme.md)（模块：功能测试） | V1.0 | 2026-09-23 | 起草中 |
| [04-bug-management](04-bug-management/01-readme.md)（模块：缺陷管理） | V1.0 | 2026-10-10 | 起草中 |
| [05-api-testing](05-api-testing/01-readme.md)（模块：接口测试） | V1.0 | 2026-09-23 | 起草中 |
| [06-requirement-management](06-requirement-management/01-readme.md)（模块：需求管理） | V1.0 | 2026-10-03 | 起草中 |
| [07-ai-capability](07-ai-capability/01-readme.md)（模块：AI 能力） | V1.0 | 2026-10-03 | 起草中 |
| [02-global-navigation](02-global-navigation.md)（平台级：全局导航与菜单交互） | V1.0 | 2026-09-23 | 已发布 |
| [03-visual-design](03-visual-design.md)（平台级：视觉设计） | V1.0 | 2026-09-23 | 已发布 |
| [04-trace-matrix-ui](04-trace-matrix-ui.md)（平台级：追溯矩阵） | V1.0 | 2026-10-03 | 起草中 |

> 阅读顺序建议：接口管理 → 快速调试 → 测试场景 → 环境管理 → 定时任务 → Mock服务 → 公共组件 → 测试报告 → 项目设置。导航框架与色彩体系见《全局导航与菜单交互系统设计》（项目设置框架见其 3.5）《视觉设计》。
>
> 接口交互示例遵循 `docs/00-spec/20-contracts/01-api.md`：分页使用 `pageNo/pageSize` 和 `list/total`，错误码以 10 位 `ErrorCodeConstants` 为准；实时连接遵循 `docs/04-detailed-design/03-realtime-websocket.md` 和 `docs/00-spec/20-contracts/03-realtime-protocol.md`。
