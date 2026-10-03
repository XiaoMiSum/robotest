# 软件测试平台——变量体系

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 步骤级变量管理

步骤级变量内嵌在场景步骤的 `variables` 段（随 `api_scene.steps` JSONB 落库），提供独立的查询与批量更新接口。

### 1.1 查询步骤变量列表

- **路径**：`GET /api/project/api-scenes/:sceneId/steps/:stepId/variables`
- **权限**：`api-scene:view`（须为当前项目成员，下同）
- **响应**（`data` 为数组，按存储顺序返回）：

```json
[
  {
    "id": "018f...",
    "name": "username",
    "value": "${TEST_USER}",
    "source": "custom",
    "interfaceVariableId": null,
    "description": "用户名",
    "sortOrder": 0
  }
]
```

- **字段**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | string | 变量 id，批量更新时由服务端重新生成 |
| name | string | 变量名，非空，两侧空白被 trim |
| value | string | 变量值，可包含 `${...}` 引用 |
| source | string | 来源标记；经 1.2 批量更新的变量写 `custom` |
| interfaceVariableId | string \| null | 关联的接口变量 id，当前实现无写入方，恒为 `null` |
| description | string | 描述，可空 |
| sortOrder | int | 排序号，批量更新时按数组顺序自 0 递增 |

### 1.2 批量更新步骤变量

- **路径**：`PUT /api/project/api-scenes/:sceneId/steps/:stepId/variables`
- **权限**：`api-scene:edit`
- **请求体**：

```json
{
  "variables": [
    { "name": "username", "value": "admin", "description": "用户名" },
    { "name": "password", "value": "${TEST_PASSWORD}", "description": "密码" }
  ]
}
```

- **说明**：全量覆盖步骤 `variables` 段；请求体元素仅含 `name` / `value` / `description` 三个字段，服务端过滤空变量名并 trim 名称，`source` 写 `custom`、`interfaceVariableId` 置 `null`、`sortOrder` 按数组顺序自 0 递增。
- **前端**：`scene.ts` 提供 `fetchStepVariables` / `updateStepVariables` 封装，调用方为步骤编辑抽屉（`StepEditorDrawer.vue`，逻辑在 `useStepEditorDrawer.ts`：编辑已有步骤时读取、保存时提交，变量行全部为空则不提交）。该抽屉当前未被任何页面挂载，场景编排页使用的内联步骤编辑器（`SceneStepInlineEditor.vue`）不含变量编辑入口，故这组接口在页面上暂无调用入口。


## 2. 场景变量管理

### 2.1 随场景聚合提交

场景级变量随场景整体聚合更新：变量列表嵌入场景创建（`POST /api/project/api-scenes`）/ 更新（`PUT /api/project/api-scenes/:id`）请求体的 `variables` 字段，一次整体提交写入主表 `api_scene.variables` JSONB 列；场景详情（`GET /api/project/api-scenes/:id`）随场景配置返回完整变量列表（接口定义见 `docs/04-detailed-design/05-api-testing/13-test-scenario-management.md` 1.3 / 1.4 / 1.2）。不提供独立的场景变量批量更新接口，批量替换、导入、导出统一复用场景的创建与更新能力。

- **权限**：创建与更新 `api-scene:edit`，详情 `api-scene:view`。
- **全量覆盖语义**：更新请求携带 `variables` 时整体替换 JSONB 数组（未携带该字段时保留原值），创建请求携带时随场景一并落库，空数组即清空。
- **归一化**：过滤空变量名、trim 名称；服务端不对变量名做字符集与重名校验。

`variables` 元素结构：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| name | string | 是 | 变量名（空名行被过滤） |
| value | string | 否 | 变量值，可包含 `${...}` 引用 |
| description | string | 否 | 变量描述 |

请求体示例（场景更新 body 中的变量段）：

```json
{
  "variables": [
    { "name": "username", "value": "admin", "description": "测试用户名" },
    { "name": "password", "value": "${TEST_PASSWORD}", "description": "密码" }
  ]
}
```

- **草稿执行携带**：创建态未保存场景经 `POST /api/project/api-scenes/draft/execute`（权限 `api-scene:execute`）以 `sceneVariables`（场景级）与 `stepVariables`（步骤级）字段随请求体携带，不落库，执行结果反映页面实时状态。

### 2.2 场景变量编辑与变量助手（前端）

