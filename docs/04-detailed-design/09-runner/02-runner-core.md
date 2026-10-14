# 软件测试平台——Runner 核心与通道协议

**文档版本**：V1.0  
**日期**：2026-10-14  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

定义执行端（Runner）的进程结构、通道协议与执行语义，为 Runner 开发与 AI 执行服务侧对接提供依据。**服务端 ↔ 执行端通道协议（帧、字段、原因码）以本文为单一事实源**，服务侧分册 `docs/04-detailed-design/03-function-testing/09-ai-ui-testing.md` 只消费不重复定义。

### 1.2 范围与对应设计

- 对应概设 `docs/02-high-level-design/03-function-testing/03-hld-ai-ui-testing.md` 第 2.1（总体架构与两条通道）、4.4（观察采集）、4.5（动作原语）、4.7（三层重试与执行 JS 护栏）、4.8（会话与凭据）、4.9（预置动作）节。
- 职责切分：**决策、判定、预算计数、落库全部在服务端**（服务侧分册）；本文只覆盖 Runner 侧的执行、观察、上报与本地护栏。Runner 不决策、不判定、不持有模型凭据、不持有长期密钥。
- 对应需求 `docs/01-requirements/03-function-testing/03-srs-ai-ui-testing.md` 的 2.3（系统边界）、3.4（动作原语）、3.5（观察与证据）、3.8（凭据安全）。

### 1.3 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 通道 | Runner 作为客户端出站连接服务端的 WSS 长连接，动作下发与观察回传的唯一通道 |
| 批次令牌 | `session.start` 前用于通道鉴权的短时凭据，由服务端签发、单批次有效 |
| 帧 | 通道上的一条 JSON 消息，`{ "type": "...", "payload": {...} }` |
| ref | 观察元素树中交互元素的临时引用（形如 `e12`），跨步稳定、DOM 真实变化后失效 |
| ref_hint | 预置动作中的稳健定位提示（可见文本 / placeholder / 简易选择器），与 ref 属不同执行通道 |
| 原因码 | 动作失败时 Runner 回传的失败分类枚举，服务端决策重试的输入 |

## 2. 数据设计

Runner **不使用数据库**（无 DB 实体，此为裁剪说明）；仅持本地状态，全部生命周期限定在批次内：

| 本地状态 | 存放 | 生命周期 |
| ---- | ---- | ---- |
| 会话状态 | 内存 | `session.start` 创建 → `session.end` 或断线超时销毁 |
| 凭据映射 | 内存（明文） | `session.start` 下发后持有，**批次结束立即清零**；不落盘、不写日志、不进任何回传 |
| ref 映射表 | 内存 | 每次观察重建，stale 时失效 |
| 执行参数 | 内存 | 由 `session.start` 载荷下发（来源为所选环境 `exec_params`），Runner 不自行读库 |

**配置文件**（`runner.config`，进程级、与批次无关）仅含：服务端通道地址、Runner 注册标识、日志级别、headless 开关、本地缓存目录（截图临时文件，批次结束清理）。**不含任何业务数据、不含量模型凭据。**

## 3. 接口详细设计（通道协议，单一事实源）

### 3.1 连接与鉴权

- **方向**：Runner 出站连接 `wss://{server}/ws/runner`（服务端不反向寻址 Runner，此为多实例可扩展的架构前提，概设 2.1）。
- **鉴权**：连接建立后首帧必须为 `session.hello`，携带服务端签发的**批次令牌**（单批次有效、过期即拒）；鉴权失败回 `error`（`code=AUTH_FAILED`）并断开。
- **心跳**：Runner 每 15s 发 `heartbeat`（含当前用例/步骤/动作序号，供服务端进度展示与失活判定）；服务端 60s 未收到心跳判定 Runner 失活，按任务失败落存口径处理（总册 1.5）。
- **重连**：断线后 Runner 以指数退避（1s 起、上限 30s）重连，携原批次令牌；令牌过期则连接被拒，Runner 停止并上报本地缓存的最后状态（若有通道可用时补发）。
- **帧格式**：文本帧 JSON，`{ "type": string, "payload": object, "ts": ISO-8601 UTC }`；二进制数据（截图）不经本通道，走文件域上传后以 `screenshot_ref` 引用。

### 3.2 帧清单

#### 3.2.1 服务端 → Runner

