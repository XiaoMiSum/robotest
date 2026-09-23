# 软件测试平台——（总览分册）

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

> 本文档为接口测试域交互设计分册：Mock 服务管理（规则管理、调试）的前端交互设计。
> 导航框架与色彩体系见 `docs/05-interaction-design/02-global-navigation.md`、`docs/05-interaction-design/03-visual-design.md`。

---

## 1. 概述

接口测试域新增 Mock 服务能力。本文档覆盖两部分交互设计：

1. **Mock 规则管理页**——Mock 定义 CRUD、启停、命中统计、地址复制（承接 SRS 3.3）；
2. **Mock 调试页**——模拟请求验证匹配与响应是否符合预期（承接 SRS 3.3）。

### 1.1 菜单与路由

**项目模式**（接口测试侧边栏）新增「Mock 服务」：

```
┌──────────────────────────────────┐
│  接口测试（顶部菜单入口）          │
│  ─────────────────────────       │
│  快速调试     /workspace/projects/api-testing?tab=debug
│  接口管理     /workspace/projects/api-testing?tab=interfaces
│  Mock 服务    /workspace/projects/api-testing?tab=mocks   ← 本文档
│  测试场景     /workspace/projects/api-testing?tab=scenes
│  接口测试报告 /workspace/projects/api-testing?tab=reports
│  定时任务     /workspace/projects/api-testing?tab=schedules
│  ═════════════════════════════
│  项目设置（平台级 · 按业务域过滤）
│   ├ 环境管理           /workspace/projects/settings/environments
│   └ 全局资产           /workspace/projects/settings/assets
└──────────────────────────────────┘
```

| 页面 | 路由 | 模式 | 权限 |
| ---- | ---- | ---- | ---- |
| Mock 规则管理页 | `/workspace/projects/api-testing?tab=mocks` | 项目模式 | 项目成员查看；项目维护者维护 |
| Mock 调试页 | `/workspace/projects/mock-debug` | 项目模式 | 项目成员 |

> 上表路由为前端路由示意，与后端 API 路径相互独立。

### 1.2 权限边界

- Mock 规则归属项目，按项目隔离；查看/访问 Mock 地址为项目成员权限，创建/编辑/启停/删除为项目维护者权限（SRS 附录 B）。

---


## 4. 通用交互模式

本页涉及的通用交互模式遵循 `docs/05-interaction-design/01-readme.md` 第 3 章的约定：

- **弹窗类型**：确认弹窗用于停用/删除 Mock、重置命中统计、批量启停、清空调试记录；表单抽屉用于 Mock 规则新建/编辑（720px）；
- **加载与错误状态**：列表首次进入表格骨架屏，筛选/搜索时顶部细进度条；表单提交按钮 loading + 禁用；行内操作按钮 loading 防重复；加载失败展示错误区 + [重试]；空态统一为插画 + 主文案 + 说明文案 + 主操作按钮，筛选无结果时提供 [清除筛选]；
- **状态徽标色彩**：统一使用 `docs/05-interaction-design/03-visual-design.md` 定义的语义色（通过/成功绿、失败/危险红、部分通过/警告黄、跳过/未执行灰、进行中/待同步蓝）；
- **响应式处理**：Mock 调试页左右分栏在小屏幕下改为上下堆叠；表单抽屉在小屏幕下全屏展示。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `36-mock-ui-overview.md` | 前言、1. 概述、4. 通用交互模式 |
| Mock 规则管理页 | `37-mock-ui-rules.md` | 2. Mock 规则管理页 |
| Mock 调试页 | `38-mock-ui-debug.md` | 3. Mock 调试页 |
