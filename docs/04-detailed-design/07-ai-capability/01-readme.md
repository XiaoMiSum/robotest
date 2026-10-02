# 详细设计（AI 能力模块）

| 文档 | 版本 | 日期 | 状态 |
| ---- | ---- | ---- | ---- |
| [02-ai-infra-overview](02-ai-infra-overview.md)（总册：AI 底座——数据设计、配置与用量、任务引擎、通用约定） | V1.0 | 2026-10-02 | 起草中 |
| [03-ai-generation](03-ai-generation.md)（分册：AI+需求 生成链） | V1.0 | 2026-10-02 | 起草中 |
| [04-ai-assistant](04-ai-assistant.md)（分册：智能助手） | V1.0 | 2026-10-02 | 起草中 |
| [05-ai-defect-analysis](05-ai-defect-analysis.md)（分册：缺陷分析） | V1.0 | 2026-10-02 | 起草中 |
| [06-ai-assisted-features](06-ai-assisted-features.md)（分册：辅助功能） | V1.0 | 2026-10-02 | 起草中 |

> 本目录为 AI 能力模块的详细设计分册；模块入口与阅读顺序见上级索引 `docs/04-detailed-design/01-readme.md`。
> **阅读顺序**：先读总册（10 张 AI 表 DDL、统一任务资源、配置与用量接口、错误码分段），再按能力域读对应分册；分册的错误码、任务类型与产物结构以总册为公共约定，不重复定义。
> 追溯矩阵（`trace_` 域 DDL 与矩阵 / 覆盖 / 影响接口）为平台级公共设计，见 `docs/04-detailed-design/05-trace-matrix.md`。