| 帧 | 载荷要点 | 说明 |
| ---- | ---- | ---- |
| `session.hello` | `batchToken` | 连接鉴权（首帧） |
| `session.start` | `context`（冻结的用例正文 / 模块锚点 / 环境内容）、`originWhitelist`（入口 URL origin 并集）、`credentials`（账号别名 → 明文密码映射，服务端解密后下发）、`execParams`（viewport / 动作超时 / allow_evaluate）、`budgetSeq` | 批次开始；Runner 校验后回 `session.ready`。**凭据明文仅存在于本帧内存处理过程，不落日志** |
| `initial_actions.execute` | `steps`（解析后的确定性原语序列，服务端已按当前环境解析引用与白名单）、`presetSource`（`project` / `environment`） | 预置动作直跑，不经模型 |
| `action.execute` | `action`（16 原语枚举）、`params`（ref / 文本 / 占位符原文 / JS 源码 / 键名 / 秒数…）、`budgetSeq`（服务端预算序号，回传时原样带回供对账） | 单动作下发；一个 `action.execute` 对应一次预算计数 |
| `observe` | `reason`（`stale` / `verify` / `manual`） | 主动请求观察（stale 重观察、验证阶段取证） |
| `session.end` | `reason`（`done` / `cancelled` / `timeout`） | 批次结束；Runner 清凭据、关会话、回 `session.closed` |

#### 3.2.2 Runner → 服务端

| 帧 | 载荷要点 | 说明 |
| ---- | ---- | ---- |
| `session.ready` | `resolved`（解析后的入口/账号摘要，不含密码） | 会话就绪（凭据与白名单校验通过） |
| `initial_actions.result` | `ok`、`failedStep`（序号）、`reasonCode`、`observation` | 预置动作结果；`ok=false` → 服务端将批次置 blocked（不烧 AI 预算，概设 4.9） |
| `action.result` | `budgetSeq`、`ok`、`observation`（成功时）、`reasonCode`（失败时）、`stale`（ref 是否失效）、`screenshotRef?` | 单动作结果；失败时 `observation` 为**带原因码的失败观察**（元素表 + 语义摘要） |
| `observe.result` | `observation`、`screenshotRef?` | 观察回传 |
| `trajectory.hint` | `kind`（`credential_leak` 等）、`masked` | 发现疑似明文密码等需要脱敏留痕的事件（概设 4.8 脱敏兜底的 Runner 侧上报） |
| `heartbeat` | `caseIdx/stepIdx/actionSeq` | 心跳与进度 |
| `error` | `code`、`message`（已脱敏） | 进程级错误（浏览器崩溃、通道异常） |
| `session.closed` | — | 确认清理完成（凭据已清零） |

#### 3.2.3 观察对象结构（`observation` 统一结构）

```json
{
  "frameId": "main",
  "url": "https://app.example.com/orders",
  "title": "订单管理",
  "semantic": { "pageType": "list", "toasts": ["保存成功"], "dialogs": [] },
  "elements": [
    { "ref": "e1", "depth": 0, "text": "销售管理 / 订单管理", "role": "heading" },
    { "ref": "e12", "depth": 2, "text": "新建", "role": "button", "name": "新建", "interactable": true }
  ],
  "truncated": false,
  "screenshotRef": "file://…"
}
```

- `elements` 为**按帧合并、iframe 拼接**后的扁平有序数组（`depth` 表达层级缩进），跨域 frame 内元素直接带 `ref` 混入，服务端与模型无感知（概设 4.4）。
- `truncated=true` 时服务端组包必须注入截断提示。
- `screenshotRef` 为文件域引用，帧内不传二进制。

#### 3.2.4 失败原因码枚举（单一事实源）

| reasonCode | 含义 | 典型处置（服务端决策面消费） |
| ---- | ---- | ---- |
| `not_visible` | 元素不可见 | 重观察 / 重试 |
| `covered` | 被上层遮挡点不到 | 等待或换路 |
| `no_response` | 点击后无预期变化 | 重试或换路 |
| `not_found` | ref 对应元素已不存在 | 重观察 |
| `timeout` | 动作超时 | 重试或止损 |
| `evaluate_error` | JS 执行报错 | 换路或判 blocked |
| `whitelist_violation` | 导航目标不在 origin 白名单 | 拒收，服务端按越界处理 |
| `allowlist_violation` | 原语不在白名单 | 拒收（协议级防御） |
| `allow_evaluate_disabled` | 环境开关关闭仍下发 evaluate | 拒收 |
| `budget_seq_mismatch` | 预算序号对账失败 | 服务端以本地预算为准并记录异常 |

> 原因码表只在此登记；服务端的重试、换路、止损决策逻辑见服务侧分册，Runner 不做任何决策。

## 4. 业务逻辑设计

### 4.1 动作执行语义（16 原语）

