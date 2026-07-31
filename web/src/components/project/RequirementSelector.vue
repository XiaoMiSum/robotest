<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRequirement, fetchRequirements } from '@/services/project'
import type { RequirementPoolItem, RequirementSummary } from '@/types'

/**
 * 需求条目选取器（US-AI-004 交互设计 6.2，可复用）：
 * 供「文档关联」「AI 生成/补全」入口调用。跨页多选以 selected Map 保序保留。
 * draftText 非空时（AI 入口临时文本）展示「保存为需求池条目」入口。
 */
const props = defineProps<{
  selectedIds?: string[]
  /** AI 入口的临时需求文本，非空时可一键另存为条目并自动勾选 */
  draftText?: string
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  confirm: [selected: RequirementSummary[]]
}>()

const loading = ref(false)
const items = ref<RequirementPoolItem[]>([])
const total = ref(0)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(10)
// 跨页保留选择：id → title，保序
const selected = ref<Map<string, string>>(new Map())
const savingDraft = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await fetchRequirements({
      keyword: keyword.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    items.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载需求条目失败')
  } finally {
    loading.value = false
  }
}

function toggle(id: string, title: string, checked: boolean): void {
  const next = new Map(selected.value)
  if (checked) next.set(id, title)
  else next.delete(id)
  selected.value = next
}

function search(): void {
  pageNo.value = 1
  load()
}

function handlePageChange(page: number): void {
  pageNo.value = page
  load()
}

async function saveDraft(): Promise<void> {
  const text = (props.draftText ?? '').trim()
  if (!text) return
  savingDraft.value = true
  try {
    // 标题预填文本首行（截断 200），内容为完整临时文本
    const title = text.split('\n')[0].slice(0, 200)
    const id = await createRequirement({ title, content: text })
    toggle(id, title, true)
    ElMessage.success('已保存为需求池条目并勾选')
    search()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    savingDraft.value = false
  }
}

function confirm(): void {
  emit(
    'confirm',
    Array.from(selected.value, ([id, title]) => ({ id, title })),
  )
  visible.value = false
}

// 每次打开同步外部已选并加载首页
watch(
  visible,
  (open) => {
    if (!open) return
    const map = new Map<string, string>()
    // 外部仅传 id，标题在列表加载后补全展示；此处先占位空串
    for (const id of props.selectedIds ?? []) map.set(id, '')
    selected.value = map
    keyword.value = ''
    pageNo.value = 1
    load()
  },
  { immediate: true },
)
</script>

<template>
  <el-dialog v-model="visible" title="选择需求条目" width="520px" append-to-body>
    <div class="req-selector">
      <div class="req-selector__search">
        <el-input
          v-model="keyword"
          placeholder="按标题过滤"
          clearable
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">过滤</el-button>
      </div>

      <div v-loading="loading" class="req-selector__list">
        <div v-if="!items.length" class="req-selector__empty">
          <el-empty description="暂无需求条目" :image-size="60" />
        </div>
        <label v-for="item in items" :key="item.id" class="req-selector__item">
          <el-checkbox
            :model-value="selected.has(item.id)"
            @update:model-value="(v) => toggle(item.id, item.title, v === true)"
          />
          <span class="req-selector__title">{{ item.title }}</span>
        </label>
      </div>

      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="pageNo"
        :page-size="pageSize"
        @current-change="handlePageChange"
      />
    </div>

    <template #footer>
      <div class="req-selector__footer">
        <el-button v-if="draftText" :loading="savingDraft" @click="saveDraft">保存为需求池条目</el-button>
        <span class="req-selector__count">已选 {{ selected.size }} 条</span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="confirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.req-selector__search {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.req-selector__list {
  min-height: 200px;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 4px 0;
}

.req-selector__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.req-selector__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.req-selector__footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.req-selector__count {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