**变量编辑**：场景编辑器左区「场景变量」页签用 `KeyValueTable.vue` 行内编辑，列为变量名 / 值 / 说明 + 行删除；末行非空自动追加空行、尾部多余空行自动回收，不设启用列与表头新增按钮；变量名与值不作格式、重名校验。保存动作（创建与编辑）始终携带 `variables` 全量提交（序列化时过滤空行、trim 名称、空串转缺省），草稿运行经 `sceneVariables` 提交。

**变量助手弹窗**（`SceneVariableHelperDialog.vue`）：

| 项 | 行为 |
| ---- | ---- |
| 入口 | 场景编辑器页签右上角 [变量助手]（同排另有 [函数助手]） |
| 打开 | 读取场景关联环境的变量；未选择环境时取项目默认环境并以该环境名展示 |
| 标题 | 「环境变量（环境名）」；无环境名时显示「未选择环境」，并挂警告标签「展示默认环境变量」 |
| 分区一 | 环境变量：读取 `GET /api/project/environments/:id` 的 `variables`（值明文回显，未配置值展示为空） |
| 分区二 | 场景变量（含未保存编辑态）：标题右侧标签显示变量个数 |
| 行操作 | 名称 · 值 · [复制]；复制将 `${变量名}` 引用形式写入剪贴板，成功与失败均有 Toast |
| 空态 | 「该环境暂未配置变量」/「场景暂未定义变量」 |
| 错误态 | 环境变量加载失败提示「加载环境变量失败」，弹窗照常打开 |
| 权限 | 弹窗与按钮不单独挂权限码；环境变量读取需 `api-env:view`，场景保存由后端 `api-scene:edit` 兜底 |

模块入口权限：测试场景菜单项挂 `api-scene:view`，环境管理挂 `api-env:view`，函数管理挂 `api-func:view`（`ApiTestingPage.vue` 侧边导航按权限码过滤），页面内的保存、执行按钮不做前端权限裁剪，由后端 `@PreAuthorize` 兜底。


## 3. 变量引用与内置函数

### 3.1 引用语法与解析优先级

变量引用使用 Ryze 原生 `${变量名}` 语法，环境变量、场景变量、步骤级变量、提取器变量统一采用该形式，不使用 `env:` / `var:` 前缀；变量值中可嵌套 `${...}`，由引擎在运行时求值。

**变量解析优先级**（从低到高）：

```
内置函数 < 环境变量 < 场景变量 < 步骤级变量 < 步骤提取器变量 < 运行时覆盖
```

各层级的承载位置与覆盖关系：

| 层级 | 承载位置 | 说明 |
| ---- | ---- | ---- |
| 环境变量 | suite 级 `variables` 底层 | 执行时的环境快照变量，最先写入 |
| 场景变量 | suite 级 `variables`（环境之后写入） | 覆盖同名环境变量 |
| 步骤级变量 | sampler 级 `variables` | Ryze context chain 覆盖 suite 级同名变量 |
| 步骤提取器变量 | 提取元件 `ref_name` | 提取结果写入上下文并流向下序步骤，覆盖同名变量 |
| 运行时覆盖 | 执行请求 `variableOverrides` 字段 | 接口契约预留；当前执行链路未消费，前端也不传入 |

> Mock 响应体的变量解析独立于场景执行：支持 `${uuid()}`、`${timestamp()}`、`${env:VAR}`，未定义的 `${...}` 保留原文，见 `docs/04-detailed-design/05-api-testing/24-mock-service-serving.md` 4。

### 3.2 内置函数清单

内置函数目录接口 `GET /api/project/functions/builtin`（权限 `api-func:view`）以引擎运行时实际注册的函数为准，平台补充分组、描述、签名、参数与示例元数据，执行期可用函数与目录一致；未匹配到平台元数据的函数回退通用描述。当前元数据清单：