| 原语 | Runner 执行语义 | 本地护栏（双侧校验中的 Runner 侧） |
| ---- | ---- | ---- |
| `navigate` | 打开 URL | 目标 origin 必须在 `originWhitelist`，否则 `whitelist_violation` |
| `back` / `switch_tab` / `close_tab` | 浏览器历史 / 标签切换与关闭 | 标签数为 1 时 `close_tab` 回失败（不崩会话） |
| `click` | 按 ref 点击（先机械等待可点） | ref stale → `not_found` |
| `fill` | 按 ref 填充；**参数中密码位置为占位符，Runner 用内存凭据映射本地替换后注入**，回传与留痕均为占位符原文 | 替换后明文不回传、不落日志 |
| `press` | 按键（Enter/Tab/Esc…） | 键名白名单 |
| `select` | 按 ref 下拉选择 | 选项不存在 → `not_found` |
| `hover` / `scroll` | 悬停 / 滚动（方向或锚点） | — |
| `find_text` | 滚动定位文本，回"找到与否 + 位置观察" | — |
| `wait` | 等待 ≤10s（服务端已校验，Runner 二次校验） | >10s 拒收 |
| `screenshot` | 截图上传文件域，回 `screenshotRef` | — |
| `dialog` | accept / dismiss 原生弹窗 | 无弹窗时回 `no_response` |
| `evaluate` | 主 frame 执行 JS，结果序列化截断（≤64KB）回传 | **四道本地校验**：`allow_evaluate` 开关 → origin 白名单 → 源码逐字进轨迹回传（不可关闭）→ 仅接受服务端下发（Runner 无任何人机输入入口） |
| `done` | **不存在于 Runner**——`done` 由服务端消费模型输出后转为 `session.end`，Runner 侧不实现该原语 | 协议清单中 16 原语对 Runner 而言为 15 执行 + 1 服务端语义，见下方说明 |

> **`done` 口径**：概设 4.5 的第 16 原语「提交判定」是**模型与服务端之间的控制原语**（循环出口），不产生下发帧；通道协议的 `action.execute.action` 枚举因此为 15 个执行原语。该拆分不改变动作白名单语义——白名单校验发生在服务端组包前，Runner 二次校验的是其可执行的 15 个。

### 4.2 机械等待层（三层重试的第一层）

* 采用 Playwright 内建 actionability 等待（元素渲染、可点击窗口、导航稳定），等待窗口取 `execParams.actionTimeout`（默认 10s）。
* 机械等待**不产生失败帧**：窗口内等到即执行成功；窗口耗尽才回带原因码的失败观察。
* Runner 不计预算、不做重试决策；每次 `action.execute` 无论成败恰好消耗一次预算（服务端按 `budgetSeq` 对账），三层重试的边界见概设 4.7。

### 4.3 观察采集流水线

1. **采集**：CDP 直采 DOM 快照 + 无障碍树 + JS 求值结果；逐 frame 采集后按 frame 树序合并，跨域 frame 同样纳入（iframe 拼接，概设 4.4）。
2. **裁剪**：剔除 `script`/`style`/`hidden`；**遮挡过滤**（被上层完全遮挡、点不到的元素不进快照）；属性白名单仅保留可访问名 / role / placeholder / value 等少量字段，不做全量属性 dump。
3. **语义摘要**：标题、URL、页面类型、toast、原生/自定义弹窗——Runner 侧规则提取（不走模型）。
4. **ref 分配**：交互元素分配临时引用 `eN`；**页面未变 ref 保持有效，真实 DOM 变化（子树增删改）即整体重建映射并标 stale**，服务端收到 `stale=true` 后组织重观察。
5. **截断**：元素数 >1000 或体积 >128KB 时按可见区域截断，`truncated=true`，截断规则文本由服务端注入提示词。
6. **截图**：验证阶段由 `observe(reason=verify)` 触发，另支持 `grounded` 模式（ref 画框标注后上传）；截图走文件域上传通道。

> 定位纪律：AI 决策只用 ref，Runner 执行也只接受 ref（预置动作通道除外，见 4.5）；**不提供 CSS / 文本语义定位兜底**（概设 4.4）。

### 4.4 会话与登录

* 批次内复用同一浏览器上下文（真实连续会话，概设 4.8）；`session.start` 创建、`session.end` 销毁并清凭据。
* 登录两类路径：**预置动作固化执行**（4.5）或 **AI 自主决策**（经 `action.execute` 下发 navigate/fill/click）；Runner 对两者一视同仁，仅凭据替换逻辑相同。
* 换账号由用例前置条件触发（服务端决策后可能下发新的 `fill` 凭据或重建会话帧——**会话不换、账号换登录**，换账号动作即一次普通登录流程）。

### 4.5 预置动作执行（确定性通道）

