# 软件测试平台——公共组件

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 公共组件接口（三级作用域）

> **权限**：查询需 `api-component:view`；写端点需 `api-component:edit` / `api-component:edit-space` / `api-component:edit-global` 任一，服务端再按记录作用域校验精确维护码（project→edit、workspace→edit-space、global→edit-global）。前端编辑 / 删除 / 启停入口按「任一维护码」口径显示，复制入口不受编辑权限限制。

### 1.1 分页查询公共组件列表

- **路径**：`GET /api/project/components?pageNo=1&pageSize=20&type=preprocessor&scope=project&keyword=Token`
- **筛选参数**：`type`（可选，preprocessor/postprocessor/validator/extractor）、`scope`（可选，project/workspace/global）、`keyword`（可选，名称模糊搜索）、`enabled`（可选，true/false）。
- **返回口径**：可见范围为「本项目 / 本空间 / 全局」三级作用域的并集，按 `updatedAt` 倒序分页。
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
      "config": "{\"enabled\":true,\"testclass\":\"http\",\"config\":{\"method\":\"POST\",\"ref\":\"http_1\",\"path\":\"/token\"},\"extractors\":[]}",
      "enabled": true,
      "updatedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 8
}
```

> `config` 为 JSON 字符串；`enabled` 为记录级启停列（区别于 config 内的同名键）。

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
    "enabled": true,
    "testclass": "http",
    "config": {
      "method": "POST",
      "ref": "http_1",
      "path": "/token",
      "body": { "username": "${username}" }
    },
    "extractors": []
  }
}
```

- **响应**：`{ "id": "018f..." }`
- **校验**：`type` 必填且限四种取值；`name` 必填且 ≤100 字符；`description` ≤500 字符；`scope` 缺省为 project，按作用域校验对应维护码；同作用域同类型内名称唯一（1000017322）。
- **启停**：记录 `enabled` 由服务端置 true，前端在 `config` 中同时提交 `enabled: true`。

### 1.3 更新公共组件

- **路径**：`PUT /api/project/components/:id`
- **请求体**：同 1.2（`scope` 缺省不变更；`type` 编辑态不可变更，与现值不一致时按 1000017321 报错；`enabled` 不随保存变更，仅由启停接口修改）。

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

> 1.6 / 1.7 的端点与前端服务层封装已就绪（`web/src/services/project/api-testing/component.ts` 的 `batchToggleComponents` / `batchDeleteComponents`），当前前端页面未提供批量操作入口。


## 2. 公共组件复制

### 2.1 复制公共组件

- **入口**：详情区 [复制]（查看态始终可见，不受编辑权限限制）。
- **说明**：复制为前端预填行为，不提供专用后端接口：右栏切换为新建面板并回填源组件全部配置（类型、作用域、描述、排序号与配置表单），名称默认追加「 (副本)」且可修改，由用户确认后经创建接口（见 1.2）落库，产生同一作用域下的独立副本；新记录 `enabled` 由服务端置 true（副本默认启用），与源组件互不影响。


## 3. 公共组件新建/编辑

新建与编辑在页面右栏编辑面板内完成（与环境管理页、函数管理页一致的左列表 / 右详情结构），公共字段 + 随类型切换的配置表单：前置 / 后置处理器用处理器配置编辑器，验证器与提取器分别用对应资产表单（组件契约见 3.4）。公共字段：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 名称 | text | 是 | 同作用域同类型内唯一（1000017322），maxlength 100 |
| 类型 | select | 是 | 前置处理器 / 后置处理器 / 验证器 / 提取器（preprocessor / postprocessor / validator / extractor）；编辑态置灰不可改 |
| 作用域 | select | 是 | 项目（project）/ 空间（workspace）/ 公共（global）；编辑态隐藏，仅新建时可选 |
| 描述 | textarea | 否 | 组件用途说明，maxlength 500 |

> **校验口径**：保存前前端仅校验名称非空（其余由服务端校验，见 1.2）；配置区字段不做前端必填校验，各配置表中的「必填」为执行所需语义。

