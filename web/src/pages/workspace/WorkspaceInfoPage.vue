<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchWorkspaceContext, updateWorkspaceInfo } from '@/services/workspace'
import { WORKSPACE_ROLE } from '@/services/admin'
import type { WorkspaceContext } from '@/types'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const detail = ref<WorkspaceContext | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', description: '' })

const isAdmin = computed(
  () => authStore.activeWorkspace?.workspaceRole === WORKSPACE_ROLE.ADMIN,
)

const rules: FormRules = {
  name: [
    { required: true, message: '请输入空间名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
  ],
}

async function load() {
  // 若路由参数的 workspaceId 与 store 不一致，以路由为准同步 store
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
    // 同步更新 store 中的空间名称
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
  <div class="ws-info" v-loading="loading">
    <h2 class="ws-info__title">空间信息</h2>

    <el-card shadow="never" class="ws-info__card">
      <template #header><span class="ws-info__section">基本信息</span></template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        style="max-width: 560px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" :disabled="!isAdmin" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :disabled="!isAdmin"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="统计">
          <span class="ws-info__stat">成员 {{ detail?.memberCount ?? 0 }}</span>
          <el-divider direction="vertical" />
          <span class="ws-info__stat">项目 {{ detail?.projectCount ?? 0 }}</span>
        </el-form-item>
        <el-form-item label="创建时间">
          <span class="ws-info__stat">{{ formatDateTime(detail?.createdAt) }}</span>
        </el-form-item>
        <el-form-item v-if="isAdmin">
          <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.ws-info__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
}

.ws-info__card {
  max-width: 720px;
}

.ws-info__section {
  font-weight: 600;
}

.ws-info__stat {
  color: var(--el-text-color-regular);
}
</style>
