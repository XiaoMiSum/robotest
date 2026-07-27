<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchWorkspaceContext, updateWorkspaceInfo } from '@/services/workspace'
import type { WorkspaceContext } from '@/types'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const detail = ref<WorkspaceContext | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '' })

const canEdit = computed(() => authStore.hasPermission('ws-info:edit'))

const rules: FormRules = {
  name: [
    { required: true, message: '请输入空间名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
  ],
}

async function load() {
  const routeWsId = route.params.workspaceId as string | undefined
  if (routeWsId && authStore.activeWorkspace?.id !== routeWsId) {
    authStore.setActiveWorkspace({ id: routeWsId, name: '', workspaceRole: '' })
  }
  loading.value = true
  try {
    const data = await fetchWorkspaceContext()
    detail.value = data
    form.name = data.name
    form.description = data.description ?? ''
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载空间信息失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const updated = await updateWorkspaceInfo({
      name: form.name.trim(),
      description: form.description.trim(),
    })
    detail.value = updated
    if (authStore.activeWorkspace) {
      authStore.setActiveWorkspace({ ...authStore.activeWorkspace, name: updated.name })
    }
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ws-info">

    <el-card shadow="never" class="ws-info__card">
      <template #header><span class="ws-info__section">基本信息</span></template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        class="ws-info__form"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" :disabled="!canEdit" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :disabled="!canEdit"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="统计">
          <div class="ws-info__stats">
            <div class="ws-info__stat-badge ws-info__stat-badge--primary">
              <el-icon><User /></el-icon>
              成员 {{ detail?.memberCount ?? 0 }}
            </div>
            <div class="ws-info__stat-badge ws-info__stat-badge--blue">
              <el-icon><Folder /></el-icon>
              项目 {{ detail?.projectCount ?? 0 }}
            </div>
          </div>
        </el-form-item>
        <el-form-item label="创建时间">
          <span class="ws-info__meta">{{ formatDateTime(detail?.createdAt) }}</span>
        </el-form-item>
        <el-form-item v-if="canEdit">
          <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.ws-info__card {
  max-width: 720px;
}

.ws-info__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.ws-info__form {
  max-width: 560px;
}

.ws-info__stats {
  display: flex;
  gap: var(--space-md);
}

.ws-info__stat-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-xs) var(--space-md);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.ws-info__stat-badge--primary {
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.ws-info__stat-badge--blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.ws-info__meta {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}
</style>
