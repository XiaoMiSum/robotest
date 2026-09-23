# 软件测试平台——（分册：全局处理器）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.2 全局处理器管理

环境级处理器**随环境整体聚合提交**：处理器列表嵌入环境配置中，通过环境的创建/更新接口（3.1.3 / 3.1.4）一次整体提交，写入主表 `processors` JSONB 列；环境详情接口（3.1.2）随环境配置返回完整处理器列表。不再提供独立的处理器 CRUD 接口。

> 处理器的 `config` 结构与公共组件（`docs/04-detailed-design/01-readme.md` 5.3.1）及场景级处理器完全一致：处理器元素即 Ryze 元件，顶层 `testclass`（`http` / `jdbc`）+ `config`（含 Ryze 配置键）+ `extractors` + `enabled`，存储直接透传 Ryze 执行，无执行层转换。HTTP `config` 含 method / ref / path / http/2 / headers / query / data / body，其中 `ref` 引用环境 http 配置的 `refName`（取代原 `base_url`）；SQL `config` 含 datasource / sql / args。启用（enabled）与排序号（sortOrder）在处理器对话框基础信息区（名称下方）配置，`enabled` 同步写入元素顶层与 `processors` 元素 `enabled` 字段，`sortOrder` 写入元素 `sortOrder` 字段；首期不提供处理器级异步与条件字段。前端复用公共组件的 `ProcessorForm` 结构化编辑，处理器携带的提取器支持从公共组件（type=extractor）复制引入（见 `docs/04-detailed-design/01-readme.md` 5.3.1）。

`processors` 数组元素结构：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| processorType | VARCHAR(20) | 是 | preprocessor / postprocessor |
| name | VARCHAR(100) | 是 | 处理器名称 |
| config | JSON | 是 | 处理器配置（结构见上） |
| sortOrder | INT | 否 | 排序序号（缺省 0） |
| enabled | BOOLEAN | 否 | 启用状态（缺省 true） |


