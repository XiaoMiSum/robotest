<script setup lang="ts">
import type { ApiSceneStepItem } from '@/types'
import { STEP_TYPE_OPTIONS, VALIDATOR_TARGETS, VALIDATOR_CONDITIONS, EXTRACTOR_SOURCES } from '../scenesModel'
import RequestConfigEditor from './RequestConfigEditor.vue'
import { useStepEditorDrawer } from '@/composables/useStepEditorDrawer'

const props = defineProps<{ modelValue: boolean; sceneId?: string; step: ApiSceneStepItem | null }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
  (e: 'commit', step: ApiSceneStepItem): void
}>()

const {
  draftMode,
  visible,
  formName,
  formStepType,
  formMethod,
  formUrl,
  formEnabled,
  activeTab,
  reqHeaders,
  reqParams,
  reqBody,
  validators,
  extractors,
  stepVariables,
  variablesLoading,
  executionConfig,
  createMode,
  quickInterfaceId,
  quickMode,
  interfaceOptions,
  interfaceSearch,
  interfaceLoading,
  saving,
  handleCreateModeChange,
  loadInterfaces,
  addStepVariable,
  removeStepVariable,
  addValidator,
  removeValidator,
  addExtractor,
  removeExtractor,
  handleSave,
} = useStepEditorDrawer(props, emit)
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="step ? '编辑步骤' : '添加步骤'"
    size="680px"
    data-test="step-editor-drawer"
  >
    <div v-if="!step" class="step-editor__mode-switch">
      <el-radio-group :model-value="createMode" @update:model-value="(v) => handleCreateModeChange(v as 'manual' | 'quick')">
        <el-radio-button value="manual">手动创建</el-radio-button>
        <el-radio-button value="quick">通过接口快速创建</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="createMode === 'manual'">
      <el-tabs v-model="activeTab" type="border-card" class="step-editor__tabs">
        <el-tab-pane label="基本信息" name="basic">
          <el-form label-position="top">
            <el-form-item label="步骤名称" required>
              <el-input v-model="formName" placeholder="如：发送登录请求" data-test="step-name" />
            </el-form-item>
            <el-form-item label="步骤类型">
              <el-select v-model="formStepType" style="width: 100%">
                <el-option v-for="opt in STEP_TYPE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="formEnabled" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="请求配置" name="request">
          <RequestConfigEditor
            :method="formMethod"
            :url="formUrl"
            :headers="reqHeaders"
            :params="reqParams"
            :body="reqBody"
            @update:method="(v: string) => { formMethod = v }"
            @update:url="(v: string) => { formUrl = v }"
            @update:headers="(v: typeof reqHeaders) => { reqHeaders = v }"
            @update:params="(v: typeof reqParams) => { reqParams = v }"
            @update:body="(v: typeof reqBody) => { reqBody = v }"
          />
        </el-tab-pane>

        <el-tab-pane label="验证器" name="validators">
          <div class="step-editor__list-section">
            <div v-for="(v, i) in validators" :key="v.id" class="step-editor__validator-card">
              <div class="step-editor__card-row">
                <el-switch v-model="v.enabled" size="small" />
                <el-select v-model="v.target" size="small" class="step-editor__field--target" placeholder="验证目标">
                  <el-option v-for="t in VALIDATOR_TARGETS" :key="t.value" :value="t.value" :label="t.label" />
                </el-select>
                <el-select v-model="v.condition" size="small" class="step-editor__field--condition" placeholder="比较条件">
                  <el-option v-for="c in VALIDATOR_CONDITIONS" :key="c.value" :value="c.value" :label="c.label" />
                </el-select>
                <el-input v-model="v.expression" size="small" placeholder="表达式（如 $.code）" class="step-editor__field--flex" />
                <el-input v-model="v.expected" size="small" placeholder="期望值" class="step-editor__field--flex" />
                <el-button link size="small" type="danger" @click="removeValidator(i)">删除</el-button>
              </div>
            </div>
            <el-button size="small" @click="addValidator">+ 添加验证器</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="提取器" name="extractors">
          <div class="step-editor__list-section">
            <div v-for="(e, i) in extractors" :key="e.id" class="step-editor__validator-card">
              <div class="step-editor__card-row">
                <el-switch v-model="e.enabled" size="small" />
                <el-select v-model="e.source" size="small" class="step-editor__field--source" placeholder="提取来源">
                  <el-option v-for="s in EXTRACTOR_SOURCES" :key="s.value" :value="s.value" :label="s.label" />
                </el-select>
                <el-input v-model="e.expression" size="small" placeholder="表达式" class="step-editor__field--flex" />
                <el-input v-model="e.variableName" size="small" placeholder="变量名" class="step-editor__field--flex" />
                <el-button link size="small" type="danger" @click="removeExtractor(i)">删除</el-button>
              </div>
            </div>
            <el-button size="small" @click="addExtractor">+ 添加提取器</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="step && !draftMode" label="变量" name="variables">
          <div v-loading="variablesLoading" class="step-editor__list-section">
            <table v-if="stepVariables.length" class="step-editor__kv-table">
              <thead>
                <tr><th>变量名</th><th>值</th><th>来源</th><th>描述</th><th style="width:40px"></th></tr>
              </thead>
              <tbody>
                <tr v-for="(sv, i) in stepVariables" :key="sv.id">
                  <td><el-input v-model="sv.name" size="small" placeholder="变量名" /></td>
                  <td><el-input v-model="sv.value" size="small" placeholder="值（支持 ${} 引用）" /></td>
                  <td><el-tag size="small" :type="sv.source === 'interface' ? 'warning' : 'info'">{{ sv.source === 'interface' ? '接口' : '自定义' }}</el-tag></td>
                  <td><el-input v-model="sv.description" size="small" placeholder="描述" /></td>
                  <td><el-button link size="small" type="danger" @click="removeStepVariable(i)">✕</el-button></td>
                </tr>
              </tbody>
            </table>
            <div v-else class="step-editor__empty-text">暂无变量</div>
            <el-button size="small" @click="addStepVariable">+ 添加变量</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="执行配置" name="execution">
          <el-form label-position="top">
            <el-form-item label="条件表达式（为空则始终执行）">
              <el-input v-model="executionConfig.conditionExpression" type="textarea" :rows="3" placeholder="如：${status} == 'success'" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </template>

    <template v-if="createMode === 'quick'">
      <el-form label-position="top">
        <el-form-item label="选择接口" required>
          <el-select
            v-model="quickInterfaceId"
            filterable
            remote
            :remote-method="(q: string) => { interfaceSearch = q; loadInterfaces() }"
            :loading="interfaceLoading"
            placeholder="搜索接口名称"
            style="width: 100%"
          >
            <el-option
              v-for="item in interfaceOptions"
              :key="item.id"
              :value="item.id"
              :label="`${item.method} ${item.path} - ${item.name}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="同步模式">
          <el-radio-group v-model="quickMode">
            <el-radio value="copy">复制（独立副本）</el-radio>
            <el-radio value="link">链接（跟随源变更）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" data-test="step-save-btn" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.step-editor__mode-switch {
  margin-bottom: var(--space-lg);
}

.step-editor__tabs {
  :deep(.el-tabs__content) {
    padding: var(--space-md);
    max-height: calc(100vh - 220px);
    overflow-y: auto;
  }
}

.step-editor__list-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.step-editor__validator-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
}

.step-editor__card-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: nowrap;
}

.step-editor__field--flex {
  flex: 1 1 0;
  min-width: 0;
}

.step-editor__field--target {
  flex: 0 0 260px;
}

.step-editor__field--condition {
  flex: 0 0 150px;
}

.step-editor__field--source {
  flex: 0 0 240px;
}

.step-editor__empty-text {
  padding: var(--space-md);
  text-align: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.step-editor__kv-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: var(--space-sm);

  th {
    text-align: left;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-500);
    padding: 4px 6px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  td {
    padding: 4px 6px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
