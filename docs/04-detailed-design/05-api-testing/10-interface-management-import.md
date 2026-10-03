# 软件测试平台——导入

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 导入接口

**统一约定**：三个端点均为 POST，上下文（projectId）经请求头传递，不出现在 URL 与请求体中；执行导入（1.1、1.2）需权限 `api-interface:edit`，仅解析预览（1.3）需权限 `api-interface:view`。预览只解析不入库；导入记录（`api_import_record`）的写入时机与字段取值见 `docs/04-detailed-design/05-api-testing/07-api-testing-infra-import-record.md`。

### 1.1 cURL 导入

- **路径**：`POST /api/project/interfaces/import/parsed`
- **请求体**：由前端 `curlParser` 解析 cURL 命令（单条或多条，解析规则见 3）后提交的解析结果，请求体只含 `operations` 字段：

```json
{
  "operations": [
    { "name": "POST /api/auth/login", "method": "POST", "path": "/api/auth/login", "headers": [], "queryParams": [], "body": { "type": "json", "content": {} } }
  ]
}
```

- **说明**：
  - cURL 解析在**前端**完成（复用快速调试 `curlParser`，支持 bash 与 Windows CMD 归一化），后端仅做解析结果落库，不重复解析文本；`operations` 为空时参数校验直接拒绝。
  - 每条操作的源标识为 `{method}:{path}`，增量更新基于该标识与现有接口匹配（详见 6）。
- **响应**：

```json
{
  "importHistoryId": "6f1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "summary": { "created": 12, "updated": 3, "failed": 0 },
  "errors": [{ "source": "POST /api/auth/login", "message": "校验失败原因" }]
}
```

- **失败处理**：逐条 upsert，单条失败不中断整体导入；`errors` 中给出失败明细（`source` 为操作名，`message` 为异常信息）。响应体只含 `importHistoryId` / `summary` / `errors` 三个字段，无 `status` 字段；整体状态由服务端写入导入记录（`success`：无失败；`partial`：有失败且至少一条成功；`failed`：全部失败）。

### 1.2 Swagger URL 导入

- **路径**：`POST /api/project/interfaces/import/url`
- **请求体**：

```json
{
  "url": "https://petstore.example.com/v2/swagger.json"
}
```

- **说明**：
  - 服务端拉取 URL 内容后按 Swagger 解析流程处理（格式自动识别 Swagger/OpenAPI 2.0/3.0，前端不再提供格式选择）；请求体可选 `format` 提示（`swagger` / `openapi`），前端当前不传。
  - URL 安全策略（统一定义见 `docs/04-detailed-design/05-api-testing/08-interface-management-overview.md` 2.3）：由配置项 `robotest.api-test.import.url-policy` 切换 `strict` / `intranet` 两级策略，缺省按配置文件（`server/src/main/resources/application-prod.yaml` 为 `strict`，`server/src/main/resources/application.yaml` 与 `server/src/main/resources/application-dev.yaml` 为 `intranet`）。`strict` 仅允许 http/https 协议，禁止内网与保留地址（本地回环、A/B/C 类私网：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16，链路本地 169.254.0.0/16 含云元数据地址，及任意本地/组播地址）；`intranet` 放行内网/回环地址但高危地址恒禁，且仅允许访问 swagger 文档特征路径（`/v2/api-docs`、`/v3/api-docs`、`swagger.json`、`openapi.yaml` 等特征端点及 `.json` / `.yaml` / `.yml` 文件）。两种策略均校验 DNS 解析后的逐 IP 复核。端口校验（`docs/00-spec/40-security/01-security.md` 6.3）：`strict` 策略限制显式端口白名单（默认 80/443/8080/8443，可经 `robotest.api-test.import.allowed-ports` 配置），两种策略均拒绝超出 1-65535 的非法端口。拉取不跟随重定向，超时 10 秒。
  - 前置失败整体中断、不产生导入记录，错误码：
    - `1000017012`（`API_IMPORT_URL_UNREACHABLE`）：URL 不可达、协议/端口/地址越权或域名解析失败；
    - `1000017010`（`API_IMPORT_FORMAT_UNSUPPORTED`）：按 `format` 提示与内容特征均无法识别格式；
    - `1000017011`（`API_IMPORT_PARSE_FAILED`）：内容解析失败（如未解析到任何路径定义）。
- **响应**：同 1.1。

### 1.3 预览导入内容

- **路径**：`POST /api/project/interfaces/import/preview`
- **请求体**：

```json
{
  "url": "https://petstore.example.com/v2/swagger.json"
}
```

- **说明**：
  - Swagger URL 导入前置预览：拉取并解析内容但**不写入数据库、不产生导入记录**，返回解析结果预览（接口列表、去重匹配情况）；URL 白名单校验同 1.2，格式自动识别。
  - 动作判定：`method` / `path` 缺失 → `skip`；按 `{method}:{path}` 已存在 → `update`（`conflict=true`）；否则 → `create`。
  - cURL 导入由前端解析后本地预览，不经过该接口。
- **响应**：

```json
{
  "items": [
    {
      "name": "用户登录",
      "method": "POST",
      "path": "/api/auth/login",
      "action": "create",
      "conflict": false
    },
    {
      "name": "用户注册",
      "method": "POST",
      "path": "/api/auth/register",
      "action": "update",
      "conflict": true
    }
  ],
  "summary": { "toCreate": 8, "toUpdate": 3, "toSkip": 1 }
}
```

---


## 2. 导入格式解析

各导入格式的解析策略：

