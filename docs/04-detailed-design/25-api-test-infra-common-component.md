# 软件测试平台——公共组件

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 公共组件接口（三级作用域）

### 1.1 分页查询公共组件列表

- **路径**：`GET /api/project/components?pageNo=1&pageSize=20&type=preprocessor&scope=project&keyword=Token`
- **筛选参数**：`type`（可选，preprocessor/postprocessor/validator/extractor）、`scope`（可选，project/workspace/global）、`keyword`（可选，名称模糊搜索）、`enabled`（可选）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "scope": "project",
      "type": "preprocessor",
      "name": "Token 预置",
      "description": "从环境变量获取 Token 并注入请求头",
      "sortOrder": 0,
      "config": "{\"testclass\":\"http\",\"config\":{\"method\":\"POST\",\"ref\":\"http_1\",\"path\":\"/token\"},\"extractors\":[],\"enabled\":true}",
      "enabled": true,
      "updatedAt": "2026-08-17 10:30:00"
    }
  ],
  "total": 8
}
```

### 1.2 创建公共组件

- **路径**：`POST /api/project/components`
- **请求体**：

```json
{
  "type": "preprocessor",
  "name": "Token 预置",
  "description": "从环境变量获取 Token 并注入请求头",
  "scope": "project",
"sortOrder": 0,
  "config": {
    "testclass": "http",
    "config": {
      "method": "POST",
      "ref": "http_1",
      "path": "/token",
      "http/2": false,
      "headers": {},
      "query": {},
      "data": {},
      "body": { "username": "${username}" }
    },
    "extractors": [],
    "enabled": true
  }
}
}
```

- **响应**：`{ "id": "018f..." }`

### 1.3 更新公共组件

- **路径**：`PUT /api/project/components/:id`
- **请求体**：同 1.2（`scope` 和 `type` 编辑态不可变更）。

### 1.4 启停公共组件

- **路径**：`PATCH /api/project/components/:id/toggle?enabled=false`
- **响应**：`{ "success": true }`

### 1.5 删除公共组件

- **路径**：`DELETE /api/project/components/:id`
- **响应**：`{ "success": true }`

### 1.6 批量启停

- **路径**：`PATCH /api/project/components/batch/toggle?enabled=false`
- **请求体**：`{ "ids": ["018f...", "018g..."] }`
- **响应**：`{ "success": true }`

### 1.7 批量删除

- **路径**：`DELETE /api/project/components/batch`
- **请求体**：`{ "ids": ["018f...", "018g..."] }`
- **响应**：`{ "success": true }`


## 2. 公共组件复制接口

### 2.1 复制公共组件

- **路径**：`POST /api/project/components/:id/copy`
- **说明**：将公共组件复制为同一作用域下的新组件，产生独立副本，名称追加" (副本)"，默认停用。
- **响应**：

```json
{
  "id": "018f...",
  "type": "preprocessor",
  "name": "Token 预置 (副本)",
  "sourceAssetId": "018g..."
}
```


## 3. 公共组件新建/编辑

新建与编辑组件使用抽屉（宽 640px），公共字段 + 随类型切换的配置表单。公共字段：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 名称 | text | 是 | 同作用域同类型内唯一（1000017322） |
| 类型 | select | 是 | preprocessor / postprocessor / validator / extractor；编辑态置灰不可改 |
| 作用域 | select | 是 | project / workspace / global；编辑态隐藏，仅新建时可选 |
| 描述 | textarea | 否 | 组件用途说明 |
| 启用 | switch | — | 启用/禁用开关，禁用时不参与执行；四类组件均在基础信息显示 |
| 排序号 | number | 仅处理器类 | 多处理器执行顺序，升序；仅前置/后置处理器类组件显示，存储于组件顶层 `sort_order` 列（config 不承载），场景引入时按升序决定处理器执行顺序 |

### 3.1 前置处理器 / 后置处理器

切换类型为前置/后置处理器时，展示处理器配置区。采用与验证器/提取器一致的平台设计原则：

基础信息包含启用与排序号（见 3 公共字段表，仅处理器类组件显示），配置区仅保留处理器核心参数与提取器：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 处理器类型 | select | 是 | `发送 HTTP 请求` / `执行 SQL`；首期支持两种，其余协议随多协议扩展预留 |

> **说明**：首期不提供处理器级异步与条件字段；启用/禁用与执行顺序统一通过基础信息的启用、排序号控制。排序号存储于组件顶层 `sort_order` 列（config 不承载），场景引入组件为处理器时按该字段升序插入。

**「发送 HTTP 请求」处理器配置：**（字段与 Ryze HTTP 处理器配置项一一对应，存储即 Ryze 元件，执行直接透传，无执行层转换）

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 请求方法 | select | 是 | GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS（缺省 GET） |
| ref 引用 | select | 是 | 从环境 http 配置中选择（选项值 = http 配置 `refName`）；自动预选该环境 `isDefault=true` 的 http 配置 |
| 路径（path） | text | 否 | 接口路径，支持 `${变量名}` 引用 |
| HTTP/2（http/2） | switch | 否 | 是否启用 HTTP/2 |
| 请求头（headers） | kv-table | 否 | 键值对编辑器，保存为 Map；支持变量引用（如 `Content-Type`） |
| Query 参数（query） | kv-table | 否 | URL 查询参数键值对，保存为 Map |
| 请求体类型 | select | 否 | 无 / JSON / 表单（data）/ 原始文本（body）：JSON 与原始编译为 `body`（Object/String），表单编译为 `data`（Map），`body` 优先级高于 `data` |

> **ref 下拉数据来源**：公共组件（project 作用域）取组件所属项目默认环境（`isDefault=true`）的 http 配置；测试场景级取场景关联环境（`environmentId`）的 http 配置。未配置环境/该环境下无 http 配置时，下拉为空并提示先配置。引用值为元数据（`refName`），本版仅前端编辑，执行层不解析（处理器 `config` 透传，见下）。

> HTTP `config` 对象仅含 Ryze 配置键：`method` / `ref` / `path` / `http/2` / `headers` / `query` / `data` / `body`。HTTP 处理器以 `ref`（引用环境 http 配置的 `refName`）取代原 `base_url` 自由文本框；平台 overlay 键（`enabled` / `sortOrder`/`extractors`）保存于元素顶层或实体列，不写入 `config`。

**「执行 SQL」处理器配置：**

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 数据源 | select | 是 | 从环境数据源中选择（选项值 = 数据源 `refName`，即 `config.datasource`）；数据来源同 HTTP `ref` 下拉（公共组件取项目默认环境、测试场景取关联环境，见上）；未配置时提示「请先在环境管理中配置数据源」 |
| SQL 语句 | textarea | 是 | 支持 `${变量名}` 引用；执行前校验数据源连接 |
| 参数 | list | 否 | SQL 占位符参数，仅值列表（对应 Ryze `args` 数组，按 `?` 占位顺序传入） |

**元素结构与转换规则**（无执行层转换：处理器元素即 Ryze 元件，`config` 直接透传；前后置语义由承载实体的 `processorType` 列区分）：

| 处理器类型 | `testclass` | `config` 键（与 Ryze 一致） |
| ---------- | ----------- | ------------------------- |
| 发送 HTTP 请求 | `http` | method / ref / path / http/2 / headers / query / data / body |
| 执行 SQL | `jdbc` | datasource / sql / args |

> SQL 处理器 `datasource` 引用环境管理配置数据源的 `ref_name`；HTTP 处理器 `ref` 引用环境 http 配置的 `refName`（取代原 `base_url`）。`args` 为 `?` 占位符参数数组。处理器元素顶层承载平台 overlay：`extractors`（提取器行列表）、`enabled`（启用）；`sortOrder` 保存于实体 `sort_order` 列。

**提取器（可选）：** 处理器可携带提取器，从处理器响应中提取变量供后续步骤使用。提取器列表以子表形式嵌入处理器配置区底部，每行结构与 3.3 提取器配置完全一致（含启用开关，支持逐条启停）。支持：

| 操作 | 说明 |
| ---- | ---- |
| 添加提取器 | 在子表内新增一行，手动逐字段填写（来源/表达式/目标变量名/提取描述） |
| 删除提取器 | 删除子表中任意一行 |
| 从公共组件获取 | 点击弹出「引入选择器」（见 `docs/05-interaction-design/44-global-asset-ui.md` 2.4）：范围为本作用域同项目内类型为 extractor 且启用的公共组件，支持搜索；选中引入为**复制**，得到独立副本，与源资产无关联，副本内容平铺追加到当前处理器提取器子表 |

### 3.2 验证器

切换类型为验证器时，展示验证器配置区。采用**平台自有数据结构**，遵循三大原则：

1. **自然语言化**：使用用户理解的术语（如「返回码」「等于」），隐藏 Ryze 内部概念
2. **最小化暴露**：仅暴露用户必须配置的字段，其余由平台推断
3. **启用禁用**：每个验证器可独立启用/禁用（开关配置于基础信息，见 3 公共字段表）

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 验证目标 | select | 是 | `返回码` / `JSON 字段` / `响应头` / `响应体` / `正则匹配` / `XPath` / `Groovy 脚本` |
| 表达式 | text | 视目标 | JSONPath / XPath / 正则 / 响应头名（仅部分目标需要） |
| 比较条件 | select | 是 | `等于` / `不等于` / `大于` / `小于` / `大于等于` / `小于等于` / `包含` / `不包含` / `以...开头` / `以...结尾` / `匹配正则` |
| 期望值 | text | 视目标 | 期望值（仅部分目标需要） |
| 验证器描述 | text | 否 | 用于报告展示的验证器说明 |

**目标与条件的联动关系**：

| 验证目标 | 表达式是否必填 | 表达式说明 | 期望值是否必填 | 可选条件 |
| ---- | ---- | ---- | ---- | ---- |
| 返回码 | 否 | — | 是 | 等于/不等于/大于/小于 |
| JSON 字段 | 是 | JSONPath，如 `$.code` | 是 | 等于/不等于/包含/匹配正则 |
| 响应头 | 是 | 头名，如 `Content-Type` | 是 | 等于/不等于 |
| 响应体 | 否 | — | 是 | 包含/不包含/以...开头/以...结尾 |
| 正则匹配 | 是 | 正则表达式 | 否 | 匹配正则 |
| XPath | 是 | XPath 表达式 | 是 | 等于/不等于 |
| Groovy 脚本 | 是 | 脚本内容 | 否 | — |

### 3.3 提取器

切换类型为提取器时，展示提取器配置区。采用**平台自有数据结构**（启用/禁用开关配置于基础信息，见 3 公共字段表）：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 提取来源 | select | 是 | `JSON 字段` / `XPath` / `正则捕获` / `两个标记之间` / `完整响应体` / `Groovy 脚本` |
| 表达式 | text | 视来源 | JSONPath / XPath / 正则 / 边界标记（仅部分来源需要） |
| 目标变量名 | text | 是 | 提取结果存入的变量名，后续步骤通过 `${变量名}` 引用 |
| 提取描述 | text | 否 | 用于报告展示的提取说明 |

**来源与表达式的联动关系**：

| 提取来源 | 表达式是否必填 | 表达式说明 | UI 控件 |
| ---- | ---- | ---- | ---- |
| JSON 字段 | 是 | JSONPath，如 `$.data.token` | 输入 JSONPath |
| XPath | 是 | XPath 表达式 | 输入 XPath |
| 正则捕获 | 是 | 正则表达式（含捕获组） | 输入正则 |
| 两个标记之间 | 是 | `左边界\|\|右边界` | 两个输入框 |
| 完整响应体 | 否 | — | 无需输入 |
| Groovy 脚本 | 是 | 脚本内容 | 代码编辑器 |

---


