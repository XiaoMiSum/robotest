<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAiStream, type AiStreamController } from '@/composables/useAiStream'
import { useAiStore } from '@/stores/ai'
import { batchCreateRequirements } from '@/services/project'
import type { AiRequirementSplitResult } from '@/types'
import AiModelSelect from '@/components/common/AiModelSelect.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'

/**
 * 需求文档 AI 拆分对话框（US-AI-019，交互设计 6.1.3）：
 * ① 粘贴整份文档 → SSE 流式拆分（虚拟进度条占位，done 帧一次成型）→ ② 按模块分组预览，
 * 行内编辑/删除 + 全选勾选，仅勾选条目批量入库（标题 = 模块名·需求点，aiGenerated=true）。
 * 关闭即丢弃未入库预览，不落库；入库成功后 emit('imported') 由父组件刷新列表。
 */
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ imported: [count: number] }>()

const aiStore = useAiStore()

type Phase = 'input' | 'streaming' | 'preview'
const phase = ref<Phase>('input')
// 输入区是否展开：与预览解耦。preview 阶段默认收起输入区（确认模式），
// 点击「重新拆分」后展开；未真正执行新拆分前旧结果仍保留可查看（用户确认流程）
const inputExpanded = ref(false)
// 预览是否展开：重新拆分模式（输入区展开）下默认收起预览避免争抢空间，
// 通过「展开拆分结果预览」按钮临时查看结果
const previewExpanded = ref(false)
const text = ref('')
const warnings = ref<string[]>([])
const controller = ref<AiStreamController | null>(null)
const importing = ref(false)

/** 预览条目：key 用于 v-for 追踪（模块-条目索引），editing 行内编辑态 */
interface PreviewEntry {
  key: string
  module: string
  title: string
  content: string
  checked: boolean
  editing: boolean
}
const entries = ref<PreviewEntry[]>([])

const totalCount = computed(() => entries.value.length)
const checkedCount = computed(() => entries.value.filter((e) => e.checked).length)
const allChecked = computed(
  () => entries.value.length > 0 && entries.value.every((e) => e.checked),
)
// 半选态：部分勾选时全选复选框呈 indeterminate，点击后走 toggleAll 全选
const partialChecked = computed(() => checkedCount.value > 0 && !allChecked.value)

/** 按模块分组（保持模块出现顺序），组头展示勾选计数 */
interface PreviewGroup {
  module: string
  items: PreviewEntry[]
  checked: number
}
const groups = computed<PreviewGroup[]>(() => {
  const map = new Map<string, PreviewEntry[]>()
  for (const e of entries.value) {
    const list = map.get(e.module)
    if (list) list.push(e)
    else map.set(e.module, [e])
  }
  const result: PreviewGroup[] = []
  for (const [module, items] of map) {
    result.push({ module, items, checked: items.filter((i) => i.checked).length })
  }
  return result
})

function startSplit(): void {
  if (!text.value.trim()) {
    ElMessage.warning('请先粘贴需求文档')
    return
  }
  phase.value = 'streaming'
  // 保留旧 entries/warnings：仅 done 帧到达才替换，失败/停止时旧结果可恢复
  controller.value = useAiStream({
    url: '/project/ai/requirements/split',
    body: { text: text.value, modelId: aiStore.effectiveModelId() ?? null },
    onEvent(event) {
      // 流式 delta 不再逐字上屏（见模板：以虚拟进度条占位），仅消费 done/error 终帧
      if (event.event === 'done') {
        const result = event.data as AiRequirementSplitResult
        warnings.value = result.warnings ?? []
        const list: PreviewEntry[] = []
        result.modules.forEach((m, mi) => {
          m.items.forEach((it, ii) => {
            list.push({
              key: `${mi}-${ii}`,
              module: m.module,
              title: it.title,
              content: it.content,
              checked: true,
              editing: false,
            })
          })
        })
        entries.value = list
        phase.value = 'preview'
        // 新结果就绪：收起输入区回到确认模式
        inputExpanded.value = false
      } else if (event.event === 'error') {
        const data = event.data as { message?: string }
        ElMessage.error(data.message ?? 'AI 拆分失败')
        restoreAfterSplitInterrupted()
      }
    },
    onError(error) {
      ElMessage.error(error.message)
      restoreAfterSplitInterrupted()
    },
    // done 帧未到达即关闭（服务端异常中断），回到可重试状态
    onClose() {
      if (phase.value === 'streaming') restoreAfterSplitInterrupted()
    },
  })
}

/** 拆分失败/停止/中断：有旧结果则回到「预览 + 输入区展开」，否则回到纯输入态 */
function restoreAfterSplitInterrupted(): void {
  if (entries.value.length > 0) {
    phase.value = 'preview'
    inputExpanded.value = true
  } else {
    phase.value = 'input'
    inputExpanded.value = false
  }
}

