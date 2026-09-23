# 软件测试平台——（分册：导入）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.2 导入接口

#### 3.4.1 cURL 导入

- **路径**：`POST /api/project/interfaces/import/parsed`
- **请求体**：由前端 `curlParser` 解析 cURL 命令（单条或多条，解析规则见 4.4）后提交的解析结果。

```json
{
  "source": "curl",
  "operations": [
    { "name": "用户登录", "method": "POST", "path": "/api/auth/login", "headers": [], "queryParams": [], "body": { "type": "json", "content": {} } }
  ]
}
```

- **说明**：
  - cURL 解析在**前端**完成（复用快速调试 `curlParser`，支持 `bash` 与 Windows CMD 归一化），后端仅做解析结果落库，不重复解析文本。
  - 增量更新：基于 `{method}:{path}` 匹配（curl 无稳定源标识），存在则更新，不存在则创建。
- **响应**：

```json
{
  "importHistoryId": "018f...",
  "summary": { "created": 12, "updated": 3, "failed": 0, "skipped": 1 },
  "errors": []
}
```

- **失败处理**：部分解析失败时返回 `status: "partial"`，`errors` 中包含具体失败明细。

#### 3.4.2 Swagger URL 导入

- **路径**：`POST /api/project/interfaces/import/url`
- **请求体**：

```json
{
  "url": "https://petstore.example.com/v2/swagger.json"
}
```

- **说明**：
  - 服务端拉取 URL 内容后按 Swagger 解析流程处理（格式自动识别 Swagger/OpenAPI 2.0/3.0，前端不再提供格式选择）。
  - URL 安全策略（见 4.3）：默认 `strict` 策略仅允许 http/https 协议并禁止内网/保留地址（127.0.0.0/8、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、169.254.0.0/16）防止 SSRF；内网部署环境可切换 `intranet` 策略放行内网地址、仅允许访问 swagger 文档特征路径。
  - 拉取失败返回错误码 7012（`API_IMPORT_URL_UNREACHABLE`）。
- **响应**：同 3.4.1。

#### 3.4.3 预览导入内容

- **路径**：`POST /api/project/interfaces/import/preview`
- **请求体**：

```json
{
  "url": "https://petstore.example.com/v2/swagger.json"
}
```

- **说明**：
  - Swagger URL 导入前置预览：拉取并解析内容但**不写入数据库**，返回解析结果预览（接口列表、去重匹配情况）；URL 白名单校验同 3.4.2，格式自动识别。
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


### 4.1 导入格式解析

各导入格式的解析策略：

| 格式 | 解析方式 | 去重标识 | 增量更新策略 |
| ---- | -------- | -------- | ------------ |
| Swagger/OpenAPI 2.0/3.0 | 服务端按 paths 下各 operation 解析 | `{method}:{path}` 或 `operationId` | 存在则更新字段，不存在则创建 |
| cURL | 前端 `curlParser` 解析（单条或多条） | `{method}:{path}` | 存在则更新字段，不存在则创建 |

**导入映射**：每次导入产生 `api_import_mapping` 记录，记录源数据与平台对象的映射关系，支持下次导入时的增量匹配。


### 4.4 cURL 解析规则

cURL 解析在**前端**完成（复用快速调试 `curlParser`，见 `docs/04-detailed-design/30-quick-debug.md` 4.2），支持**多条命令**批量解析。cURL 命令解析支持以下要素提取：

| cURL 参数 | 平台映射 |
| --------- | -------- |
| `-X` / `--request` | method |
| URL 参数 | path |
| `-H` / `--header` | headers（数组） |
| `-d` / `--data` / `--data-raw` | body.content（type=json） |
| `-F` / `--form` | body.content（type=form） |
| `-b` / `--cookie` | headers（Cookie） |

不支持的参数（如 `--proxy`、`--cert`）在解析结果中忽略，不报错。解析失败的命令条目计入失败清单，不阻断整体导入。

---


### 5.3 导入弹窗

- **cURL 导入**：多行粘贴区，粘贴 cURL 命令（单条或多条），前端解析后本地预览、确认后提交解析结果。
- **URL 导入**：URL 输入框，格式自动识别（Swagger/OpenAPI 2.0/3.0）。
- **预览确认**：导入前展示解析结果预览（接口列表、去重匹配），用户确认后执行。

---


### 6.1 文件解析库选型

| 格式 | 解析方式 | 说明 |
| ---- | ------ | ---- |
| Swagger/OpenAPI 2.0/3.0 | `io.swagger.parser.v3:swagger-parser` | 官方解析器，支持 YAML/JSON |
| cURL | 前端 `curlParser` | 复用快速调试前端解析，后端不解析 |


### 6.2 增量导入策略

导入映射表（`api_import_mapping`）记录每次导入的源与目标映射。增量更新时：
1. 按 `source_type` + `source_id` 匹配已有映射。
2. 存在映射 → 更新对应平台对象（action = updated）。
3. 不存在映射 → 创建新对象（action = created）。
4. 源中不存在的映射 → 保留平台对象不删除（action = skipped），用户可手动清理。