* `initial_actions.execute` 的 `steps` 由**服务端**完成两级继承解析（环境级 → 项目级，概设 4.9）与引用解析（entryName → 当前环境 URL、accountAlias → 当前环境凭据）后下发，Runner 收到的是**已解析、已过白名单校验**的确定性序列。
* Runner 直跑、不经模型、**不接受 evaluate**（下发含 evaluate 即以 `allowlist_violation` 拒收）；允许 `ref_hint` 定位（与 AI 决策通道的 ref 纪律不冲突，概设 4.9）。
* 定位解析顺序（`ref_hint`）：可见文本精确匹配 → placeholder 匹配 → 简易选择器（限定 `data-testid` / `name` / `id` 属性）；多命中或零命中 → `failedStep` + `not_found`，服务端将批次置 blocked 并写明第几步。
* 该通道的每次执行也逐帧回传 `initial_actions.result`（成功步的观察摘要进轨迹留痕，`kind=action`、阶段为 `precondition`）。

### 4.6 护栏汇总（Runner 侧落地）

| 护栏 | 落地 |
| ---- | ---- |
| origin 白名单 | `navigate` 与 `evaluate` 双处校验（`session.start` 载荷即白名单） |
| 动作 allowlist | `action.execute` 枚举校验，清单外拒收 |
| 凭据占位符 | 明文只进内存替换、不回传不落日志；`trajectory.hint(credential_leak)` 兜底上报 |
| 执行 JS | 开关校验 + 逐字回传 + 纪律（服务端提示词承担，Runner 侧只保证可审计） |
| 资源 | 浏览器进程崩溃 / OOM → `error(code=BROWSER_CRASHED)`；截图临时文件批次结束清理；单批次时长与内存上限由服务端 `session.end` 强制 |

## 5. 前端设计（裁剪与 CLI 壳）

* **无 Web 前端**：Runner 不属于 `web/`，不产生页面；本条为裁剪说明，交互设计分册不覆盖 Runner。
* **CLI 壳（V1 先行）**：`runner start --config <path>` 启动，注册到服务端通道；日志输出到控制台与本地滚动文件（**统一脱敏过滤器**：密码字段、Authorization、凭据帧内容一律替换为 `***`）；退出时 `session.end` 清理。
* **桌面壳（M3 升级项）**：Electron 包裹同一 Runner 核心，仅追加登录自动注册与托盘，不改变协议与执行语义；本文不展开。

## 6. 实施说明

### 6.1 仓库落位与依赖（新增依赖，过会项）

    runner/                        # 顶层目录，与 web/、server/ 并列
    ├── package.json               # 独立 npm 包，不进 web/server 构建链
    ├── src/
    │   ├── protocol/              # 帧类型与原因码（与本文 3.2 同步）
    │   ├── session/               # 会话、凭据映射、清理
    │   ├── actions/               # 15 执行原语
    │   ├── observe/               # CDP 采集、裁剪、ref、截图
    │   └── cli/                   # CLI 壳与日志脱敏
    └── test/

| 依赖 | 用途 | 必要性说明（C10/依赖管控） |
| ---- | ---- | ---- |
| Playwright | 浏览器驱动与 CDP 观察 | 概设 M1 过会项；browser-use 同路线，动作 actionability 与 CDP 可控性是核心选型依据 |
| ws（或等价 WSS 客户端） | 通道连接 | 原生 WebSocket 能力不足时引入，随 M1 一并评估 |
| Electron / electron-builder | 桌面壳 | **M3 项，本轮不引入** |

### 6.2 构建与运行

* `runner/` 独立 `pnpm build` 产出 CLI；不参与 `scripts/validate.mjs --frontend/--backend` 门禁（M2 落地时再纳入，需在质量脚本登记）。
* 本地调试：`headless=false` + dev 服务端通道；调试日志同样过脱敏过滤器。
* 版本对齐：`session.hello` 携带协议版本号（`v1`），服务端不兼容时回 `error(code=PROTOCOL_VERSION)`——协议演进向后兼容，新增帧不影响旧 Runner。

### 6.3 与服务侧分册的对接清单

| 项 | 本文位置 | 服务侧消费位置 |
| ---- | ---- | ---- |
| 帧与字段 | 3.2 | 服务侧分册「通道服务」章节 |
| 失败原因码 | 3.2.4 | 服务侧分册决策重试逻辑 |
| 观察结构 | 3.2.3 | 服务侧组包与轨迹落库 |
| 凭据明文下发 | 3.2.1 `session.start` | 服务侧分册凭据解密与下发、日志脱敏 |
| 预置动作解析 | 4.5 | 服务侧两级继承与引用解析 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-14 | 初始版本 |
