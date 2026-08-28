<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiDebugKeyValue, ApiInterfaceDetail } from '@/types'
import {
  createInterface,
  deleteInterface,
  fetchInterfaceDetail,
  followInterface,
  unfollowInterface,
  updateInterface,
} from '@/services/apiInterface'
import KeyValueTable from './debug/KeyValueTable.vue'
import StepsPanel from './interfaces/StepsPanel.vue'
import VariablesPanel from './interfaces/VariablesPanel.vue'
import ChangeLogsPanel from './interfaces/ChangeLogsPanel.vue'
import { createEditorForm, methodTagType, toCreatePayload, type InterfaceEditorForm } from './interfacesModel'

const route = useRoute()
const router = useRouter()

const isNew = computed(() => route.params.interfaceId === 'new')
const interfaceId = computed(() => (isNew.value ? '' : String(route.params.interfaceId)))

const detail = ref<ApiInterfaceDetail | null>(null)
const form = ref<InterfaceEditorForm>(createEditorForm())
/** REST 路径参数与 Query 独立编辑（3.1.2 restParams） */
const restRows = ref<ApiDebugKeyValue[]>([{ key: '', value: '', enabled: true }])
const loading = ref(false)
const saving = ref(false)
const showChangeLogs = ref(false)
const activeTab = ref('basic')

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']
const BODY_TYPE_OPTIONS: { value: InterfaceEditorForm['bodyType']; label: string }[] = [
  { value: 'none', label: '无' },
  { value: 'json', label: 'JSON' },
  { value: 'form', label: '表单' },
  { value: 'raw', label: '原始文本' },
]

async function loadDetail() {
  if (isNew.value) {
    // 新建模式支持 ?moduleId= 预选模块（列表页树节点进入）
    const queryModuleId = typeof route.query.moduleId === 'string' ? route.query.moduleId : null
    form.value = createEditorForm()
    form.value.moduleId = queryModuleId
    return
  }
  loading.value = true
  try {
    detail.value = await fetchInterfaceDetail(interfaceId.value)
    form.value = createEditorForm(detail.value)
    restRows.value = (detail.value.restParams ?? []).filter((row) => row.key !== '').concat({ key: '', value: '', enabled: true })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接口详情加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 保存 ====================

function buildRequestBody(): { type?: string; content?: unknown } | undefined {
  switch (form.value.bodyType) {
    case 'none':
      return undefined
    case 'json':
      return { type: 'json', content: form.value.jsonText }
    case 'form':
      return { type: 'form', content: form.value.formRows }
    case 'raw':
      return { type: 'raw', content: form.value.rawText }
  }
}

async function save() {
  if (saving.value) return
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写接口名称')
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      const { req, error } = toCreatePayload(form.value)
      if (error) {
        ElMessage.warning(error)
        return
      }
      req.body = buildRequestBody()
      req.restParams = enabledRows(restRows.value)
      const id = await createInterface(req)
      ElMessage.success('接口已创建')
      // 替换路由避免刷新后重复创建
      await router.replace(`/workspace/projects/interfaces/${id}`)
      return
    }
    const payload = toCreatePayload(form.value)
    if (payload.error) {
      ElMessage.warning(payload.error)
      return
    }
    await updateInterface(interfaceId.value, {
      ...payload.req,
      body: buildRequestBody(),
      restParams: enabledRows(restRows.value),
      changeVersion: detail.value!.changeVersion,
    })
    ElMessage.success('已保存')
    await loadDetail()
  } catch (err) {
    await handleSaveConflict(err)
  } finally {
    saving.value = false
  }
}

function enabledRows(rows: ApiDebugKeyValue[]): ApiDebugKeyValue[] {
  return rows.filter((row) => row.key.trim() !== '').map((row) => ({ key: row.key.trim(), value: row.value, enabled: row.enabled }))
}

/** 乐观锁冲突（7105）：提示以服务端最新版本为准，确认后重载覆盖本地编辑 */
async function handleSaveConflict(err: unknown) {
  const message = err instanceof Error ? err.message : ''
  if (!message.includes('7105') && !message.includes('版本')) {
    ElMessage.error(message || '保存失败')
    return
  }
  await ElMessageBox.confirm('接口已被他人修改，是否加载最新版本（将丢弃当前未保存的编辑）？', '版本冲突', { type: 'warning' })
  await loadDetail()
}

function handleCtrlS(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void save()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleCtrlS)
  void loadDetail()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleCtrlS)
})

// ==================== 页头操作 ====================

function goBack() {
  void router.push('/workspace/projects/api-testing?tab=interfaces')
}

