<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { WORKSPACE_ROLE } from '@/services/admin'
import { fetchProjectSettings, updateProjectSettings } from '@/services/projectSetting'
import {
  buildUpdatePayload,
  EXPIRE_DAY_OPTIONS,
  mapItemsToForm,
  resolveSaveError,
} from './projectSettingsModel'
import type { ProjectSettingsForm } from './projectSettingsModel'

const authStore = useAuthStore()

// 空间管理员≙项目维护者（详细设计 4.3），前端仅作交互提示，后端仍兜底校验
const canEdit = computed(() => authStore.activeWorkspace?.workspaceRole === WORKSPACE_ROLE.ADMIN)

const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)
const form = reactive<ProjectSettingsForm>({ shareEnabled: false, expireDays: 7 })
// 最近一次成功保存的快照，保存失败时控件回滚至该值（交互设计 3.2）
let lastSaved: ProjectSettingsForm | null = null

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const resp = await fetchProjectSettings('api_test')
    Object.assign(form, mapItemsToForm(resp.items))
    lastSaved = { ...form }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 成功前不落表单状态，失败即天然回滚，避免中途态闪烁
async function persist() {
  if (!canEdit.value || saving.value) return
  saving.value = true
  try {
    await updateProjectSettings(buildUpdatePayload(form))
    lastSaved = { ...form }
    ElMessage.success('已保存')
  } catch (err) {
    if (lastSaved) Object.assign(form, lastSaved)
    ElMessage.error(resolveSaveError(err))
  } finally {
    saving.value = false
  }
}

async function handleShareChange(value: boolean | string | number) {
  const next = value === true
  try {
    await ElMessageBox.confirm(
      next ? '开启后项目成员可生成分享链接' : '关闭后不可再生成新的分享链接，已生成链接在有效期内仍可访问',
      next ? '开启分享' : '关闭分享',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  form.shareEnabled = next
  await persist()
}

async function handleExpireChange(value: unknown) {
  const days = typeof value === 'number' ? value : Number.parseInt(String(value), 10)
  if (!EXPIRE_DAY_OPTIONS.includes(days)) return
  form.expireDays = days
  await persist()
}
</script>

<template>
  <div class="project-settings">
    <el-alert
      v-if="!canEdit && !loading"
      class="project-settings__readonly-tip"
      title="当前为只读模式，修改需项目维护者权限"
      type="info"
      show-icon
      :closable="false"
    />

    <el-card v-if="loadError" shadow="never" class="project-settings__card">
      <div class="project-settings__error">
        <p>设置加载失败，请稍后重试</p>
        <el-button @click="load">重试</el-button>
      </div>
    </el-card>

    <el-card v-else-if="loading" shadow="never" class="project-settings__card">
      <el-skeleton :rows="4" animated />
    </el-card>

    <el-card v-else shadow="never" class="project-settings__card">
      <template #header><span class="project-settings__title">安全策略与应用设置</span></template>

      <div class="project-settings__section-title">安全策略</div>
      <div class="project-settings__row">
        <div class="project-settings__row-main">
          <div class="project-settings__row-label">
            允许分享接口测试报告
            <span class="project-settings__row-default">默认关闭</span>
          </div>
          <div class="project-settings__row-desc">开启后，项目成员可在测试报告页生成分享链接</div>
        </div>
        <el-tooltip content="需项目维护者" placement="top" :disabled="canEdit || saving">
          <el-switch
            :model-value="form.shareEnabled"
            :disabled="!canEdit || saving"
            @change="handleShareChange"
          />
        </el-tooltip>
      </div>

      <div class="project-settings__section-title">应用设置</div>
      <div class="project-settings__row">
        <div class="project-settings__row-main">
          <div class="project-settings__row-label">分享链接有效期</div>
          <div class="project-settings__row-desc">
            对新生成的接口测试报告分享链接生效；已生成链接按其生成时的有效期判定，不受后续修改影响
          </div>
        </div>
        <el-tooltip content="需项目维护者" placement="top" :disabled="canEdit || saving">
          <el-select
            :model-value="form.expireDays"
            :disabled="!canEdit || saving"
            class="project-settings__select"
            @change="handleExpireChange"
          >
            <el-option v-for="days in EXPIRE_DAY_OPTIONS" :key="days" :label="`${days} 天`" :value="days" />
          </el-select>
        </el-tooltip>
      </div>

      <div class="project-settings__footer-note">各业务域配置相互独立，此处仅展示通用与接口测试域配置项</div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.project-settings__readonly-tip {
  max-width: 720px;
  margin-bottom: var(--space-md);
}

.project-settings__card {
  max-width: 720px;
}

.project-settings__title {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.project-settings__error {
  text-align: center;
  padding: var(--space-xl) 0;

  p {
    color: var(--color-neutral-500);
    margin-bottom: var(--space-md);
  }
}

.project-settings__section-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  letter-spacing: 0.05em;
  padding-bottom: var(--space-sm);
  border-bottom: 1px solid var(--color-neutral-100);
  margin-bottom: var(--space-md);

  & + .project-settings__row,
  & + .project-settings__row + .project-settings__row {
    border-top: none;
  }

  &:not(:first-child) {
    margin-top: var(--space-lg);
  }
}

.project-settings__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-sm) 0;

  & + & {
    border-top: 1px solid var(--color-neutral-50);
  }
}

.project-settings__row-label {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

.project-settings__row-default {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.project-settings__row-desc {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  margin-top: 2px;
  max-width: 480px;
}

.project-settings__select {
  width: 110px;
  flex-shrink: 0;
}

.project-settings__footer-note {
  margin-top: var(--space-lg);
  padding-top: var(--space-md);
  border-top: 1px dashed var(--color-neutral-100);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-300);
}
</style>
