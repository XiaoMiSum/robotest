<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type {
  AiArtifactConfirmItem,
  AiArtifactSummary,
  SelectedCaseNode,
  TestPlanCreatePayload,
  TestReviewCreatePayload,
} from '@/types'
import AiConfirmReceipt from '@/components/project/ai/AiConfirmReceipt.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import ReviewCreateDialog from '@/components/project/functional-testing/review/ReviewCreateDialog.vue'
import PlanCreateDialog from '@/components/project/functional-testing/plan/PlanCreateDialog.vue'
import { useAiArtifactReview } from '@/composables/project/ai/useAiArtifactReview'
import {
  useSelectionReview,
  type SelectionReviewItem,
} from '@/composables/project/ai/useSelectionReview'

const props = defineProps<{
  taskId: string
  taskType: 'review_selection' | 'plan_selection'
  artifacts: AiArtifactSummary[]
  canConfirm: boolean
}>()

const emit = defineEmits<{ confirmed: []; leave: [] }>()

const router = useRouter()
const isPlan = computed(() => props.taskType === 'plan_selection')

const base = useAiArtifactReview(
  props.taskId,
  () => props.artifacts,
  () => props.canConfirm,
  () => {
    base.syncRows()
    emit('confirmed')
  },
)
const sel = useSelectionReview(props.taskId)

base.init()
void sel.load()

function reload(): void {
  void sel.load()
}

const caseSelectorVisible = ref(false)
const createDialogVisible = ref(false)
const presetNodes = ref<SelectedCaseNode[]>([])
const rejectVisible = ref(false)
const rejectNote = ref('')

const adopted = computed(() =>
  base.rows.value.some(
    (row) => row.confirmStatus === 'adopted' || row.confirmStatus === 'adopted_edited',
  ),
)
/** 产物全驳回 → 审核区空态「已全部驳回」（交互 2.4.3 同口径） */
const allRejected = computed(
  () =>
    base.rows.value.length > 0 &&
    base.rows.value.every((row) => row.confirmStatus === 'rejected'),
)
const canCreate = computed(
  () => base.canConfirm.value && sel.items.value.length > 0 && !adopted.value,
)

const createSummary = computed(() =>
  isPlan.value
    ? `将创建 1 条计划记录（含 ${sel.items.value.length} 个用例）`
    : `将创建 1 条评审记录（含 ${sel.items.value.length} 个用例）`,
)

/** 卡片分组：评审单组「推荐进评审」，计划按轮次分组（交互 2.4 卡片列表） */
const groups = computed<{ label: string; items: SelectionReviewItem[] }[]>(() => {
  if (!isPlan.value) return [{ label: '推荐进评审', items: sel.items.value }]
  const buckets = new Map<number | 'none', SelectionReviewItem[]>()
  for (const item of sel.items.value) {
    const key = item.round ?? 'none'
    const bucket = buckets.get(key)
    if (bucket) bucket.push(item)
    else buckets.set(key, [item])
  }
  return [...buckets.entries()]
    .sort(([left], [right]) => {
      if (left === 'none') return 1
      if (right === 'none') return -1
      return left - right
    })
    .map(([round, items]) => ({
      label: round === 'none' ? '推荐进计划 · 未分轮' : `推荐进计划 · 第 ${round} 轮`,
      items,
    }))
})

function onManualAdd(nodes: SelectedCaseNode[]): void {
  void sel.addCases(nodes)
}

/** 确认前补齐 documentId，组装既有创建弹窗的预选用例（交互 2.4 提交行） */
async function openCreate(): Promise<void> {
  if (!canCreate.value) return
  await sel.resolveDocumentIds()
  const { nodes, missing } = sel.selectedNodes()
  if (nodes.length === 0) {
    ElMessage.warning('所选用例无法定位所属文档，请调整后重试')
    return
  }
  if (missing > 0) {
    ElMessage.warning(`${missing} 个用例已失效或无法定位，已排除出创建范围`)
  }
  presetNodes.value = nodes
  createDialogVisible.value = true
}

/** 创建弹窗载荷转圈选确认的 target.createParams，落库后跳转对应评审 / 计划页 */
async function handleCreate(
  payload: TestReviewCreatePayload | TestPlanCreatePayload,
): Promise<void> {
  await base.submitItems([{ key: 'sel-1', action: 'adopted' }], {
    createParams: { ...payload },
  })
  createDialogVisible.value = false
  const result = base.receipt.value[0]
  if (result?.key === 'sel-1' && result.success && result.createdId) {
    void router.push(
      isPlan.value
        ? `/workspace/projects/plans/${result.createdId}`
        : `/workspace/projects/reviews/${result.createdId}`,
    )
  }
}

function openReject(): void {
  rejectNote.value = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  const item: AiArtifactConfirmItem = { key: 'sel-1', action: 'rejected' }
  const note = rejectNote.value.trim()
  if (note) item.note = note
  await base.submitItems([item])
  rejectVisible.value = false
}
</script>

