<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RequirementSelector from '@/components/project/RequirementSelector.vue'
import { regressionRecommend, type AiRegressionRecommendReq } from '@/services/ai'
import type { AiRegressionRecommendItem, AiRegressionRecommendResult, RequirementSummary } from '@/types'

/**
 * 回归测试用例子集推荐弹窗（US-AI-018，交互设计第 6 章）：
 * 模块列表 / 变更说明 / 需求条目三态输入至少一项 → 同步长调用（70s 超时，可取消）→
 * 勾选结果「带入计划关联」：仅抛出 caseNodeId 清单，由计划详情页解析归属文档并并入既有 CaseSelector 流程。
 */
const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  'bring-into-plan': [caseNodeIds: string[]]
}>()

const modules = ref<string[]>([])
const text = ref('')
const requirementIds = ref<string[]>([])
const requirementTitles = ref<RequirementSummary[]>([])
const reqSelectorVisible = ref(false)

const recommending = ref(false)
const result = ref<AiRegressionRecommendResult | null>(null)
const checkedIndexes = ref<Set<number>>(new Set())

let controller: AbortController | null = null

const hasAnyInput = computed(
  () => modules.value.length > 0 || text.value.trim() !== '' || requirementIds.value.length > 0,
)

const checkedItems = computed<AiRegressionRecommendItem[]>(() =>
  (result.value?.items ?? []).filter((_, index) => checkedIndexes.value.has(index)),
)
const allChecked = computed(
  () => result.value !== null && checkedIndexes.value.size === result.value.items.length,
)

const matchTypeLabel: Record<AiRegressionRecommendItem['matchType'], string> = {
  module: '模块匹配',
  semantic: '语义匹配',
  both: '双命中',
}

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

function buildReq(): AiRegressionRecommendReq | null {
  if (!hasAnyInput.value) {
    ElMessage.warning('请至少输入变更模块、变更说明或选择需求条目')
    return null
  }
  const req: AiRegressionRecommendReq = {
    modules: modules.value.length ? modules.value : undefined,
    text: text.value.trim() || undefined,
    requirementIds: requirementIds.value.length ? requirementIds.value : undefined,
  }
  return req
}

async function recommend(): Promise<void> {
  const req = buildReq()
  if (!req) return
  recommending.value = true
  result.value = null
  const { controller: c, promise } = regressionRecommend(req)
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

function handleBringIntoPlan(): void {
  const items = checkedItems.value
  if (!items.length) {
    ElMessage.warning('请至少勾选一个推荐用例')
    return
  }
  emit('bring-into-plan', items.map((item) => item.caseNodeId))
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
      <span class="rr-title"><el-icon><MagicStick /></el-icon> 回归用例子集推荐</span>
    </template>

    <div class="rr">
      <!-- 三态输入：模块列表 / 变更说明 / 需求条目，至少一项非空（详细设计 3.5） -->
      <div class="rr-inputs">
        <div class="rr-field">
          <div class="rr-field__label">模块列表</div>
          <el-select
            v-model="modules"
            multiple
            filterable
            allow-create
            default-first-option
            :disabled="recommending"
            placeholder="输入变更模块名，回车确认"
          >
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
        </div>

        <div class="rr-field">
          <div class="rr-field__label">变更说明</div>
          <el-input
            v-model="text"
            type="textarea"
            :rows="3"
            maxlength="20000"
            :disabled="recommending"
            placeholder="粘贴变更说明文本（可空；填写时后端抽取关键词再检索）"
          />
        </div>

        <div class="rr-field">
          <div class="rr-field__label">需求条目</div>
          <div class="rr-field__row">
            <el-button size="small" :disabled="recommending" @click="reqSelectorVisible = true">
              {{ requirementTitles.length ? '调整条目' : '＋ 从需求池选取' }}
            </el-button>
            <div v-if="requirementTitles.length" class="rr-req-tags">
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
            <span v-else class="rr-field__hint">未选择需求条目</span>
          </div>
        </div>
      </div>

      <div class="rr-actions">
        <el-button type="primary" :loading="recommending" :disabled="!hasAnyInput" @click="recommend">
          {{ result ? '重新推荐' : '开始推荐' }}
        </el-button>
        <el-button v-if="recommending" @click="cancelRecommend">取消</el-button>
      </div>

      <!-- 语义降级提示条（交互设计 6.2） -->
      <el-alert
        v-if="result && result.semanticDegraded"
        type="warning"
        :closable="false"
        show-icon
        title="当前为模块名/关键词匹配结果"
      />

      <template v-if="result">
        <div class="rr-result-head">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="checkedIndexes.size > 0 && !allChecked"
            @update:model-value="(v) => toggleAll(v === true)"
          >全选</el-checkbox>
          <span class="rr-result-count">共 {{ result.items.length }} 条，已选 {{ checkedIndexes.size }} 条</span>
        </div>

        <div v-if="result.items.length" class="rr-list">
          <div v-for="(item, index) in result.items" :key="item.caseNodeId" class="rr-item">
            <el-checkbox
              :model-value="checkedIndexes.has(index)"
              @update:model-value="(v) => toggleItem(index, v === true)"
            />
            <div class="rr-item__body">
              <div class="rr-item__title">{{ item.title }}</div>
              <div class="rr-item__tags">
                <el-tag size="small" effect="plain" type="info">{{ item.modulePath }}</el-tag>
                <el-tag size="small" effect="plain">{{ matchTypeLabel[item.matchType] }}</el-tag>
                <span v-if="item.reason" class="rr-item__reason">{{ item.reason }}</span>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="未发现推荐用例" :image-size="72" />
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!checkedItems.length" @click="handleBringIntoPlan">
        带入计划关联（{{ checkedItems.length }}）
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
.rr-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.rr {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rr-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rr-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rr-field__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.rr-field__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.rr-field__hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.rr-req-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rr-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rr-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rr-result-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.rr-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.rr-item__body {
  flex: 1;
  min-width: 0;
}

.rr-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rr-item__tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.rr-item__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
</style>
