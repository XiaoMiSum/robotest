<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchRequirements } from '@/services/project'
import type { RequirementPoolItem, RequirementSummary } from '@/types'

/**
 * 需求选取器（US-AI-004 交互设计 6.2，可复用）：
 * 供「文档关联」「AI 生成/补全」「遗漏测试点分析」「回归子集推荐」入口调用。跨页多选以 selected Map 保序保留。
 */
const props = defineProps<{
  selectedIds?: string[]
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

async function load() {
  loading.value = true
  try {
    const page = await fetchRequirements({
      keyword: keyword.value || undefined,
      // 选取器仅展示 active 条目：已归档不参与 AI 消费与文档关联（需求规格 3.2.4）
      status: 'active',
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    items.value = page.list
    total.value = page.total
    // 回填已选项标题：打开时 selected 仅占位空串，列表就绪后补全，避免确认后标签只显示关闭按钮
    const next = new Map(selected.value)
    for (const item of page.list) {
      if (next.has(item.id)) next.set(item.id, item.title)
    }
    selected.value = next
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载需求失败')
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
  <el-dialog v-model="visible" title="选择需求" width="520px" append-to-body>
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
          <el-empty description="暂无需求" :image-size="60" />
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
