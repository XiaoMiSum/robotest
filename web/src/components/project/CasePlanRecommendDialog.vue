<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { planRecommend, type AiCasePlanRecommendReq } from '@/services/ai'
import type { AiCasePlanRecommendItem, AiCasePlanRecommendResult, RequirementSummary } from '@/types'

/**
 * 用例规划智能推荐弹窗（US-AI-018，交互设计第 6 章）：
 * 需求条目 / 需求文本至少一项 → 同步长调用（70s 超时，可取消）→
 * 勾选结果「加入评审 / 加入计划」：仅抛出 caseNodeId 清单，由评审/计划详情页解析归属文档并并入既有 CaseSelector 流程。
 * 推荐目标由 target 决定按钮文案，评审/计划双入口共用同一组件。
 */
const visible = defineModel<boolean>({ required: true })

const props = defineProps<{
  /** 当前评审/计划已纳入的用例节点 ID，推荐接口排除这些用例（不重复推荐，4.5） */
  excludeCaseNodeIds: string[]
  /** 推荐目标：决定底部「加入评审 / 加入计划」按钮文案 */
  target: 'review' | 'plan'
}>()

const emit = defineEmits<{
  'bring-in': [caseNodeIds: string[]]
}>()

const text = ref('')
const requirementIds = ref<string[]>([])
const requirementTitles = ref<RequirementSummary[]>([])
const reqSelectorVisible = ref(false)

const recommending = ref(false)
const result = ref<AiCasePlanRecommendResult | null>(null)
const checkedIndexes = ref<Set<number>>(new Set())

let controller: AbortController | null = null

const hasAnyInput = computed(
  () => text.value.trim() !== '' || requirementIds.value.length > 0,
)

const checkedItems = computed<AiCasePlanRecommendItem[]>(() =>
  (result.value?.items ?? []).filter((_, index) => checkedIndexes.value.has(index)),
)
const allChecked = computed(
  () => result.value !== null && checkedIndexes.value.size === result.value.items.length,
)

const actionLabel = computed(() => (props.target === 'review' ? '加入评审' : '加入计划'))

function toggleAll(checked: boolean): void {
  if (!result.value) return
  checkedIndexes.value = checked
    ? new Set(result.value.items.map((_, index) => index))
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
  requirementTitles.value = selected
}

function removeRequirement(id: string): void {
  requirementIds.value = requirementIds.value.filter((rid) => rid !== id)
  requirementTitles.value = requirementTitles.value.filter((r) => r.id !== id)
}

function buildReq(): AiCasePlanRecommendReq | null {
  if (!hasAnyInput.value) {
    ElMessage.warning('请至少输入需求文本或选择需求条目')
    return null
  }
  const req: AiCasePlanRecommendReq = {
    text: text.value.trim() || undefined,
    requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
    excludeCaseNodeIds: props.excludeCaseNodeIds.length ? props.excludeCaseNodeIds : undefined,
  }
  return req
}

async function recommend(): Promise<void> {
  const req = buildReq()
  if (!req) return
  recommending.value = true
  result.value = null
  const { controller: c, promise } = planRecommend(req)
  controller = c
  try {
    const resp = await promise
    result.value = resp
    checkedIndexes.value = new Set(resp.items.map((_, index) => index))
  } catch (err) {
    // 用户主动取消不提示（同步调用无部分结果）
    if (controller?.signal.aborted) return
    ElMessage.error(err instanceof Error ? err.message : '推荐失败')
  } finally {
    recommending.value = false
    controller = null
  }
}

function cancelRecommend(): void {
  controller?.abort()
  controller = null
  recommending.value = false
}

function handleBringIn(): void {
  const items = checkedItems.value
  if (!items.length) {
    ElMessage.warning('请至少勾选一个推荐用例')
    return
  }
  emit('bring-in', items.map((item) => item.caseNodeId))
}

// 弹窗关闭不保留本次推荐结果（交互设计 6.2），并中止进行中的长调用
function handleClosed(): void {
  cancelRecommend()
  result.value = null
  checkedIndexes.value = new Set()
}

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <el-dialog
    v-model="visible"
    width="640px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <template #header>
      <span class="cpr-title"><el-icon><MagicStick /></el-icon> AI 推荐用例</span>
    </template>

    <div class="cpr">
      <!-- 需求输入：条目 / 文本至少一项非空（详细设计 3.5）；按钮三区职责化：选取随字段行，发起在操作行 -->
      <div class="cpr-inputs">
        <div class="cpr-field">
          <div class="cpr-field__bar">
            <div class="cpr-field__label">需求条目</div>
            <el-button size="small" :disabled="recommending" @click="reqSelectorVisible = true">
              {{ requirementTitles.length ? '调整条目' : '＋ 从需求池选取' }}
            </el-button>
          </div>
          <div class="cpr-field__row">
            <div v-if="requirementTitles.length" class="cpr-req-tags">
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
            <span v-else class="cpr-field__hint">未选择需求条目</span>
          </div>
        </div>

        <div class="cpr-field">
          <div class="cpr-field__label">需求文本</div>
          <el-input
            v-model="text"
            type="textarea"
            :rows="3"
            maxlength="20000"
            :disabled="recommending"
            placeholder="粘贴需求文本（可空；填写时后端抽取关键词再检索）"
          />
        </div>
      </div>

      <div class="cpr-actions">
        <el-button v-if="recommending" @click="cancelRecommend">取消</el-button>
        <el-button type="primary" :loading="recommending" :disabled="!hasAnyInput" @click="recommend">
          {{ result ? '重新推荐' : '开始推荐' }}
        </el-button>
      </div>

      <!-- 语义降级提示条（交互设计 6.2） -->
      <el-alert
        v-if="result && result.semanticDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="当前为关键词匹配结果"
      />

      <template v-if="result">
        <div class="cpr-result-head">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="checkedIndexes.size > 0 && !allChecked"
            @update:model-value="(v) => toggleAll(v === true)"
          >全选</el-checkbox>
          <span class="cpr-result-count">共 {{ result.items.length }} 条，已选 {{ checkedIndexes.size }} 条</span>
        </div>

        <div v-if="result.items.length" class="cpr-list">
          <div v-for="(item, index) in result.items" :key="item.caseNodeId" class="cpr-item">
            <el-checkbox
              :model-value="checkedIndexes.has(index)"
              @update:model-value="(v) => toggleItem(index, v === true)"
            />
            <div class="cpr-item__body">
              <div class="cpr-item__title">{{ item.title }}</div>
              <div class="cpr-item__tags">
                <el-tag size="small" effect="plain" type="info">{{ item.modulePath }}</el-tag>
                <span v-if="item.reason" class="cpr-item__reason">{{ item.reason }}</span>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="未发现推荐用例" :image-size="72" />
      </template>
    </div>

    <template #footer>
      <el-button
        v-if="result"
        type="primary"
        :disabled="!checkedItems.length"
        @click="handleBringIn"
      >
        {{ actionLabel }}（{{ checkedItems.length }}）
      </el-button>
    </template>

    <RequirementSelector
      v-model="reqSelectorVisible"
      :selected-ids="requirementIds"
      @confirm="handleRequirementConfirm"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.cpr-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.cpr {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cpr-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cpr-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cpr-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cpr-field__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* 需求条目行：标签居左、选取按钮靠最右，已选标签独占下一行 */
.cpr-field__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cpr-field__hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.cpr-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cpr-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.cpr-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cpr-result-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cpr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.cpr-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.cpr-item__body {
  flex: 1;
  min-width: 0;
}

.cpr-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cpr-item__tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.cpr-item__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
</style>
