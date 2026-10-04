<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import {
  useRequirementPicker,
  type RequirementPick,
} from '@/composables/project/requirement/useRequirementPicker'
import { requirementStatusMeta } from '@/composables/project/requirement/requirementPresentation'
import type { RequirementListItem } from '@/types'

/**
 * 「选择需求」选取器（交互 06 关联需求）：关键字过滤 + 分页多选 + 已选计数，
 * 行内 [明细] 就近冒泡 Markdown 正文（仅正文展示，不联动勾选）。
 */
const props = defineProps<{
  /** 打开时回填的既有条目（跨页已选保留） */
  initial: RequirementPick[]
}>()

const emit = defineEmits<{ confirm: [ids: string[]] }>()

const visible = defineModel<boolean>({ required: true })

const picker = useRequirementPicker()
const detailVisible = ref(false)
const detailTitle = ref('')

const canConfirm = computed(() => picker.selectedCount.value > 0)

watch(visible, (open) => {
  if (!open) {
    closeDetailPopover()
    return
  }
  picker.seed(props.initial)
  picker.reset()
})

function closeDetailPopover(): void {
  detailVisible.value = false
  picker.closeDetail()
}

async function toggleDetail(row: { id: string; title: string }): Promise<void> {
  if (detailVisible.value && detailTitle.value === row.title) {
    closeDetailPopover()
    return
  }
  detailTitle.value = row.title
  detailVisible.value = true
  await picker.openDetail(row)
}

function confirm(): void {
  emit('confirm', picker.selectedIds.value)
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="选择需求" width="760px" append-to-body>
    <div class="picker__toolbar">
      <el-input
        v-model="picker.keyword.value"
        clearable
        placeholder="编号或标题"
        style="width: 240px"
        @keyup.enter="picker.search"
        @clear="picker.search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <span class="picker__count">已选 {{ picker.selectedCount.value }} 项</span>
      <el-button v-if="picker.selectedCount.value > 0" text type="primary" @click="picker.clearSelected">
        清空已选
      </el-button>
    </div>

    <el-alert
      v-if="picker.loadError.value"
      type="error"
      :title="picker.loadError.value"
      show-icon
      :closable="false"
      class="picker__error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="picker.retry">重试</el-button>
      </template>
    </el-alert>

    <el-table
      v-loading="picker.loading.value"
      :data="picker.rows.value"
      row-key="id"
      max-height="380"
      @row-click="picker.toggle($event as RequirementListItem)"
    >
      <el-table-column width="44" align="center">
        <template #default="{ row }">
          <el-checkbox
            :model-value="picker.selected.value.has(row.id)"
            @click.stop
            @change="picker.toggle(row as RequirementListItem)"
          />
        </template>
      </el-table-column>
      <el-table-column label="编号" width="110">
        <template #default="{ row }">
          <span class="picker__code">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="picker__title">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag
            :type="requirementStatusMeta(row.status).tagType"
            size="small"
            effect="light"
          >
            {{ requirementStatusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="right">
        <template #default="{ row }">
          <el-popover
            :visible="detailVisible && detailTitle === row.title"
            placement="left"
            :width="420"
          >
            <template #reference>
              <el-button text type="primary" size="small" @click.stop="toggleDetail(row as RequirementListItem)">
                明细
              </el-button>
            </template>
            <div v-loading="picker.detailLoading.value" class="picker__detail">
              <el-alert
                v-if="picker.detailError.value"
                type="error"
                :title="picker.detailError.value"
                show-icon
                :closable="false"
              />
              <MarkdownView
                v-else-if="picker.detail.value?.description"
                :content="picker.detail.value.description"
              />
              <p v-else class="picker__detail-empty">该需求无描述正文</p>
              <div class="picker__detail-foot">
                <el-button size="small" text @click="closeDetailPopover">关闭</el-button>
              </div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
      <template #empty>
        <span class="picker__empty">
          {{ picker.hasLoaded.value ? '无匹配需求' : '加载需求列表…' }}
        </span>
      </template>
    </el-table>

    <el-pagination
      v-if="picker.total.value > 0"
      :current-page="picker.pageNo.value"
      :page-size="10"
      :total="picker.total.value"
      layout="total, prev, pager, next"
      small
      class="picker__pager"
      @current-change="picker.changePage"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="confirm">
        确定{{ picker.selectedCount.value > 0 ? `（${picker.selectedCount.value}）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.picker__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.picker__count {
  color: var(--color-neutral-600);
  font-size: 13px;
}

.picker__code {
  font-weight: 600;
  color: var(--color-neutral-800);
}

.picker__title {
  color: var(--color-neutral-700);
}

.picker__error {
  margin-bottom: 8px;
}

.picker__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.picker__empty {
  color: var(--color-neutral-500);
}

.picker__detail-empty {
  margin: 0;
  color: var(--color-neutral-500);
  font-size: 13px;
}

.picker__detail-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
