<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { promptSourceLabel, useAiAdminStore } from '@/stores/aiAdmin'
import { formatDateTime } from '@/utils/format'
import type { AiPromptListItem } from '@/types'

const store = useAiAdminStore()
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await store.loadPrompts()
  } finally {
    loading.value = false
  }
}

// ---------- 编辑弹窗 ----------

const dialogVisible = ref(false)
const current = ref<AiPromptListItem | null>(null)
const form = reactive({ content: '' })
const detailLoading = ref(false)
const saveError = ref('')
const detailError = ref('')
const saving = ref(false)
const formRef = ref<FormInstance>()

/** el-input（textarea）暴露的原生 textarea，用于变量插入的光标定位 */
interface EditorExpose {
  textarea?: HTMLTextAreaElement
}
const editorRef = ref<EditorExpose | null>(null)

function validateContent(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value?.trim()) {
    callback()
    return
  }
  callback(new Error('提示词内容不能为空'))
}

const rules: FormRules = {
  content: [{ validator: validateContent, trigger: 'blur' }],
}

async function loadDetail(scene: string) {
  detailError.value = ''
  detailLoading.value = true
  try {
    const detail = await store.loadPromptDetail(scene)
    form.content = detail.content
  } catch (error) {
    detailError.value =
      error instanceof Error && error.message ? error.message : '加载提示词详情失败'
  } finally {
    detailLoading.value = false
  }
}

function openEdit(row: AiPromptListItem) {
  current.value = row
  form.content = ''
  saveError.value = ''
  dialogVisible.value = true
  void loadDetail(row.scene)
}

/** 悬浮变量点击插入占位；取不到光标位置时追加到末尾 */
function insertVariable(name: string) {
  const token = `{{${name}}}`
  const textarea = editorRef.value?.textarea
  if (!textarea) {
    form.content += token
    return
  }
  const start = textarea.selectionStart ?? form.content.length
  const end = textarea.selectionEnd ?? start
  form.content = form.content.slice(0, start) + token + form.content.slice(end)
  void nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + token.length, start + token.length)
  })
}

async function save() {
  if (!current.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  saveError.value = ''
  try {
    await store.savePrompt(current.value.scene, form.content)
    ElMessage.success('提示词已保存')
    dialogVisible.value = false
  } catch (error) {
    // 后端变量校验失败内联展示，保留输入值（交互 2.4）
    saveError.value =
      error instanceof Error && error.message ? error.message : '保存提示词失败'
  } finally {
    saving.value = false
  }
}

// ---------- 恢复默认 ----------

async function reset(row: AiPromptListItem) {
  try {
    await ElMessageBox.confirm('恢复后自定义内容将丢失', '恢复默认提示词', {
      type: 'warning',
      confirmButtonText: '恢复默认',
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }
  try {
    await store.resetPrompt(row.scene)
    ElMessage.success('已恢复内置默认内容')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '恢复失败')
  }
}

onMounted(async () => {
  // 切换分组复用缓存（交互 5）：已有数据不重复请求
  if (store.prompts.length === 0) await load()
})
</script>

<template>
  <div class="prompt-group">
    <!-- 列表错误（UI-PAGE-11：页面捕获 + 重试） -->
    <div v-if="store.promptsError" class="prompt-group__error">
      <span>{{ store.promptsError }}</span>
      <el-button size="small" @click="load">重试</el-button>
    </div>

    <el-empty
      v-else-if="!loading && store.prompts.length === 0"
      description="暂无提示词场景"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="store.prompts"
      class="prompt-group__table"
    >
      <el-table-column label="场景" min-width="180">
        <template #default="{ row }">
          <div class="prompt-group__scene">
            <span class="prompt-group__scene-name">{{ row.name }}</span>
            <span class="prompt-group__scene-key">{{ row.scene }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="内容摘要" prop="summary" min-width="240" show-overflow-tooltip />
      <el-table-column label="来源" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.source === 'custom' ? 'primary' : 'info'">
            {{ promptSourceLabel(row.source) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新人" width="110">
        <template #default="{ row }">
          {{ row.updatedByName ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="176">
        <template #default="{ row }">
          {{ row.updatedAt ? formatDateTime(row.updatedAt) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row as AiPromptListItem)">编辑</el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="row.source !== 'custom'"
            @click="reset(row as AiPromptListItem)"
          >
            恢复默认
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="current ? `编辑提示词 · ${current.name}` : '编辑提示词'"
      width="720px"
      destroy-on-close
    >
      <el-alert
        v-if="saveError"
        :title="saveError"
        type="error"
        :closable="false"
        class="prompt-group__dialog-error"
      />

      <div v-if="detailError" class="prompt-group__detail-error">
        <span>{{ detailError }}</span>
        <el-button size="small" @click="current && loadDetail(current.scene)">重试</el-button>
      </div>

      <el-form
        v-else
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item v-loading="detailLoading" label="提示词内容" prop="content">
          <el-input
            ref="editorRef"
            v-model="form.content"
            type="textarea"
            :rows="12"
            placeholder="提示词内容，变量写作 {{变量名}}"
          />
        </el-form-item>
      </el-form>

      <!-- 可用变量占位说明（交互 2.4）：悬浮看说明，点击插入占位 -->
      <div class="prompt-group__vars">
        <span class="prompt-group__vars-label">可用变量</span>
        <el-tooltip
          v-for="variable in store.promptDetails[current?.scene ?? '']?.variables ?? []"
          :key="variable.name"
          :content="`${variable.desc}${variable.required ? '（必填）' : '（可选）'}`"
          placement="top"
        >
          <el-tag class="prompt-group__var" size="small" @click="insertVariable(variable.name)">
            {{ variable.name }}
          </el-tag>
        </el-tooltip>
        <span
          v-if="!(store.promptDetails[current?.scene ?? '']?.variables ?? []).length"
          class="prompt-group__vars-empty"
        >
          该场景无可用变量
        </span>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="detailError !== ''"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.prompt-group {
  min-height: 0;
}

.prompt-group__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
}

.prompt-group__scene {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.prompt-group__scene-name {
  color: var(--color-neutral-900);
}

.prompt-group__scene-key {
  font-family: monospace;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.prompt-group__dialog-error {
  margin-bottom: var(--space-md);
}

.prompt-group__detail-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  background: var(--color-danger-light);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-md);
}

.prompt-group__vars {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding-top: var(--space-sm);
}

.prompt-group__vars-label {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.prompt-group__var {
  cursor: pointer;
}

.prompt-group__vars-empty {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}
</style>