async function toggleFollow() {
  if (!detail.value) return
  try {
    if (detail.value.followed) {
      await unfollowInterface(interfaceId.value)
      detail.value.followed = false
    } else {
      await followInterface(interfaceId.value)
      detail.value.followed = true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function removeCurrent() {
  await ElMessageBox.confirm(`删除接口「${detail.value?.name}」？删除后不可恢复。`, '删除接口', { type: 'warning' })
  try {
    await deleteInterface(interfaceId.value)
    ElMessage.success('已删除')
    goBack()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}
</script>

<template>
  <div v-loading="loading" class="interface-editor">
    <el-card shadow="never">
      <template #header>
        <div class="interface-editor__header">
          <el-button link data-test="editor-back-btn" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回列表
          </el-button>

          <template v-if="!isNew">
            <el-icon class="interface-editor__star" :class="{ 'is-active': detail?.followed }" @click="toggleFollow">
              <StarFilled v-if="detail?.followed" /><Star v-else />
            </el-icon>
            <span v-if="detail" class="interface-editor__version">v{{ detail.changeVersion }}</span>
          </template>

          <div class="interface-editor__spacer" />

          <el-button v-if="!isNew" data-test="editor-changelogs-btn" @click="showChangeLogs = true">变更历史</el-button>
          <el-button v-if="!isNew" type="danger" plain data-test="editor-delete-btn" @click="removeCurrent">删除</el-button>
          <el-button type="primary" :loading="saving" data-test="editor-save-btn" @click="save">
            {{ isNew ? '创建' : '保存' }}
          </el-button>
        </div>
      </template>

      <div class="interface-editor__request-line">
        <el-select v-model="form.method" style="width: 130px" data-test="editor-method-select">
          <el-option v-for="method in METHOD_OPTIONS" :key="method" :value="method" :label="method" />
        </el-select>
        <el-tag size="small" :type="methodTagType(form.method)" class="interface-editor__protocol">http</el-tag>
        <el-input v-model="form.path" placeholder="/api/resource" style="flex: 1" data-test="editor-path-input" />
        <el-input v-model="form.name" placeholder="接口名称" style="width: 260px" data-test="editor-name-input" />
      </div>

      <el-tabs v-model="activeTab" class="interface-editor__tabs">
        <el-tab-pane name="basic" label="基本信息">
          <div class="interface-editor__field-row interface-editor__field-row--top">
            <span class="interface-editor__field-label">描述</span>
            <el-input v-model="form.description" type="textarea" :rows="3" data-test="editor-description-input" />
          </div>
          <div class="interface-editor__field-row interface-editor__field-row--top">
            <span class="interface-editor__field-label">响应示例</span>
            <el-input
              v-model="form.responseExampleText"
              type="textarea"
              :rows="8"
              placeholder='{"status": 200, "body": {...}}'
              data-test="editor-response-example-input"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="headers" label="请求头">
          <KeyValueTable v-model:entries="form.headers" placeholder-key="Header" />
        </el-tab-pane>

        <el-tab-pane name="query" label="Query 参数">
          <KeyValueTable v-model:entries="form.params" placeholder-key="参数名" />
        </el-tab-pane>

        <el-tab-pane name="rest" label="路径参数">
          <p class="interface-editor__hint">路径中以 {id} 形式声明的占位参数在此配置默认值。</p>
          <KeyValueTable v-model:entries="restRows" placeholder-key="参数名" />
        </el-tab-pane>

        <el-tab-pane name="body" label="请求体">
          <el-radio-group v-model="form.bodyType" class="interface-editor__body-types" data-test="editor-body-type-group">
            <el-radio-button v-for="option in BODY_TYPE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>

          <el-input
            v-if="form.bodyType === 'json'"
            v-model="form.jsonText"
            type="textarea"
            :rows="12"
            placeholder='{ "username": "admin" }'
            data-test="editor-json-body-input"
          />
          <template v-else-if="form.bodyType === 'form'">
            <p class="interface-editor__hint">文件上传字段随测试场景模块开放，当前支持文本表单。</p>
            <KeyValueTable v-model:entries="form.formRows" placeholder-key="字段名" />
          </template>
          <el-input
            v-else-if="form.bodyType === 'raw'"
            v-model="form.rawText"
            type="textarea"
            :rows="12"
            placeholder="原始请求体内容"
          />
          <p v-else class="interface-editor__hint">该请求不携带请求体。</p>
        </el-tab-pane>

        <el-tab-pane v-if="!isNew" name="steps" label="公共步骤">
          <StepsPanel
            v-if="detail"
            :interface-id="interfaceId"
            :steps="detail.steps"
            @change="(steps) => (detail!.steps = steps)"
          />
        </el-tab-pane>

        <el-tab-pane v-if="!isNew" name="variables" label="变量">
          <VariablesPanel :interface-id="interfaceId" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <ChangeLogsPanel v-if="!isNew" v-model="showChangeLogs" :interface-id="interfaceId" />
  </div>
</template>

<style scoped lang="scss">
.interface-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--space-lg);
}

.interface-editor__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interface-editor__star {
  cursor: pointer;
  color: var(--color-neutral-300);
  font-size: 18px;

  &.is-active {
    color: #f59e0b;
  }
}

.interface-editor__version {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.interface-editor__spacer {
  flex: 1;
}

.interface-editor__request-line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interface-editor__protocol {
  flex-shrink: 0;
}

.interface-editor__tabs {
  :deep(.el-tabs__content) {
    padding-top: var(--space-sm);
  }
}

.interface-editor__field-row {
  display: flex;
  gap: var(--space-md);
  align-items: center;

  &--top {
    align-items: flex-start;
  }

  & + & {
    margin-top: var(--space-md);
  }
}

.interface-editor__field-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  line-height: 32px;
}

.interface-editor__body-types {
  margin-bottom: var(--space-md);
}

.interface-editor__hint {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
