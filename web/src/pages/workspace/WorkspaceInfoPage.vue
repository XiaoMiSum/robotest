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
    <header class="ws-info__head">
      <h1 class="ws-info__title">空间信息</h1>
      <p class="ws-info__subtitle">空间基础资料与运行统计</p>
    </header>

    <section class="ws-info__kpi-grid" aria-label="工作空间统计">
      <article class="ws-info__kpi-card">
        <div class="ws-info__kpi-label">
          <el-icon><User /></el-icon>
          <span>成员</span>
        </div>
        <div class="ws-info__kpi-value">
          {{ detail?.memberCount ?? 0 }}<span class="ws-info__kpi-unit">人</span>
        </div>
      </article>
      <article class="ws-info__kpi-card">
        <div class="ws-info__kpi-label">
          <el-icon><Folder /></el-icon>
          <span>项目</span>
        </div>
        <div class="ws-info__kpi-value">
          {{ detail?.projectCount ?? 0 }}<span class="ws-info__kpi-unit">个</span>
        </div>
      </article>
    </section>

    <el-card shadow="never" class="ws-info__card">
      <template #header>
        <h2 class="ws-info__section">基础信息</h2>
      </template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="ws-info__form"
      >
        <div class="ws-info__form-grid">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" :disabled="!canEdit" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="空间 ID">
            <el-input :model-value="detail?.id ?? '—'" disabled class="ws-info__id" />
          </el-form-item>
        </div>
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
      </el-form>

      <div class="ws-info__meta">
        <span class="ws-info__meta-label">创建时间</span>
        <span class="ws-info__meta-value">{{ formatDateTime(detail?.createdAt) }}</span>
      </div>

      <div v-if="canEdit" class="ws-info__actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.ws-info {
  min-width: 0;
}

.ws-info__head {
  margin-bottom: var(--block-gap);
}

.ws-info__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.ws-info__subtitle {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.ws-info__kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.ws-info__kpi-card {
  min-height: 128px;
  padding: 20px var(--card-pad);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.ws-info__kpi-label {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;

  .el-icon {
    color: var(--color-neutral-400);
    font-size: 15px;
  }
}

.ws-info__kpi-value {
  margin-top: 10px;
  color: var(--color-neutral-900);
  font-size: 30px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.ws-info__kpi-unit {
  margin-left: 3px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-base);
  font-weight: 500;
}

.ws-info__card {
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.ws-info__card :deep(.el-card__header) {
  padding: var(--space-lg) var(--card-pad);
}

.ws-info__card :deep(.el-card__body) {
  padding: var(--card-pad);
}

.ws-info__section {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.ws-info__form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.4;
}

.ws-info__form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.ws-info__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-xl);
}

.ws-info__id :deep(.el-input__inner) {
  font-family: var(--font-mono);
  color: var(--color-neutral-500);
}

.ws-info__meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  padding-top: var(--space-xs);
}

.ws-info__meta-label {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.ws-info__meta-value {
  color: var(--color-neutral-900);
  font-size: var(--font-size-base);
  font-variant-numeric: tabular-nums;
}

.ws-info__actions {
  display: flex;
  justify-content: flex-end;
  margin: var(--card-pad) calc(-1 * var(--card-pad)) calc(-1 * var(--card-pad));
  padding: 14px var(--card-pad);
  border-top: 1px solid var(--color-neutral-100);
}

@media (max-width: 768px) {
  .ws-info__kpi-grid,
  .ws-info__form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
