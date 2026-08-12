<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { analyzeMissingPoints, type AiMissingPointReq } from '@/services/ai'
import { fetchModuleTree, getDocumentRequirements } from '@/services/project'
import type { AiMissingPoint, AiMissingPointResult, RequirementSummary } from '@/types'
import {
  buildMissingPointText,
  collectDocumentOptions,
  pickPreselectDocument,
  type MissingPointDocumentOption,
} from './missingPoints'

/**
 * 遗漏测试点分析面板（US-AI-007，交互设计第 4 章）：
 * 关键词 / 需求文本 / 需求条目三态输入至少一项 → 同步长调用（70s 超时，可取消）→
 * 勾选结果「转用例生成」：预选出现次数最多的建议模块对应文档，直达用例页预填 AI 生成抽屉。
 */
const props = defineProps<{ docId: string }>()
const visible = defineModel<boolean>({ required: true })
const router = useRouter()

const keywords = ref<string[]>([])
const text = ref('')
const requirementIds = ref<string[]>([])
const requirementTitles = ref<RequirementSummary[]>([])
const reqSelectorVisible = ref(false)

const analyzing = ref(false)
const result = ref<AiMissingPointResult | null>(null)
const checkedIndexes = ref<Set<number>>(new Set())

let controller: AbortController | null = null

const hasAnyInput = computed(
  () => keywords.value.length > 0 || text.value.trim() !== '' || requirementIds.value.length > 0,
)

const checkedPoints = computed<AiMissingPoint[]>(() =>
  (result.value?.points ?? []).filter((_, index) => checkedIndexes.value.has(index)),
)
const allChecked = computed(
  () => result.value !== null && checkedIndexes.value.size === result.value.points.length,
)

function toggleAll(checked: boolean): void {
  if (!result.value) return
  checkedIndexes.value = checked
    ? new Set(result.value.points.map((_, index) => index))
    : new Set<number>()
}

function toggleItem(index: number, checked: boolean): void {
  const next = new Set(checkedIndexes.value)
  if (checked) next.add(index)
  else next.delete(index)
  checkedIndexes.value = next
}

function handleRequirementConfirm(selected: RequirementSummary[]): void {
  requirementIds.value = selected.map((r) => r.id)
  // 选取器仅回传 id，标题可能为空（跨页场景）；从上次已选补全，避免标签只剩关闭按钮
  requirementTitles.value = selected.map((r) => {
    if (r.title) return r
    return requirementTitles.value.find((prev) => prev.id === r.id) ?? r
  })
}

function removeRequirement(id: string): void {
  requirementIds.value = requirementIds.value.filter((rid) => rid !== id)
  requirementTitles.value = requirementTitles.value.filter((r) => r.id !== id)
}

/** 打开时默认带入当前文档已关联的需求条目（交互设计 4.2，同 AI 生成抽屉 6.3） */
async function loadDocumentRequirements(): Promise<void> {
  try {
    const list = await getDocumentRequirements(props.docId)
    requirementIds.value = list.map((r) => r.id)
    requirementTitles.value = list
  } catch (err) {
    // 加载失败不阻断输入，保持空态由用户手动选取
    ElMessage.error(err instanceof Error ? err.message : '加载文档关联需求失败')
  }
}

// 每次打开重新同步关联条目，上次手动调整不残留（同 AiGeneratePanel）
watch(
  visible,
  (open) => {
    if (open) void loadDocumentRequirements()
  },
  { immediate: true },
)

function buildReq(): AiMissingPointReq | null {
  if (!hasAnyInput.value) {
    ElMessage.warning('请至少输入关键词、需求文本或选择需求')
    return null
  }
  const req: AiMissingPointReq = {
    keywords: keywords.value.length ? keywords.value : undefined,
    text: text.value.trim() || undefined,
    requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
  }
  return req
}

async function analyze(): Promise<void> {
  const req = buildReq()
  if (!req) return
  analyzing.value = true
  result.value = null
  const { controller: c, promise } = analyzeMissingPoints(req)
  controller = c
  try {
    const resp = await promise
    result.value = resp
    checkedIndexes.value = new Set(resp.points.map((_, index) => index))
  } catch (err) {
    // 用户主动取消不提示（同步调用无部分结果）
    if (controller?.signal.aborted) return
    ElMessage.error(err instanceof Error ? err.message : '分析失败')
  } finally {
    analyzing.value = false
    controller = null
  }
}

function cancelAnalyze(): void {
  controller?.abort()
  controller = null
  analyzing.value = false
}

// ==================== 转用例生成（交互设计 4.3） ====================

const documentOptions = ref<MissingPointDocumentOption[]>([])
const docSelectVisible = ref(false)
const targetDocId = ref('')

async function openTargetSelect(): Promise<void> {
  const points = checkedPoints.value
  if (!points.length) {
    ElMessage.warning('请至少勾选一个遗漏测试点')
    return
  }
  try {
    const tree = await fetchModuleTree()
    documentOptions.value = collectDocumentOptions(tree)
    if (!documentOptions.value.length) {
      ElMessage.warning('项目暂无文档，无法生成用例')
      return
    }
    targetDocId.value = pickPreselectDocument(documentOptions.value, points)
    docSelectVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
  }
}