> 新建组件默认启用（记录 `enabled` 列由服务端置 true，`config` 同时携带 `enabled: true`）；编辑面板不提供启停字段，保存时保留组件原启用状态。启停在详情区卡片头部的「启用」勾选处进行（见 `docs/05-interaction-design/05-api-testing/22-global-asset-ui.md` 2.3），禁用时不参与执行、不可被引入（引入选择器按 `enabled=true` 过滤，场景编辑器例外，见 3.1）。

> 组件排序号由顶层 `sort_order` 列承载（`INT`，默认 0，config 不承载），随保存提交；表单不提供该字段的编辑入口。

### 3.1 前置处理器 / 后置处理器

切换类型为前置/后置处理器时，展示处理器配置区。基础信息为名称、类型、作用域与描述（启停见 3 公共字段表下的说明），配置区仅保留处理器核心参数与提取器：

配置区首行为类型切换与引用选择（同排）：类型为 `HTTP` / `JDBC` 单选按钮组（对应「发送 HTTP 请求」/「执行 SQL」两种 Ryze 元件，首期支持两种，其余协议随多协议扩展预留），选中类型后按 testclass 展示对应配置。

**「发送 HTTP 请求」（testclass=http）配置：**（存储即 Ryze 元件，`config` 键与 Ryze 一致，执行时原样透传，缺省 ref 的注入见下）

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 请求方法 | select | 是 | GET / POST / PUT / PATCH / DELETE / HEAD / OPTIONS（缺省 GET，始终写入 config） |
| ref 引用 | select | 否 | 选择环境 HTTP 配置（选项文案「名称（引用名）」，值 = http 配置 `refName`，可搜索）；留空时执行前由服务端注入环境默认引用 |
| 路径（path） | text | 否 | 接口路径，支持 `${变量名}` 引用 |
| 请求头（headers） | kv-table（「请求头」tab） | 否 | 键值表行结构 `{ key, value, enabled }`，保存为 Map（忽略空键与未启用行）；支持变量引用（如 `Content-Type`） |
| Query 参数（query） | kv-table（「Query 参数」tab） | 否 | 同上，保存为 Map |
| 请求体 | tab（按钮组） | 否 | `none` / `x-www-form-urlencoded` / `raw`；raw 子类型 text / json / xml / html / javascript；JSON 子类型提供格式化按钮，解析失败提示「JSON 请求体格式非法，请修正后再保存」且不覆盖已保存 body |

> **请求体编译规则**：urlencoded 行编译为 `data`（Map）；raw+json 编译为 `body`（JSON 对象）；raw 其余子类型编译为 `body`（String）；`data` 与 `body` 互斥，同一时刻仅写其一。切换请求体类型或 raw 子类型时联动写入 / 移除 `Content-Type` 请求头行。

> **ref 下拉数据来源**：公共组件页取项目默认环境（`isDefault=true`）的 http 配置；场景编辑器取场景关联环境（`environmentId`）的 http 配置；环境页编辑环境自身处理器时隐藏 ref 选择。仅当用户尚未选择时自动预选 `isDefault=true` 的选项，不覆盖已有取值；无默认环境或该环境无 http 配置时下拉为空。引用值为 `refName`：环境 http 配置以 `refName` 注册为 Ryze 配置元件，执行时 `ref` 用于匹配该元件（缺省 ref 的 HTTP 处理器在执行前注入环境默认引用，避免空主机名）。

**「执行 SQL」（testclass=jdbc）配置：**

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 数据源 | select | 是 | 选择环境数据源（选项文案「名称（引用名）」，值 = 数据源 `refName`，写入 `config.datasource`）；数据来源同 HTTP `ref` 下拉，未配置时下拉为空 |
| SQL 语句 | textarea | 是 | 支持 `${变量名}` 引用 |
| 参数 | list | 否 | SQL 占位符参数，仅值列表（对应 Ryze `args` 数组，按 `?` 占位顺序传入），支持逐行增删 |

**元素结构与转换规则**（处理器元素即 Ryze 元件，`config` 原样透传；前后置语义由组件实体 `type` 列（preprocessor / postprocessor）区分）：

| 处理器类型 | `testclass` | `config` 键（与 Ryze 一致） |
| ---------- | ----------- | ------------------------- |
| 发送 HTTP 请求 | `http` | method / ref / path / headers / query / data / body |
| 执行 SQL | `jdbc` | datasource / sql / args |