| 分组 | 函数 | 签名 | 说明 | 示例 |
| ---- | ---- | ---- | ---- | ---- |
| 数据生成 | `random` | `${random(min, max)}` | 生成指定区间的随机整数 | `${random(1, 100)}` |
| 数据生成 | `random_string` | `${random_string(length)}` | 生成指定长度的随机字符串（含字母与数字） | `${random_string(8)}` |
| 数据生成 | `faker` | `${faker(path[, locale])}` | 按 Faker 表达式生成仿真测试数据 | `${faker(name.fullName, zh_CN)}` |
| 数据生成 | `uuid` | `${uuid()}` | 生成随机 UUID（去连字符） | `${uuid()}` |
| 日期时间 | `timestamp` | `${timestamp([format\|_s])}` | 当前时间戳，默认毫秒；可指定秒级或日期格式 | `${timestamp(_s)}` |
| 日期时间 | `time_shift` | `${time_shift([format,] offset)}` | 基于当前时间按 ISO-8601 偏移量平移后格式化输出 | `${time_shift(+1d)}` |
| 数据处理 | `json` | `${json(k1=v1, k2=v2)}` | 将多组 k=v 参数组装为 JSON 字符串 | `${json(code=0, msg=ok)}` |
| 数据处理 | `json_read` | `${json_read(json, jsonpath)}` | 从 JSON 文本中按 JsonPath 提取值 | `${json_read(${json(id=1)}, $.id)}` |
| 数据处理 | `url_encode` | `${url_encode(content)}` | URL 编码 | `${url_encode(a b&c=1)}` |
| 数据处理 | `url_decode` | `${url_decode(content)}` | URL 解码 | `${url_decode(%E4%B8%AD%E6%96%87)}` |
| 数据处理 | `base64_encode` | `${base64_encode(content)}` | Base64 编码 | `${base64_encode(robotest)}` |
| 数据处理 | `base64_decode` | `${base64_decode(content)}` | Base64 解码 | `${base64_decode(cm9ib3Rlc3Q=)}` |
| 数据处理 | `property` | `${property(key)}` | 读取平台变量值（就近作用域解析） | `${property(token)}` |
| 安全加密 | `digest` | `${digest(algorithm, content[, salt])}` | 摘要算法（md5/sha-1/sha-256 等，支持盐值） | `${digest(md5, password, salt123)}` |
| 安全加密 | `google2fa` | `${google2fa(secretKey)}` | 根据 2FA 密钥生成 Google 验证码 | `${google2fa(JBSWY3DPEHPK3PXP)}` |

日期格式化不提供独立 `date` 函数，统一由 `timestamp` 的格式化参数承担；随机字符串函数名为 `random_string`（下划线）。

### 3.3 自定义函数与函数助手

**自定义函数**：作用域 项目 > 空间 > 全局（同名取更高作用域），仅启用项生效，调用形式 `${函数名(参数)}`。执行前平台把当前项目可见且启用的自定义函数调用名重写为 `${robotest_custom("函数名", 参数…)}`，由平台适配层分发执行；未命中的调用名原样保留，交由引擎按内置函数解析。

**函数助手弹窗**（`FunctionHelperDialog.vue`，当前挂载于场景编辑器页签右上角 [函数助手]）：

| 项 | 行为 |
| ---- | ---- |
| 打开 | 并行加载内置函数目录与自定义函数列表，并重置选择、参数与表达式 |
| 选择与搜索 | 下拉按「内置函数 / 自定义函数」分组，按函数名与描述实时过滤 |
| 函数说明 | 签名、描述、参数（名称 / 必填 / 说明）、示例，只读展示 |
| 参数填写 | 按元数据参数逐项输入，空参数不参与表达式拼接 |
| 表达式 | 选择函数或修改参数时自动生成，可手工改写 |
| 试算 | [试算] 调用 `POST /api/project/functions/evaluate`（权限 `api-func:view`，表达式必填且不超过 2000 字符），展示结果与耗时 |
| 复制 | 复制表达式 / 复制试算结果，成功与失败均有 Toast |

试算失败按错误码提示：`1000017021` 自定义函数不存在或不属于当前可见范围、`1000017022` 函数名与内置函数重名或同作用域已存在同名函数、`1000017023` Groovy 脚本编译失败、`1000017024` 函数试算执行失败。


## 4. Ryze 变量映射

- 平台变量与 Ryze `variables` 为直接一对一：`name` → key，`value` → value。
- 变量值保持原始 `${...}` 引用，平台不预解析，由 Ryze 引擎在运行时求值；内置函数即引擎自身函数，同样在运行时求值，平台不代为展开。
- 装配层级：suite 级 `variables` = 环境变量 + 场景变量（场景覆盖同名环境变量），sampler 级 `variables` = 步骤级变量（覆盖 suite 级同名变量）；提取器以元件 `ref_name` 承载变量名，结果经 context chain 流向下序步骤。
- 自定义函数经 3.3 的调用名重写后进入引擎，引擎自身只解析变量引用与内置函数。
- 定时任务的多场景组合执行中，环境相关内容挂顶层 suite，场景子 suite 的 `variables` 仅含该场景变量，见 `docs/04-detailed-design/05-api-testing/27-scheduled-task-scheduler.md` 2。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐实现：修正步骤/场景变量接口路径与响应字段、变量引用语法与内置函数清单、Ryze 映射口径，补充场景变量编辑、变量助手与函数助手的前端交互及权限 |
