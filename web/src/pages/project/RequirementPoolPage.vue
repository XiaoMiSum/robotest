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
import type { RequirementPoolItem } from '@/types'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'

const authStore = useAuthStore()
// 编辑/删除/归档入口按权限点显隐；后端按"创建人或项目管理权限"强校验兜底
const canEdit = computed(() => authStore.hasPermission('requirement:edit'))

const loading = ref(false)
const items = ref<RequirementPoolItem[]>([])
const total = ref(0)
const keyword = ref('')
// 状态筛选：空串表示全部（后端 status 缺省返回全部）
const statusFilter = ref<string>('')
const pageNo = ref(1)
const pageSize = ref(20)

async function load() {
  loading.value = true
  try {
    const page = await fetchRequirements({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
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
  statusFilter.value = ''
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
  <div class="requirement-pool">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="requirement-pool__toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索条目标题"
            clearable
            class="requirement-pool__search"
            @keyup.enter="search"
            @clear="search"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
            v-model="statusFilter"
            placeholder="状态"
            clearable
            class="requirement-pool__status"
            @change="search"
          >
            <el-option label="启用" value="active" />
            <el-option label="已归档" value="archived" />
          </el-select>
          <el-button type="primary" @click="search">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <div class="requirement-pool__spacer" />
          <el-button type="primary" @click="openCreate">
            <el-icon><Plus /></el-icon>新建条目
          </el-button>
        </div>
      </template>

      <el-table :data="items" class="requirement-pool__table">
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="来源 URL" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.sourceUrl" :href="row.sourceUrl" target="_blank" type="primary">{{ row.sourceUrl }}</el-link>
            <span v-else class="requirement-pool__muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'archived' ? 'info' : 'success'" disable-transitions>
              {{ row.status === 'archived' ? '已归档' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="更新人" width="140" />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <!-- 操作列含 编辑/删除/归档 三个 link 按钮（归档态为「取消归档」四字），需加宽避免挤压 -->
        <el-table-column v-if="canEdit" label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <!-- 已归档只读：隐藏编辑入口（后端同时强校验拒绝） -->
            <el-button v-if="row.status !== 'archived'" link type="primary" @click="openEdit(row.id)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
            <el-button link type="warning" @click="handleArchive(row as RequirementPoolItem)">
              {{ row.status === 'archived' ? '取消归档' : '归档' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无需求条目" :image-size="80" />
        </template>
      </el-table>

      <div class="requirement-pool__pager">
        <el-pagination
          layout="total, prev, pager, next"
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
  </div>
</template>

<style scoped lang="scss">
.requirement-pool__toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.requirement-pool__search {
  width: 260px;
}

.requirement-pool__status {
  width: 130px;
}

.requirement-pool__spacer {
  flex: 1;
}

.requirement-pool__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.requirement-pool__muted {
  color: var(--el-text-color-placeholder);
}
</style>
