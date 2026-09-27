<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  archiveRequirement,
  createRequirement,
  deleteRequirement,
  fetchRequirements,
  getRequirement,
  updateRequirement,
} from '@/services/project'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import type { RequirementPoolItem, RequirementStatus } from '@/types'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import RequirementSplitDialog from '@/components/project/functional-testing/requirement/RequirementSplitDialog.vue'
import {
  REQUIREMENT_SEGMENT_OPTIONS,
  canEditRequirement,
  requirementPagerTotal,
  requirementSourceSub,
  requirementStatusMeta,
} from '@/components/project/functional-testing/requirement/requirementPoolPresentation'

const authStore = useAuthStore()
// 编辑/删除/归档入口按权限点显隐；后端按"创建人或项目管理权限"强校验兜底
const canEdit = computed(() => authStore.hasPermission('requirement:edit'))

const loading = ref(false)
const items = ref<RequirementPoolItem[]>([])
const total = ref(0)
const keyword = ref('')
// 归档维筛选：两态互斥即全集，默认启用（交互设计 1.1）
const segment = ref<RequirementStatus>('active')
const pageNo = ref(1)
const pageSize = ref(20)

// 展示口径（状态/来源副行/编辑门槛）下沉为视图模型，与详情、AI 入口共用同一份纯函数
const rows = computed(() =>
  items.value.map((item) => ({
    item,
    status: requirementStatusMeta(item.status),
    source: requirementSourceSub(item.sourceUrl),
    editable: canEditRequirement(item.status),
  })),
)