<template>
  <section class="sel-review">
    <header class="sel-review__head">
      <div class="sel-review__head-main">
        <h2 class="sel-review__title">
          {{ isPlan ? '计划圈选建议审核' : '评审圈选建议审核' }}
        </h2>
        <span class="sel-review__muted">
          已处理 {{ base.processed.value.processed }} / 共 {{ base.processed.value.total }}
        </span>
      </div>
      <span v-if="!canConfirm" class="sel-review__muted">
        当前账号无产物确认权限，仅可预览
      </span>
      <span v-else class="sel-review__muted">
        调整推荐后经创建弹窗落库；驳回不创建任何数据
      </span>
    </header>

    <div v-if="allRejected" class="sel-review__body sel-review__body--single">
      <el-empty description="已全部驳回，未创建任何数据" :image-size="80">
        <el-button type="primary" @click="emit('leave')">返回任务中心</el-button>
      </el-empty>
    </div>

    <div v-else-if="adopted" class="sel-review__body sel-review__body--single">
      <el-alert
        type="success"
        title="圈选建议已采纳"
        :description="`${isPlan ? '计划' : '评审'}已创建，可在对应列表页查看`"
        show-icon
        :closable="false"
      />
      <AiConfirmReceipt
        v-if="base.receipt.value.length > 0"
        :receipt="base.receipt.value"
        :confirming="base.confirming.value"
        @retry="base.retryResult"
        @clear="base.clearReceipt"
      />
    </div>

    <div v-else class="sel-review__body sel-review__body--single">
      <div v-loading="sel.loading.value || sel.resolving.value" class="sel-review__content">
        <el-alert
          v-if="sel.loadFailed.value"
          type="error"
          title="读取圈选建议失败"
          show-icon
          :closable="false"
          class="sel-review__alert"
        >
          <el-button link type="primary" @click="reload">重试</el-button>
        </el-alert>
        <el-alert
          v-else-if="!sel.loading.value && sel.items.value.length === 0"
          type="info"
          title="模型未推荐任何用例，可手动添加用例或驳回产物"
          show-icon
          :closable="false"
          class="sel-review__alert"
        />

        <div v-for="group in groups" :key="group.label" class="sel-review__group">
          <div class="sel-review__group-head">
            <span class="sel-review__group-title">{{ group.label }}</span>
            <span class="sel-review__muted">{{ group.items.length }} 条</span>
          </div>
          <ul class="sel-review__cards">
            <li v-for="item in group.items" :key="item.caseId" class="sel-review__card">
              <div class="sel-review__card-main">
                <div class="sel-review__card-title">
                  <span class="sel-review__card-name">{{ item.title }}</span>
                  <el-tag v-if="item.manual" size="small" type="info" effect="plain">
                    手动添加
                  </el-tag>
                </div>
                <p v-if="item.reason" class="sel-review__card-reason">{{ item.reason }}</p>
                <p v-else class="sel-review__card-reason sel-review__card-reason--muted">
                  手动添加，未附推荐理由
                </p>
              </div>
              <el-button
                v-if="canConfirm"
                link
                type="danger"
                :disabled="sel.resolving.value"
                @click="sel.removeItem(item.caseId)"
              >移除</el-button>
            </li>
          </ul>
        </div>
      </div>

      <footer v-if="canConfirm" class="sel-review__foot">
        <div class="sel-review__foot-actions">
          <el-button :disabled="sel.resolving.value" @click="caseSelectorVisible = true">
            手动添加用例
          </el-button>
          <el-button
            type="primary"
            :disabled="!canCreate || sel.resolving.value"
            :loading="base.confirming.value"
            @click="openCreate"
          >
            {{ isPlan ? '创建计划' : '创建评审' }}
          </el-button>
          <el-button
            type="danger"
            plain
            :loading="base.confirming.value"
            @click="openReject"
          >
            驳回（可附反馈）
          </el-button>
        </div>
        <span class="sel-review__muted">{{ createSummary }}</span>
      </footer>

      <AiConfirmReceipt
        v-if="base.receipt.value.length > 0"
        :receipt="base.receipt.value"
        :confirming="base.confirming.value"
        @retry="base.retryResult"
        @clear="base.clearReceipt"
      />
    </div>

    <CaseSelector v-model="caseSelectorVisible" @confirm="onManualAdd" />

    <!-- 确认前展示创建摘要并接入既有创建弹窗（交互 2.4 提交行） -->
    <ReviewCreateDialog
      v-if="!isPlan"
      v-model="createDialogVisible"
      :initial-selected-nodes="presetNodes"
      :submitting="base.confirming.value"
      @submit="handleCreate"
    />
    <PlanCreateDialog
      v-else
      v-model="createDialogVisible"
      :initial-selected-nodes="presetNodes"
      :submitting="base.confirming.value"
      @submit="handleCreate"
    />

    <!-- 驳回反馈 -->
    <el-dialog v-model="rejectVisible" title="驳回圈选产物" width="480px" :close-on-click-modal="false">
      <el-input
        v-model="rejectNote"
        type="textarea"
        :rows="4"
        :maxlength="base.NOTE_MAX_LENGTH"
        show-word-limit
        placeholder="反馈可选，用于改进后续推荐（不超过 500 字）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="base.confirming.value" @click="submitReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.sel-review {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.sel-review__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.sel-review__head-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.sel-review__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.sel-review__muted {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.sel-review__body--single {
  display: block;
}

.sel-review__content {
  min-height: 120px;
}

.sel-review__alert {
  margin-bottom: var(--space-sm);
}

.sel-review__group + .sel-review__group {
  margin-top: var(--space-md);
}

.sel-review__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--color-neutral-200);
}

.sel-review__group-title {
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.sel-review__cards {
  margin: 0;
  padding: 0;
  list-style: none;
}

.sel-review__card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
}

.sel-review__card-main {
  min-width: 0;
}

.sel-review__card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.sel-review__card-name {
  color: var(--color-neutral-900);
  font-size: var(--font-size-sm);
  font-weight: 550;
}

.sel-review__card-reason {
  margin: var(--space-xxs, 2px) 0 0;
  color: var(--color-neutral-700);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.sel-review__card-reason--muted {
  color: var(--color-neutral-400);
}

.sel-review__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-200);
}

.sel-review__foot-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
</style>
