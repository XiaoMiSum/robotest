# 软件测试平台——（总览分册）

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

> 本文档为接口测试域交互设计的第二部分：测试场景编排与步骤管理的前端交互设计。
> 环境管理交互设计见 `docs/05-interaction-design/01-readme.md`；导航框架与色彩体系见 `docs/05-interaction-design/02-global-navigation.md`、`docs/05-interaction-design/03-visual-design.md`。

---

## 1. 概述

### 1.1 模块范围

本文档覆盖接口测试业务域（承接 SRS 3.4 测试场景）的测试场景能力：

**测试场景**——场景 CRUD、步骤编排（http/jdbc 取样器）、场景参数、场景级处理器、场景执行。

### 1.2 侧边栏菜单与路由

项目模式顶部菜单在既有「功能测试」「缺陷管理」之外**新增「接口测试」入口**（增量见 `docs/05-interaction-design/02-global-navigation.md` 3.3）。接口测试页面内部采用侧边栏组织子模块：

```
┌──────────────────────────────────┐
│  接口测试（顶部菜单入口）          │
│  ─────────────────────────       │
│  快速调试     /workspace/projects/api-testing?tab=debug
│  接口管理     /workspace/projects/api-testing?tab=interfaces
│  Mock 服务    /workspace/projects/api-testing?tab=mocks
│  测试场景     /workspace/projects/api-testing?tab=scenes       ← 本文档第 2、3 章
│  接口测试报告 /workspace/projects/api-testing?tab=reports
│  定时任务     /workspace/projects/api-testing?tab=schedules
│  ═════════════════════════════
│  项目设置（平台级 · 按业务域过滤）
│   ├ 环境管理           /workspace/projects/settings/environments ← 见《环境管理交互设计》
│   └ 全局资产           /workspace/projects/settings/assets
└──────────────────────────────────┘
```

> 快速调试、接口管理、Mock 服务、报告、定时任务的交互设计见接口测试域交互设计第一部分及对应文档；「项目设置」分组（环境管理、全局资产）见对应分册与《项目设置交互设计》，本文档不重复描述。

### 1.3 场景与环境的业务关系

- **场景**是核心执行单元：由**步骤**（http/jdbc 取样器）按顺序编排而成，步骤可来源于接口定义（复制或链接引用）或自定义请求。
- **环境**为执行提供变量、Base URL、默认配置、数据源与全局处理器；场景执行时必须选择目标环境（可配置默认环境）。环境管理交互见 `docs/05-interaction-design/01-readme.md`。
- 变量解析优先级（低→高）：**内置函数 < 环境变量 < 场景参数 < 步骤级变量 < 步骤提取器变量 < 运行时覆盖**（见 `docs/04-detailed-design/01-readme.md` 4.1）。
- 配置合并优先级（低→高）：**环境默认配置 < 场景级配置 < 步骤级配置**，同名配置项以高优先级覆盖。

---


## 4. 通用交互模式

弹窗类型、加载状态、错误处理与拖拽约定等通用交互模式与《环境管理交互设计》第 4 章一致，本文档不重复描述：

- 弹窗类型汇总（表单 / 选择器 / 确认 / 执行 / 帮助 / 提示条）见 `docs/05-interaction-design/01-readme.md` 4.1；
- 加载状态（骨架屏、细进度条、按钮 loading、执行轮询、导入解析进度）见 4.2；
- 错误处理（校验错误、接口失败、业务错误码 7203/7401/7402/7403、乐观锁冲突 409、并发队列满、链接引用源缺失）见 4.3；
- 拖拽约定（步骤卡片、处理器列表、键值对排序、拖拽限制）见 4.4。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `30-scenario-ui-overview.md` | 前言、1. 概述、4. 通用交互模式 |
| 场景管理列表页 | `31-scenario-ui-list.md` | 2. 场景管理列表页 |
| 场景编排页 | `32-scenario-ui-orchestration.md` | 3. 场景编排页 |
