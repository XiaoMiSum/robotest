# 详细设计

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [01-system-management](01-system-management/01-readme.md)（模块：系统管理） | V1.0 | 2026-09-23 | 起草中 |
| [02-space-management](02-space-management/01-readme.md)（模块：空间管理） | V1.0 | 2026-09-23 | 已发布 |
| [03-function-test](03-function-test/01-readme.md)（模块：功能测试） | V1.0 | 2026-09-23 | 已发布 |
| [04-bug](04-bug/01-readme.md)（模块：缺陷管理） | V1.0 | 2026-09-23 | 起草中 |
| [05-api-test](05-api-test/01-readme.md)（模块：接口测试） | V1.0 | 2026-09-23 | 起草中 |
| [06-ai](06-ai/01-readme.md)（模块：AI 能力） | V1.0 | 2026-09-23 | 起草中 |
| [99-common](99-common/01-readme.md)（模块：公共与通用） | V1.0 | 2026-09-24 | 起草中 |

> 接口示例统一遵循 `docs/00-spec/20-contracts/01-api.md`：响应使用 `Result`，字段为 `code`、`msg`、`data`；分页使用 `pageNo/pageSize` 和 `list/total`；错误码以 10 位 `ErrorCodeConstants` 为准。接口示例若仅展示 `data`，不重复展示外层响应。
>
> 阅读顺序建议：先读《API 测试基础设施》（公共数据表、执行引擎、格式转换和错误码号段），再读《项目模块》（统一模块树、错误码号段）与《文档管理》（用例资产、错误码号段），其余文档均引用其公共约定。
> 阅读顺序建议：先读《AI 基础设施》（公共数据表、SSE 帧格式、错误码号段、网关/限流/任务框架），再读《WebSocket 实时通信》（连接、Ticket、房间、帧和生命周期）和《时间契约双方案比较》（DEC-005 比较材料），其余文档均引用其公共约定。