function stop(): void {
  controller.value?.cancel()
  controller.value = null
  restoreAfterSplitInterrupted()
}

/** 进入重新拆分模式：展开输入区，同时收起预览（避免编辑器与长列表争抢弹窗空间） */
function expandInput(): void {
  inputExpanded.value = true
  previewExpanded.value = false
}

function toggleAll(): void {
  const target = !allChecked.value
  entries.value.forEach((e) => (e.checked = target))
}

function removeEntry(entry: PreviewEntry): void {
  entries.value = entries.value.filter((e) => e !== entry)
}

function saveEdit(entry: PreviewEntry): void {
  if (!entry.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  entry.editing = false
}

async function importChecked(): Promise<void> {
  const selected = entries.value.filter((e) => e.checked)
  if (selected.length === 0) {
    ElMessage.warning('请至少勾选一条')
    return
  }
  importing.value = true
  try {
    const resp = await batchCreateRequirements({
      items: selected.map((e) => ({
        // 接口不感知模块，标题前缀由前端按预览分组拼接（3.1.7）
        title: `${e.module}·${e.title}`,
        content: e.content,
        aiGenerated: true,
      })),
    })
    ElMessage.success(`已入库 ${resp.count} 条`)
    emit('imported', resp.count)
    visible.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '批量入库失败')
  } finally {
    importing.value = false
  }
}

// 关闭即丢弃：取消未完成调用并清空预览，避免下次打开残留
function reset(): void {
  controller.value?.cancel()
  controller.value = null
  phase.value = 'input'
  inputExpanded.value = false
  previewExpanded.value = false
  text.value = ''
  warnings.value = []
  entries.value = []
}

