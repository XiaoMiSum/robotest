# 软件测试平台——测试报告交互设计总览

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

> 本文档为接口测试域交互设计分册：测试报告（列表、详情与分享）的前端交互设计。
> 导航框架与色彩体系见 `docs/05-interaction-design/02-global-navigation.md`、`docs/05-interaction-design/03-visual-design.md`。

---

## 1. 概述

接口测试域新增测试报告能力。本文档覆盖测试报告页的交互设计：

1. **测试报告页**——场景执行结果的列表、详情与分享（承接 SRS 3.5）。

### 1.1 菜单与路由

**项目模式**（接口测试侧边栏）新增「测试报告」：

```
┌──────────────────────────────────┐
│  接口测试（顶部菜单入口）          │
│  ─────────────────────────       │
│  快速调试     /workspace/projects/api-testing?tab=debug
│  接口管理     /workspace/projects/api-testing?tab=interfaces
│  Mock 服务    /workspace/projects/api-testing?tab=mocks
│  测试场景     /workspace/projects/api-testing?tab=scenes
│  接口测试报告 /workspace/projects/api-testing?tab=reports   ← 本文档
│  定时任务     /workspace/projects/api-testing?tab=schedules
│  ───────────────────────────────
│  项目设置（平台级 · 按业务域过滤）
│   ├ 环境管理           /workspace/projects/settings/environments
│   └ 全局资产           /workspace/projects/settings/assets
└──────────────────────────────────┘
```

| 页面 | 路由 | 模式 | 权限 |
| ---- | ---- | ---- | ---- |
| 测试报告列表页 | `/workspace/projects/api-testing?tab=reports` | 项目模式 | 执行者本人或项目维护者 |
| 测试报告详情页 | `/workspace/projects/reports/:id` | 项目模式 | 执行者本人或项目维护者 |

> 上表路由为前端路由示意，与后端 API 路径相互独立。

### 1.2 权限边界

- 报告按项目隔离；查看/分享为执行者本人或项目维护者权限，删除为项目维护者权限。

---


## 2. 通用交互模式

本页涉及的通用交互模式遵循 `docs/05-interaction-design/01-readme.md` 第 4 章的约定：

- **弹窗类型**：确认弹窗用于删除报告、批量删除、清空操作；分享弹窗用于报告分享链接生成与复制;
- **加载与错误状态**：列表首次进入表格骨架屏，筛选/搜索时顶部细进度条；表单提交按钮 loading + 禁用；加载失败展示错误区 + [重试]；空态统一为插画 + 主文案 + 说明文案 + 主操作按钮，筛选无结果时提供 [清除筛选]；
- **状态徽标色彩**：统一使用 `docs/05-interaction-design/03-visual-design.md` 定义的语义色（通过/成功绿、失败/危险红、部分通过/警告黄、跳过/未执行灰、进行中/待同步蓝）；
- **响应式处理**：报告详情执行概览统计卡在小屏幕下自适应换列；场景卡的 mini 统计在小屏幕下移至头部下方占满一行；表单抽屉在小屏幕下全屏展示。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `40-report-ui-overview.md` | 前言、1. 概述、3. 通用交互模式 |
| 报告列表页 | `41-report-ui-list.md` | 2.1 报告列表页 |
| 报告详情页 | `42-report-ui-detail.md` | 2.2 报告详情页 |
| 分享访问页 | `43-report-ui-share.md` | 2.3 分享访问页 |