// AI 拆分对话框（US-AI-019，入口与编辑权限一致，交互设计 6.1.2）
const splitDialogVisible = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await fetchRequirements({
      keyword: keyword.value || undefined,
      status: segment.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    items.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载需求池失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pageNo.value = 1
  load()
}

function handleReset() {
  keyword.value = ''
  segment.value = 'active'
  pageNo.value = 1
  load()
}

function handlePageChange(page: number) {
  pageNo.value = page
  load()
}

// ==================== 新建/编辑抽屉 ====================
const drawerVisible = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', content: '', sourceUrl: '' })

const rules: FormRules = {
  title: [
    { required: true, message: '请输入条目标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 字符', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入需求内容', trigger: 'blur' }],
}

const drawerTitle = computed(() => (editingId.value ? '编辑需求条目' : '新建需求条目'))

function openCreate() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.sourceUrl = ''
  drawerVisible.value = true
}

async function openEdit(id: string) {
  try {
    const detail = await getRequirement(id)
    editingId.value = detail.id
    form.title = detail.title
    form.content = detail.content
    form.sourceUrl = detail.sourceUrl ?? ''
    drawerVisible.value = true
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载条目详情失败')
  }
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await updateRequirement(editingId.value, {
        title: form.title,
        content: form.content,
        // 空串清空来源 URL（三态语义）
        sourceUrl: form.sourceUrl,
      })
      ElMessage.success('已保存')
    } else {
      await createRequirement({
        title: form.title,
        content: form.content,
        sourceUrl: form.sourceUrl || undefined,
      })
      ElMessage.success('已创建')
    }
    drawerVisible.value = false
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm(
      '删除不影响已生成的用例，文档关联将解除。确定删除该条目？',
      '删除需求条目',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteRequirement(id)
    ElMessage.success('已删除')
    // 删除末页最后一条时回退一页
    if (items.value.length === 1 && pageNo.value > 1) pageNo.value -= 1
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

async function handleArchive(row: RequirementPoolItem) {
  const archived = row.status !== 'archived'
  try {
    await ElMessageBox.confirm(
      archived
        ? '归档后条目不可编辑，且不再被 AI 功能选用。确定归档？'
        : '恢复为启用状态，重新参与 AI 选用。确定取消归档？',
      archived ? '归档需求条目' : '取消归档',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await archiveRequirement(row.id, archived)
    ElMessage.success(archived ? '已归档' : '已取消归档')
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <main class="requirement-pool">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">需求池</h1>
        <p class="page-head__desc">沉淀需求条目，作为用例设计与评审的输入</p>
      </div>
      <div class="page-head__actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建需求
        </el-button>
      </div>
    </header>

    <el-card v-loading="loading" shadow="never">
      <div class="requirement-pool__toolbar">
        <el-radio-group v-model="segment" @change="search">
          <el-radio-button
            v-for="option in REQUIREMENT_SEGMENT_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          placeholder="需求标题"
          clearable
          style="width: 200px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="requirement-pool__toolbar-end">
          <!-- AI 拆分入口：与编辑权限一致（交互设计 6.1.2） -->
          <el-button v-if="canEdit" type="primary" plain @click="splitDialogVisible = true">
            <el-icon><MagicStick /></el-icon>AI 拆分
          </el-button>
          <span class="requirement-pool__sort">按更新时间排序</span>
        </div>
      </div>

      <el-table :data="rows">
        <!-- AI 拆分入库条目标题前展示青色 AI 徽标（复用 mindmap AI_BADGE 色值，交互设计 6.1.2）；副行承载来源 -->
        <el-table-column label="标题" min-width="300">
          <template #default="{ row }">
            <div class="requirement-pool__cell">
              <span class="requirement-pool__cell-main">
                <el-tag
                  v-if="row.item.aiGenerated"
                  size="small"
                  class="requirement-pool__ai-badge"
                  disable-transitions
                >AI</el-tag>
                <span>{{ row.item.title }}</span>
              </span>
              <el-link
                v-if="row.source.href"
                class="requirement-pool__cell-sub"
                :href="row.source.href ?? undefined"
                target="_blank"
                underline="never"
              >{{ row.source.text }}</el-link>
              <span v-else class="requirement-pool__cell-sub requirement-pool__muted">{{ row.source.text }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="requirement-pool__status" :class="`requirement-pool__status--${row.status.modifier}`">
              <span class="requirement-pool__status-dot" />
              {{ row.status.label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="更新人" width="110">
          <template #default="{ row }">{{ row.item.creatorName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.item.updatedAt) }}</template>
        </el-table-column>
        <!-- 操作列含 编辑/删除/归档 三个 link 按钮（归档态为「取消归档」四字），需加宽避免挤压 -->
        <el-table-column v-if="canEdit" label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <!-- 已归档只读：隐藏编辑入口（后端同时强校验拒绝） -->
            <el-button v-if="row.editable" link type="primary" @click="openEdit(row.item.id)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.item.id)">删除</el-button>
            <el-button link type="warning" @click="handleArchive(row.item)">
              {{ row.editable ? '归档' : '取消归档' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无需求条目" :image-size="80" />
        </template>
      </el-table>

      <div class="requirement-pool__pager">
        <span class="requirement-pool__pager-total">{{ requirementPagerTotal(total, pageSize) }}</span>
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :current-page="pageNo"
          :page-size="pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入条目标题" />
        </el-form-item>
        <el-form-item label="需求内容" prop="content">
          <MarkdownEditor v-model="form.content" height="280px" placeholder="粘贴或输入需求文本（支持 Markdown）" />
        </el-form-item>
        <el-form-item label="来源 URL">
          <el-input v-model="form.sourceUrl" placeholder="可选，仅记录出处，平台不抓取" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <!-- AI 拆分入库成功后刷新列表（对话框内部负责入库与提示） -->
    <RequirementSplitDialog v-model="splitDialogVisible" @imported="load" />
  </main>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.requirement-pool__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding-bottom: var(--space-md);
}

.requirement-pool__toolbar-end {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-left: auto;
}

.requirement-pool__sort {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.requirement-pool__cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.requirement-pool__cell-main {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
}

.requirement-pool__cell-sub {
  font-family: var(--font-mono);
  font-size: var(--font-size-2xs);
}

.requirement-pool__status {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.requirement-pool__status-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: var(--radius-full);
  background: var(--color-neutral-400);
}

.requirement-pool__status--active {
  color: var(--color-success);

  .requirement-pool__status-dot {
    background: var(--color-success);
  }
}

.requirement-pool__status--archived {
  color: var(--color-neutral-500);
}

.requirement-pool__muted {
  color: var(--color-neutral-400);
}

.requirement-pool__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.requirement-pool__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

/* AI 拆分入库条目标识：青色 AI 徽标，与 mindmap badges 的 AI_BADGE 同源（视觉设计 --color-ai-badge） */
.requirement-pool__ai-badge {
  color: var(--color-ai-badge);
  border-color: var(--color-ai-badge);
  background-color: color-mix(in srgb, var(--color-ai-badge) 10%, transparent);
}
</style>