> SQL 处理器 `datasource` 引用环境数据源的 `refName`；HTTP 处理器 `ref` 引用环境 http 配置的 `refName`。处理器元素顶层承载平台 overlay：`extractors`（提取器行列表）、`enabled`（启用）；`sortOrder` 保存于实体 `sort_order` 列。执行时 HTTP 缺省 `ref` 注入环境默认引用，提取器行转换为 Ryze 提取器格式（`{testclass, field, ref_name}`）。

**提取器（可选）：** 处理器可携带提取器，从处理器响应中提取变量供后续步骤使用。提取器以子表形式嵌入处理器配置区（「提取器」tab），行结构为 `{ enabled, source, expression, variableName, description? }`，与 3.3 提取器资产字段一致（便于复制引入后回读）；行内提供启用开关、提取来源、表达式、变量名与删除操作，不提供描述输入。保存时全空行被丢弃，部分填写的行保留。支持：

| 操作 | 说明 |
| ---- | ---- |
| 添加提取器 | 点击「+ 添加提取器」新增一行（空表默认带一行全空起点行，保存时丢弃），手动逐字段填写（来源/表达式/目标变量名） |
| 删除提取器 | 删除子表中任意一行 |
| 从公共组件获取 | 点击弹出「引入选择器」（见 `docs/05-interaction-design/05-api-testing/22-global-asset-ui.md` 2.4）：加载当前可见范围内（项目 / 空间 / 全局）类型为 extractor 且启用（`enabled=true`）的公共组件，首页 100 条、支持名称关键词搜索、多选勾选；选中引入为**复制**，得到独立副本，与源资产无关联，副本内容平铺追加到当前处理器提取器子表。场景编辑器的处理器/提取器选择器按同结构加载，但每页 200 条且不按启用态过滤 |

### 3.2 验证器

切换类型为验证器时，展示验证器配置区。采用**平台自有数据结构**，遵循三大原则：

1. **自然语言化**：使用用户理解的术语（如「状态码」「等于」），隐藏 Ryze 内部概念
2. **最小化暴露**：仅暴露用户必须配置的字段，其余由平台推断
3. **启用禁用**：每个验证器可独立启用/禁用（详情区卡片头部「启用」勾选，见 3 说明）

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 验证目标 | select | 是（缺省 `status_code`） | 状态码 / JSON 字段 / 响应头 / 响应体 / 正则匹配（`status_code` / `json_field` / `response_header` / `response_body` / `regex`） |
| 比较条件 | select | 是（缺省 `equals`） | 等于 / 不等于 / 大于 / 小于 / 大于等于 / 小于等于 / 包含 / 不包含 / 以…开头 / 以…结尾 / 正则匹配 |
| 表达式 | text | 视目标 | JSONPath（如 `$.code`）/ 响应头名 / 正则等，随目标填写 |
| 期望值 | text | 视目标 | 期望值 |

**数据结构与联动口径**：配置保存为 `{ enabled, target, expression, condition, expected }`。四个字段始终全部展示，目标与条件不做联动限制（全部目标可选全部条件），表达式与期望值不做条件必填校验；保存前仅校验组件名称（见 3 说明）。查看态按目标/条件的中文标签渲染摘要行。

> **执行约束**：目标与条件的兼容性由执行层保证——`status_code` / `json_field` / `response_header` / `response_body` 支持等于/不等于/包含/不包含/大小比较/正则匹配，「以…开头 / 以…结尾」仅在响应体断言内生效（执行时转锚定正则）；非法目标或条件在执行期报错。

### 3.3 提取器

切换类型为提取器时，展示提取器配置区。采用**平台自有数据结构**（启用/禁用由详情区卡片头部「启用」勾选控制，见 3 说明）：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 提取来源 | select | 是（缺省 `json_field`） | JSON 字段 / 响应头 / 正则匹配 / 完整响应体（`json_field` / `response_header` / `regex` / `full_body`） |
| 表达式 | text | 视来源 | JSONPath（如 `$.data.token`）/ 响应头名 / 正则，随来源填写；完整响应体无需表达式 |
| 目标变量名 | text | 是 | 提取结果存入的变量名（表单标签「变量名」），后续步骤通过 `${变量名}` 引用 |