| 格式 | 解析方式 | 源标识（sourceId） | 增量匹配策略 |
| ---- | -------- | -------- | ------------ |
| Swagger/OpenAPI 2.0/3.0 | 服务端 `swagger-parser` 按 `paths` 下各 operation 解析（支持 get/post/put/patch/delete/options/head，YAML/JSON，含 `$ref`） | `operationId`，缺省回退 `{method}:{path}` | 先按 `source_type` + `source_id` 查映射；无映射回退 `{method}:{path}` 匹配现有接口；命中则更新，未命中则创建 |
| cURL | 前端 `curlParser` 解析（单条或多条） | `{method}:{path}` | 同左（映射标识与回退标识一致） |

**字段映射**（Swagger）：接口名称按 `summary` → `operationId` → `METHOD path` 逐级降级；路径模板参数 `{id}` 归一为平台 `${id}` 占位；请求头取为空数组，Query 参数提取 `in=query` 的参数名，请求体仅提取 JSON 结构骨架（`application/json` → `type=json`，表单类型 → `type=form`，其余 → `type=raw`），示例值留给调试时填充。

**导入映射**：每次执行导入产生一条 `api_import_record` 与若干 `api_import_mapping` 记录（仅记录成功条目），映射的 `source_type` 取值 `curl_operation` / `swagger_operation`，记录源数据与平台对象的映射关系，支持下次导入时的增量匹配。


## 3. cURL 解析规则

cURL 解析在**前端**完成（复用快速调试 `curlParser`，见 `docs/04-detailed-design/05-api-testing/11-quick-debug.md` 4.2），支持**多条命令**批量解析。cURL 命令解析支持以下要素提取：

| cURL 参数 | 平台映射 |
| --------- | -------- |
| `-X` / `--request` | method |
| URL 参数 | 剥离 host 取 path（查询串拆分为 queryParams 数组） |
| `-H` / `--header` | headers（数组） |
| `-d` / `--data` / `--data-raw` | body.content（type=json） |
| `-F` / `--form` | body.content（type=form） |
| `-b` / `--cookie` | headers（Cookie） |

补充规则（与快速调试 4.2 同源）：未指定 `-X` 且携带请求体时默认 `POST`，否则默认 `GET`；`-d` 值无法解析为 JSON 时 `body.type` 降级为 `raw`；不支持的参数（如 `--proxy`、`--cert`）连同其取值一起忽略，不报错。

**失败口径**：未找到 URL 的命令与单条解析失败的命令在前端被静默丢弃，不进入提交内容，也不产生失败明细；`errors` 失败明细由后端逐条落库（upsert）失败时产生，不阻断其余条目导入。

---


## 4. 导入弹窗

- **入口**：接口工作区顶部右侧「导入」按钮（`web/src/pages/project/api-testing/interface/InterfaceWorkspace.vue`）先切回列表页签，再委托列表页打开弹窗（`web/src/pages/project/api-testing/interface/ImportDialog.vue`）；前端按钮未挂权限码，实际写入权限由后端 `api-interface:edit` 校验。
- **来源切换**：`Swagger URL` / `cURL` 单选切换，切换后清空已解析预览与本地解析结果；弹窗每次打开重置全部输入与结果。
- **URL 导入**：URL 输入框，格式自动识别（Swagger/OpenAPI 2.0/3.0），「解析预览」调用预览接口（1.3）。
- **cURL 导入**：多行粘贴区（支持多条命令），「本地解析」由前端 `parseCurlImport` 完成，不经过后端；本地预览条目动作恒为「新建」、`conflict` 恒为 `false`（前端不查后端去重情况）。
- **预览表格**：列展示名称、方法、路径、动作标签（`create`→新建 / `update`→覆盖更新 / `skip`→跳过）。预览为可选步骤，「执行导入」按钮始终可用。
- **输入校验**：URL 为空 → 警告「请输入 Swagger 文档 URL」；cURL 未解析到任何接口 → 警告「未解析到任何接口，请检查 cURL 命令格式」，均阻断后续动作。
- **执行导入**：以 `loading` 防重复提交；成功提示摘要 `新建 x · 更新 y · 失败 z`；`errors` 非空 → 保留弹窗，提示「部分条目失败（n），详见导入结果」并展示「失败明细」列表（`source：message`）；`errors` 为空 → 关闭弹窗。两种分支均向列表页 emit `imported`，列表回到第 1 页刷新。
- **异常反馈**：请求异常以错误消息提示，不关闭弹窗。

---


## 5. 解析库选型

| 格式 | 解析方式 | 说明 |
| ---- | ------ | ---- |
| Swagger/OpenAPI 2.0/3.0 | `io.swagger.parser.v3:swagger-parser` | 官方解析器，支持 YAML/JSON |
| cURL | 前端 `curlParser` | 复用快速调试前端解析，后端不解析 |

> 当前仅支持上述两种导入来源；文件导入（Postman / HAR / JMeter 等）未实现。


## 6. 增量导入策略

导入映射表（`api_import_mapping`）记录每次导入的源与目标映射。增量更新时：

1. 按 `source_type` + `source_id` 匹配已有映射。
2. 存在映射 → 按映射 `target_id` 取现有接口并更新（action = `updated`）。
3. 不存在映射 → 按 `{method}:{path}` 匹配现有接口：命中 → 更新（action = `updated`）；未命中 → 创建（action = `created`）。
4. 源中不存在的既有接口保留不删除，用户可手动清理。
5. 更新仅覆盖源中提供的字段（method、path、description、headers、queryParams、body/bodyType），`changeVersion` 递增并写入变更日志（action = `import`）；创建时名称冲突自动追加 ` (n)` 后缀。

---


## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 按实现对齐：请求体移除未提交的 `source` 字段，响应摘要改为 `{created, updated, failed}` 并澄清响应无 `status`，补全错误码与权限口径、cURL 本地预览与失败反馈分支、增量匹配回退规则及动作取值 |