function toCaseGenerate(): void {
  const points = checkedPoints.value
  if (!points.length || !targetDocId.value) return
  docSelectVisible.value = false
  visible.value = false
  // 勾选点拼接为需求文本直达用例页，预填「AI 生成用例」抽屉（详细设计 3.3）
  router.push({
    name: 'FunctionalTesting',
    query: { tab: 'cases', documentId: targetDocId.value, aiGenerate: buildMissingPointText(points) },
  })
}

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <el-drawer
    v-model="visible"
    size="600px"
    :modal="true"
    :close-on-click-modal="true"
    modal-class="mp-drawer-modal"
    @closed="cancelAnalyze"
  >
    <template #header>
      <span class="mp-title"><el-icon><MagicStick /></el-icon> 遗漏测试点分析</span>
    </template>

    <div class="mp">
      <!-- 三态输入：关键词 / 需求文本 / 需求条目，至少一项非空（详细设计 3.3） -->
      <div class="mp-inputs">
        <div class="mp-field">
          <div class="mp-field__label">关键词</div>
          <el-select
            v-model="keywords"
            multiple
            filterable
            allow-create
            default-first-option
            :disabled="analyzing"
            placeholder="输入关键词，回车确认"
          >
            <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
          </el-select>
        </div>

        <div class="mp-field">
          <div class="mp-field__label">需求文本</div>
          <el-input
            v-model="text"
            type="textarea"
            :rows="5"
            maxlength="20000"
            :disabled="analyzing"
            placeholder="粘贴需求描述文本（可空；填写时后端先抽取关键词再分析）"
          />
        </div>

        <div class="mp-field">
          <div class="mp-field__bar">
            <span class="mp-field__label">需求池</span>
            <el-button size="small" :disabled="analyzing" @click="reqSelectorVisible = true">
              {{ requirementTitles.length ? '调整需求' : '选择需求' }}
            </el-button>
          </div>
          <div v-if="requirementTitles.length" class="mp-req-tags">
            <el-tag
              v-for="item in requirementTitles"
              :key="item.id"
              size="small"
              closable
              @close="removeRequirement(item.id)"
            >
              {{ item.title }}
            </el-tag>
          </div>
          <span v-else class="mp-field__hint">未选择需求，将仅依据输入文本生成</span>
        </div>
      </div>

      <div class="mp-actions">
        <el-button type="primary" :loading="analyzing" :disabled="!hasAnyInput" @click="analyze">
          {{ result ? '重新分析' : '开始分析' }}
        </el-button>
        <el-button v-if="analyzing" @click="cancelAnalyze">取消</el-button>
      </div>

      <!-- 关键词版恒为语义降级，顶部提示（交互设计 4.3） -->
      <el-alert
        v-if="result && result.semanticDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="当前为关键词匹配结果"
      />

      <template v-if="result">
        <div class="mp-result-head">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="checkedIndexes.size > 0 && !allChecked"
            @update:model-value="(v) => toggleAll(v === true)"
          >全选</el-checkbox>
          <span class="mp-result-count">共 {{ result.points.length }} 条，已选 {{ checkedIndexes.size }} 条</span>
        </div>

        <div v-if="result.points.length" class="mp-list">
          <div v-for="(point, index) in result.points" :key="index" class="mp-item">
            <el-checkbox
              :model-value="checkedIndexes.has(index)"
              @update:model-value="(v) => toggleItem(index, v === true)"
            />
            <div class="mp-item__body">
              <div class="mp-item__title">{{ point.title }}</div>
              <div class="mp-item__desc">{{ point.description }}</div>
              <div v-if="point.suggestedModulePath" class="mp-item__tags">
                <el-tag size="small" effect="plain" type="info">
                  建议模块：{{ point.suggestedModulePath }}
                </el-tag>
              </div>
              <div v-if="point.relatedCaseTitles.length" class="mp-item__related">
                <span class="mp-item__related-label">相关用例：</span>
                <el-tag
                  v-for="title in point.relatedCaseTitles"
                  :key="title"
                  size="small"
                  effect="plain"
                  class="mp-item__related-tag"
                >
                  {{ title }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="未发现遗漏测试点" :image-size="72" />
      </template>

      <div v-if="result && result.points.length" class="mp-footer">
        <el-button type="primary" :disabled="!checkedIndexes.size" @click="openTargetSelect">
          转用例生成（{{ checkedIndexes.size }}）
        </el-button>
      </div>
    </div>

    <RequirementSelector
      v-model="reqSelectorVisible"
      :selected-ids="requirementIds"
      @confirm="handleRequirementConfirm"
    />

    <el-dialog v-model="docSelectVisible" title="选择目标文档" width="440px" append-to-body>
      <div class="mp-doc-tip">默认已预选出现次数最多的建议模块，可更换</div>
      <el-select v-model="targetDocId" filterable placeholder="搜索文档路径" class="mp-doc-select">
        <el-option v-for="doc in documentOptions" :key="doc.id" :label="doc.path" :value="doc.id" />
      </el-select>
      <template #footer>
        <el-button @click="docSelectVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!targetDocId" @click="toCaseGenerate">生成用例</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped lang="scss">
/* 透明遮罩：点击抽屉外空白处自动关闭，同时不压暗画布（交互设计 4.2） */
:deep(.mp-drawer-modal) {
  background: transparent;
}

.mp-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.mp {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mp-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mp-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mp-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.mp-field__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mp-field__hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.mp-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mp-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mp-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mp-result-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.mp-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.mp-item__body {
  flex: 1;
  min-width: 0;
}

.mp-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mp-item__desc {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
  white-space: pre-wrap;
}

.mp-item__tags {
  margin-top: 6px;
}

.mp-item__related {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-item__related-label {
  flex-shrink: 0;
}

.mp-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.mp-doc-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mp-doc-select {
  width: 100%;
}
</style>
