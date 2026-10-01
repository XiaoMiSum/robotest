# 交互设计

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [01-system-management](01-system-management/01-readme.md)（模块：系统管理） | V1.0 | 2026-09-23 | 起草中 |
| [02-space-management](02-space-management/01-readme.md)（模块：空间管理） | V1.0 | 2026-09-23 | 已发布 |
| [03-project-workspace](03-project-workspace/01-readme.md)（模块：项目工作台） | V1.0 | 2026-09-23 | 起草中 |
| [04-api-infrastructure](04-api-infrastructure/01-readme.md)（模块：接口测试基础设施） | V1.0 | 2026-09-23 | 起草中 |
| [05-interface-management](05-interface-management/01-readme.md)（模块：接口管理） | V1.0 | 2026-09-23 | 起草中 |
| [06-quick-debug](06-quick-debug/01-readme.md)（模块：快速调试） | V1.0 | 2026-09-23 | 起草中 |
| [07-test-scenario](07-test-scenario/01-readme.md)（模块：测试场景） | V1.0 | 2026-09-23 | 起草中 |
| [08-environment](08-environment/01-readme.md)（模块：环境管理） | V1.0 | 2026-09-23 | 起草中 |
| [09-mock-service](09-mock-service/01-readme.md)（模块：Mock 服务） | V1.0 | 2026-09-23 | 起草中 |
| [10-scheduled-task](10-scheduled-task/01-readme.md)（模块：定时任务） | V1.0 | 2026-09-23 | 起草中 |
| [11-test-report](11-test-report/01-readme.md)（模块：测试报告） | V1.0 | 2026-09-23 | 起草中 |
| [12-project-settings](12-project-settings/01-readme.md)（模块：项目设置） | V1.0 | 2026-09-23 | 起草中 |
| [13-ai-infrastructure](13-ai-infrastructure/01-readme.md)（模块：AI 基础设施） | V1.0 | 2026-09-23 | 起草中 |
| [14-ai-case-generation](14-ai-case-generation/01-readme.md)（模块：智能用例生成） | V1.0 | 2026-09-23 | 起草中 |
| [15-ai-review](15-ai-review/01-readme.md)（模块：AI 评审与覆盖度分析） | V1.0 | 2026-09-23 | 起草中 |
| [16-bug-ai-analysis](16-bug-ai-analysis/01-readme.md)（模块：缺陷智能分析） | V1.0 | 2026-09-23 | 起草中 |
| [18-global-assistant](18-global-assistant/01-readme.md)（模块：全局智能助手） | V1.0 | 2026-09-23 | 起草中 |
| [99-common](99-common/01-readme.md)（模块：公共与通用） | V1.0 | 2026-09-23 | 已发布 |

> 阅读顺序建议：接口管理 → 快速调试 → 测试场景 → 环境管理 → 定时任务 → Mock服务 → 公共组件 → 测试报告 → 项目设置。导航框架与色彩体系见《全局导航与菜单交互系统设计》（项目设置框架见其 3.5）《视觉设计》。
> 阅读顺序建议：先读《AI基础设施与管理端页面交互设计》（第 2 章为 AI 通用交互规范，其余 4 份均引用）。导航框架与色彩体系见《全局导航与菜单交互系统设计》《视觉设计》。
>
> 接口交互示例遵循 `docs/00-spec/20-contracts/01-api.md`：分页使用 `pageNo/pageSize` 和 `list/total`，错误码以 10 位 `ErrorCodeConstants` 为准；实时连接遵循 `docs/04-detailed-design/99-common/78-realtime-websocket.md` 和 `docs/00-spec/20-contracts/03-realtime-protocol.md`。
