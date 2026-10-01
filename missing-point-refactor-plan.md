# 方案：遗漏分析改为文档级全量候选检索

> 状态：**待用户确认**
> 依据文档：`docs/04-detailed-design/15-ai-review/65-ai-review-missing-points.md`（已更新，提交 `af418253`）
> 关联：AI 遗漏测试点分析（US-AI-007）从项目级关键词检索改为文档级全量获取

---

## 一、变更总览

| 端 | 文件 | 变更 |
|---|---|---|
| 后端 | `model/dto/request/ai/AiMissingPointReqDTO.java` | 删 `keywords`，增 `documentIds` |
| 后端 | `model/dto/response/ai/AiMissingPointRespDTO.java` | 删 `semanticDegraded` |
| 后端 | `service/ai/casegen/AiMissingPointServiceImpl.java` | 删关键词抽取依赖与前缀块，`retrieveCandidates` 改为文档级全量 |
| 后端 | `test/.../AiMissingPointServiceImplTest.java` | 同步改造测试 |
| 前端 | `services/ai.ts` | `AiMissingPointReq` 删 `keywords`、增 `documentIds` |
| 前端 | `types/ai.ts` | `AiMissingPointResult` 删 `semanticDegraded` |
| 前端 | `composables/.../useMissingPointsPanel.ts` | 删 `keywords`，`buildReq()` 发 `documentIds: [docId()]` |
| 前端 | `components/.../MissingPointsPanel.vue` | 删关键词输入框与降级提示，焦点改落需求文本 |
| 前端 | `useMissingPointsPanel.spec.ts` | 同步改造测试 |
| 生成物 | `types/generated/contract.d.ts` | 后端 OpenAPI 重新生成 |

**不动的**（避免误伤）：

- `AiKeywordExtractor` / `KEYWORD_EXTRACTION` — 用例规划推荐降级态仍在用
- `AiConstants.CANDIDATE_LIMIT_PER_KEYWORD` — 用例规划推荐仍在用
- `stores/ai.ts` 的 `semanticDegraded`（全局能力位）与 BugDedup / CasePlanRecommend 的响应字段 — 与本功能无关
- `AiCaseController` — 仅路由 + `@Valid`，DTO 校验自动生效，无需改

---

## 二、后端详细改动

### 1. `AiMissingPointReqDTO`

```java
// 删除
@Size(max = 20, message = "关键词数量不能超过 20")
private List<String> keywords;

// 新增
@NotEmpty(message = "文档列表不能为空")
@Size(max = 20, message = "文档数量不能超过 20")
private List<UUID> documentIds;
```

`text` / `requirementIds` / `modelId` 保留不变；Javadoc 同步更新。
校验口径：`documentIds` 不可空（DTO 层 `@NotEmpty` + service 层防御）；`text` 与 `requirementIds` 至少一项非空（service 层）。

### 2. `AiMissingPointRespDTO`

删除 `semanticDegraded` 字段，只保留 `points`。

### 3. `AiMissingPointServiceImpl`（核心）

- **依赖**：移除 `AiKeywordExtractor` 字段与 import；移除 `KEYWORD_TASK_INSTRUCTION` 常量
- **类 Javadoc**：改为文档级全量检索描述
- **`analyze()` 重写**：
  1. 校验：`documentIds` 非空（防御，DTO 层已有 `@NotEmpty`）+ `text`/`requirementIds` 至少一项非空 → 否则 `VALIDATION_FAILED`
  2. 需求归一：`requirementContextAssembler.assemble(projectId, requirementIds, text, null)` — 第 4 参前缀块传 `null`（删除【关键词】前缀拼接）
  3. **删除**关键词确定步骤（入参关键词 / LLM 抽取均移除）
  4. `retrieveCandidates(projectId, reqDTO.getDocumentIds())`
  5. LLM 比对、结构断言、幻觉过滤 — 逻辑不变
  6. `response()` 中删除 `setSemanticDegraded(true)`
- **`retrieveCandidates(UUID projectId, List<UUID> documentIds)` 重写**：

```java
List<TestCaseDocument> documents = testCaseDocumentMapper.selectBatchIds(documentIds);
if (documents.isEmpty()) return List.of();
// 模块树：全量 ProjectModule（父级目录） + 指定文档
List<ModuleTreeNode> allNodes = new ArrayList<>();
projectModuleMapper.listByProjectId(projectId).forEach(m -> allNodes.add(ModuleTreeNode.fromProjectModule(m)));
documents.forEach(d -> allNodes.add(ModuleTreeNode.fromTestCaseDocument(d)));
Map<UUID, String> modulePathById = AiModuleTreeSupport.buildModulePaths(allNodes);
// 全量取 case 节点（复用现成 listCaseNodesByDocumentIds，无关键词、无限额）
List<TestCaseNode> nodes = testCaseNodeMapper.listCaseNodesByDocumentIds(documentIds);
// 组装 Candidate（token 预算截断仍由 buildComparisonData 的 CANDIDATE_TOKEN_BUDGET 兜底）
```