**数据结构与联动口径**：配置保存为 `{ enabled, source, expression, variableName }`。三个字段始终全部展示，来源与表达式不做联动限制（全部来源共用同一表达式输入框），保存前仅校验组件名称（见 3 说明）；保存时全空行/空对象被丢弃。查看态按来源中文标签渲染摘要行，另展示「表达式」「目标变量名」。

### 3.4 配置表单共享组件契约

新建/编辑表单与各使用方（环境页、场景页、接口编辑器、调试页）复用同一组前端组件，契约如下：

| 组件 | 职责 | 契约要点 |
| ---- | ---- | ---- |
| `KeyValueTable` | 键值表编辑器 | `v-model:entries`（行 `{ key, value, enabled, description? }`）；`change` 事件；props：`placeholderKey`、`suggestions`（Key 列改可搜索下拉，allow-create）、`disabled`（只读）、`showEnabled`（启用勾选列，缺省显示）、`showDescription`（说明列，缺省隐藏）、`headerAdd`（表头 [＋ 新增] 并关闭自动补行）、`emptyText`（空表占位）；缺省末行空行自动补行（Postman 风格） |
| `RequestConfigEditor` | 请求配置编辑器（请求行 + 请求头 / Query / 请求体 tabs） | props：`method` / `url` / `headers` / `params` / `body`（`{ type, content }`）/ `validators?` / `extractors?`；事件：`update:*` 及 `add-validator` / `add-extractor` / `import-validators` / `import-extractors`；暴露 `emitAll()` 供父级保存前冲刷本地编辑态（序列化过滤空行）；仅当传入 `validators` / `extractors` 时渲染对应 tab |
| `ProcessorConfigEditor` | 处理器元素编辑器 | props：`modelValue?`（`{ testclass, config, extractors }` + 平台 overlay）、`httpOptions?` / `dsOptions?`（ref 下拉）、`showTypeSelect?` / `showRefSelect?`（缺省 true）；事件：`update:modelValue` / `import-extractors`；插槽 `header`（与类型/引用选择同排的页面级控件）；http 走 `RequestConfigEditor`，jdbc 走 SQL / 参数 + 提取器 tabs |
| `ValidatorsExtractorsPanes` | 验证器 / 提取器统一 tab 面板 | props：`validators?` / `extractors?`（传入才渲染对应 tab）；事件：`update:validators` / `update:extractors` / `add-*` / `import-*`；本地副本编辑、变更整表回传，tab 徽标为目标 / 来源非空行数 |
| `ValidatorForm` / `ExtractorForm` | 验证器 / 提取器资产表单 | `v-model`（config 对象），字段变更即时回传；缺省 target=`status_code`、condition=`equals`、source=`json_field` |
| `ExtractorAssetPicker` | 引入选择器对话框 | props：`modelValue`（显隐）/ `loading` / `items` / `error?` / `keyword` 及文案 `title?` / `tip?` / `emptyText?` / `searchPlaceholder?`；事件：`update:keyword` / `search` / `confirm(items)`；表列勾选 / 名称 / 作用域 / 描述，多选，关闭后清空勾选 |
| `ProcessorConfigDetail` | 处理器只读明细 | props：`element?`（`{ testclass, config, extractors }`），渲染参数行、代码块与提取器只读表格 |

> **使用口径**：公共组件页由 `ComponentEditPanel` 组合处理器/验证器/提取器表单，查看态处理器由 `ProcessorConfigDetail` 渲染、验证器/提取器按摘要行渲染；环境页复用 `ProcessorConfigEditor`（隐藏 ref 选择），场景页经 `header` 插槽承载名称与启用；接口编辑器复用 `KeyValueTable` 与 `ValidatorsExtractorsPanes`；场景步骤抽屉与步骤内联编辑器复用 `RequestConfigEditor`；快速调试与环境编辑复用 `KeyValueTable`。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 与前端实现对齐：修正处理器/验证器/提取器配置口径（移除未实现的 HTTP/2、XPath、Groovy 等项）、校验与联动描述、ref 数据来源与引入选择范围、复制与批量接口说明，并补充共享组件契约 |