onBeforeUnmount(() => {
  controller.value?.cancel()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="AI 拆分需求文档"
    width="720px"
    class="split-dialog"
    :close-on-click-modal="false"
    @closed="reset"
  >
    <!-- ① 输入（确认模式 preview 默认收起；点击「重新拆分」展开，未执行新拆分前旧结果保留） -->
    <div v-if="phase !== 'preview' || inputExpanded" class="split-dialog__input">
      <!-- 复用全站 Markdown 编辑器（流式中禁用）；超限截断由后端执行并以 warning 红字提示 -->
      <MarkdownEditor
        v-model="text"
        height="280px"
        :disabled="phase === 'streaming'"
        placeholder="粘贴整份需求文档，AI 将按模块/功能拆分为细粒度需求条目"
      />
      <div class="split-dialog__actions">
        <!-- 输入上限提示与操作按钮同行（底部），左对齐撑开，按钮靠右 -->
        <span class="split-dialog__hint">
          需求文档（Markdown，上限 20000 字符，超出部分将被忽略）
        </span>
        <el-progress
          v-if="phase === 'streaming'"
          :percentage="100"
          :indeterminate="true"
          :duration="1.2"
          class="split-dialog__progress"
        />
        <template v-else>
          <AiModelSelect />
          <el-button type="primary" :disabled="!text.trim()" @click="startSplit">
            <el-icon><MagicStick /></el-icon>AI 拆分
          </el-button>
        </template>
        <el-button v-if="phase === 'streaming'" @click="stop">停止</el-button>
      </div>
    </div>
    <!-- 确认模式：输入区收起，仅保留「重新拆分」入口（结果仍在下方可查看） -->
    <div v-else class="split-dialog__input">
      <div class="split-dialog__collapsed">
        <span class="split-dialog__hint">
          已生成 {{ totalCount }} 条拆分结果，可重新拆分或确认入库
        </span>
        <el-link type="primary" :underline="false" @click="expandInput">重新拆分</el-link>
      </div>
    </div>

    <!-- 拆分警告（超限截断等，红字提示） -->
    <el-alert
      v-for="(w, i) in warnings"
      :key="i"
      :title="w"
      type="warning"
      :closable="false"
      show-icon
      class="split-dialog__warning"
    />

    <!-- 展开/收起操作：分割线形式，整条可点击（仅重新拆分模式需要，确认模式预览常显） -->
    <div
      v-if="phase === 'preview' && inputExpanded"
      class="split-dialog__divider"
      @click="previewExpanded = !previewExpanded"
    >
      <span class="split-dialog__divider-line" />
      <span class="split-dialog__divider-label">
        <el-icon><ArrowUp v-if="previewExpanded" /><ArrowDown v-else /></el-icon>
        {{ previewExpanded ? '收起拆分结果预览' : '展开拆分结果预览' }}
      </span>
      <span class="split-dialog__divider-line" />
    </div>

    <!-- ② 预览：确认模式常显；重新拆分模式（输入区展开）默认隐藏，展开后显示 -->
    <template v-if="phase === 'preview' && (previewExpanded || !inputExpanded)">
      <div v-if="entries.length" class="split-dialog__preview-header">
        <span class="split-dialog__preview-title">拆分预览（共 {{ totalCount }} 条）</span>
        <el-checkbox :model-value="allChecked" :indeterminate="partialChecked" @change="toggleAll">
          全选
        </el-checkbox>
      </div>
      <div class="split-dialog__preview">
        <div v-for="g in groups" :key="g.module" class="split-dialog__group">
          <div class="split-dialog__group-header">
            <span class="split-dialog__group-name">{{ g.module }}</span>
            <span class="split-dialog__group-count">已选 {{ g.checked }}/{{ g.items.length }}</span>
          </div>
          <div v-for="entry in g.items" :key="entry.key" class="split-dialog__entry">
            <el-checkbox v-model="entry.checked" class="split-dialog__entry-check" />
            <template v-if="entry.editing">
              <div class="split-dialog__entry-edit">
                <el-input v-model="entry.title" maxlength="200" size="small" placeholder="需求点标题" />
                <MarkdownEditor v-model="entry.content" height="160px" />
                <div class="split-dialog__entry-edit-actions">
                  <el-button link size="small" type="primary" @click="saveEdit(entry)">保存</el-button>
                  <el-button link size="small" @click="entry.editing = false">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else>
              <span class="split-dialog__entry-title">{{ entry.title }}</span>
              <!-- 编辑/删除收敛为图标，hover 条目时浮现，避免确认模式列表满屏按钮 -->
              <span class="split-dialog__entry-actions">
                <el-button
                  link
                  type="primary"
                  size="small"
                  :aria-label="`编辑 ${entry.title}`"
                  @click="entry.editing = true"
                >
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :aria-label="`删除 ${entry.title}`"
                  @click="removeEntry(entry)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </span>
            </template>
          </div>
        </div>
        <el-empty
          v-if="phase === 'preview' && !entries.length"
          description="预览为空，可关闭后重新拆分"
          :image-size="64"
        />
      </div>
    </template>

    <!-- 底部操作区：与预览同显同隐（收起预览时连计数提示与入库按钮一并隐藏）；
         input/streaming 阶段不渲染插槽，避免空 footer 容器占用对话框底部高度 -->
    <template v-if="phase === 'preview' && (previewExpanded || !inputExpanded)" #footer>
      <span class="split-dialog__selected">已选 {{ checkedCount }} / {{ totalCount }} 条</span>
      <el-button :loading="importing" :disabled="checkedCount === 0" type="primary" @click="importChecked">
        批量入库
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.split-dialog__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-top: var(--space-md);
}

/* 输入上限提示：占据剩余空间，操作按钮靠右，与 AI 拆分按钮同行 */
.split-dialog__hint {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 确认模式收起态：提示与「重新拆分」按钮同行，避免空容器占高 */
.split-dialog__collapsed {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

/* 重新拆分模式输入区展开时（编辑器 280px + 展开后预览）内容可能超出视口，body 滚动 */
.split-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}

.split-dialog__progress {
  flex: 1;
}

.split-dialog__selected {
  float: left;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 32px;
}

.split-dialog__warning {
  margin-top: var(--space-md);
}

/* 分割线式展开/收起：整条可点击；收起态箭头朝下提示下方有可展开内容 */
.split-dialog__divider {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin: var(--space-md) 0;
  cursor: pointer;
  user-select: none;

  &:hover .split-dialog__divider-label {
    color: var(--el-color-primary);
  }
}

.split-dialog__divider-line {
  flex: 1;
  height: 1px;
  background: var(--el-border-color-lighter);
}

.split-dialog__divider-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

.split-dialog__preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--space-lg) 0 var(--space-sm);
}

.split-dialog__preview-title {
  font-weight: 600;
}

.split-dialog__preview {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.split-dialog__group {
  padding: var(--space-md);

  & + & {
    border-top: 1px dashed var(--el-border-color-lighter);
  }
}

.split-dialog__group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}

.split-dialog__group-name {
  font-weight: 600;
}

.split-dialog__group-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.split-dialog__entry {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;

  & + & {
    border-top: 1px solid var(--el-border-color-extra-light);
  }
}

/* 条目操作默认隐藏、hover/focus 条目时浮现：确认模式 8 条 × 2 按钮全收，避免满屏按钮 */
.split-dialog__entry-actions {
  visibility: hidden;
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.split-dialog__entry:hover .split-dialog__entry-actions,
.split-dialog__entry:focus-within .split-dialog__entry-actions {
  visibility: visible;
}

.split-dialog__entry-check {
  margin-right: var(--space-xs);
}

.split-dialog__entry-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.split-dialog__entry-edit {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.split-dialog__entry-edit-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