- 不再调用 `listByProjectId` 取全部文档做 ID 池，不再 ILIKE，不再用 `CANDIDATE_LIMIT_PER_KEYWORD`

**Token 预算论证**：单文档通常 20–100 条用例 × ~50 token/行 ≈ 5,000 token < `CANDIDATE_TOKEN_BUDGET`(8,000)，超出时 `buildComparisonData` 静默截断尾部（已有防御逻辑），网关 24K 输入上限安全。

### 4. 测试 `AiMissingPointServiceImplTest`

- 删 `@Mock AiKeywordExtractor` 及相关 verify
- `req()` / `reqWithText()` 辅助方法改为设置 `documentIds`（如 `List.of(DOC_ID)`）
- `stubProjectModules()` 改 stub `selectBatchIds`
- 所有 `listCaseNodesByDocumentIdsAndKeyword(...)` 改 stub `listCaseNodesByDocumentIds(...)`
- 删 `assertTrue(resp.isSemanticDegraded())` 断言
- `textOnly_extractsKeywordsOnceThenAnalyzes` 用例改为「text 场景不触发关键词抽取、直接全量检索」
- `allInputsEmpty_throws` 保留（text/requirementIds 全空场景）
- 类 Javadoc 更新

---

## 三、前端详细改动

### 1. `services/ai.ts`

```typescript
export interface AiMissingPointReq {
  /** 文档 ID 列表（当前脑图文档），必填 */
  documentIds: string[]
  /** 需求文本，与 requirementIds 至少一项非空 */
  text?: string
  requirementIds?: string[]
  modelId?: string
}
```

### 2. `types/ai.ts`

`AiMissingPointResult` 删 `semanticDegraded`，保留 `points`。

### 3. `useMissingPointsPanel.ts`

- 删 `keywords` ref 及导出、`docId` watch 中的重置行
- `hasAnyInput`：`text.trim() !== '' || requirementIds.length > 0`
- `buildReq()`：

```typescript
if (!hasAnyInput.value) {
  ElMessage.warning('请输入需求文本或选择需求')
  return null
}
const req: AiMissingPointReq = {
  documentIds: [docId()],
  text: text.value.trim() || undefined,
  requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
  modelId: aiStore.effectiveModelId() ?? undefined,
}
```

### 4. `MissingPointsPanel.vue`

- 删除整段关键词输入 `mp-field`（`el-select` + label）
- 删 `keywordRef`、`focusKeyword()`；新增 `textRef` 挂到 `el-input` textarea，打开抽屉时焦点落需求文本
- 删 `semanticDegraded` 的 `el-alert` 提示块
- 需求文本 placeholder：`粘贴需求描述文本（与需求池至少填一项）`
- 类 Javadoc：「三组输入（关键词 / 需求文本 / 需求池）」→「两组输入（需求文本 / 需求池）」

### 5. `useMissingPointsPanel.spec.ts`

- `makeResult()` 删 `semanticDegraded`
- 删「keywords 为空数组」「有 keywords 时为 true」等用例
- 所有 `panel.keywords.value = ['kw1']` 触发条件改为 `panel.text.value = '...'`
- 请求断言改为含 `documentIds: ['doc-1']`（`init()` 中 docId 为 `'doc-1'`）
- 警告文案断言改为 `'请输入需求文本或选择需求'`

---

## 四、验证与提交

1. `node scripts/validate.mjs --backend`（含 `mvn test`，C8 覆盖率）
2. `node scripts/validate.mjs --frontend`（lint + typecheck + 测试/覆盖率）
3. contract.d.ts 若需重新生成则执行对应生成脚本
4. 提交（C7）：`♻️ refactor(ai/missing-point): 候选检索改为文档级全量获取，移除关键词与降级字段`
   - **不夹带** `AiModels.java` / `AiConstants.java` / `BugDetailPage.vue` 的既有未提交变更

---

## 五、执行顺序

后端（DTO → Service → 测试）→ 前端（types → services → composable → 组件 → 测试）→ 双端验证 → 自检 C1–C11 → 交付
