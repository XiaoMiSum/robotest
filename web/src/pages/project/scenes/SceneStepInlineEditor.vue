<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import type { ApiComponentListItem, ApiComponentType, ApiSceneStepItem } from '@/types'
import { fetchEnvironmentDetail } from '@/services/project/environment'
import { fetchComponents } from '@/services/project/component'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import {
  STEP_TYPE_OPTIONS,
  parseRequestConfig,
  type ValidatorItem,
  type ExtractorItem,
  createValidator,
  createExtractor,
  serializeValidators,
  serializeExtractors,
  stepValidatorsFromComponents,
  stepExtractorsFromComponents,
} from '../scenesModel'
import RequestConfigEditor from './RequestConfigEditor.vue'
import ValidatorsExtractorsPanes from '@/components/api-testing/ValidatorsExtractorsPanes.vue'

const props = defineProps<{
  step: ApiSceneStepItem | null
  draft?: boolean
  environmentId?: string | null
}>()

// JDBC 数据源下拉：从当前选择的环境获取可选数据源（值存 ref_name）
const datasourceOptions = ref<{ value: string; label: string }[]>([])

// HTTP 引用配置下拉：从当前环境获取可选 HTTP 配置（值存 ref_name），有默认则预选
const httpConfigOptions = ref<{ value: string; label: string; isDefault: boolean }[]>([])
const httpRefName = ref('')

watch(
  () => props.environmentId,
  async (envId) => {
    if (!envId) { datasourceOptions.value = []; httpConfigOptions.value = []; return }
    try {
      const detail = await fetchEnvironmentDetail(envId)
      datasourceOptions.value = detail.dataSources
        .filter((ds) => ds.refName)
        .map((ds) => ({ value: String(ds.refName), label: `${ds.name}（${ds.refName}）` }))
      httpConfigOptions.value = detail.httpConfigs
        .filter((hc) => hc.refName)
        .map((hc) => ({ value: String(hc.refName), label: `${hc.name}（${hc.refName}）`, isDefault: !!hc.isDefault }))
      // 引用配置预选：步骤未引用时（新步骤或未设置），有默认配置选默认，否则选第一个
      if (!httpRefName.value) {
        const def = httpConfigOptions.value.find((o) => o.isDefault) ?? httpConfigOptions.value[0]
        if (def) httpRefName.value = def.value
      }
      // 数据源预选：步骤未选择时（新步骤或未设置）选第一个；JDBC 数据源无默认标记
      if (!jdbcDatasource.value && datasourceOptions.value.length) {
        jdbcDatasource.value = datasourceOptions.value[0].value
      }
    } catch {
      datasourceOptions.value = []
      httpConfigOptions.value = []
    }
  },
  { immediate: true },
)

// ==================== 编辑态工作副本 ====================
const formName = ref('')
const formEnabled = ref(true)
const formStepType = ref('http')
const formMethod = ref('GET')
const formUrl = ref('')
const reqHeaders = ref<{ key: string; value: string; enabled: boolean }[]>([])
const reqParams = ref<{ key: string; value: string; enabled: boolean }[]>([])
const reqBody = ref<{ type: string; content: unknown }>({ type: 'none', content: null })
const validators = ref<ValidatorItem[]>([])
const extractors = ref<ExtractorItem[]>([])
const jdbcDatasource = ref('')
const jdbcSql = ref('')
const jdbcArgs = ref<string[]>([])

// jdbc 分区：SQL（请求配置） / 验证器 / 提取器 tabs（http 复用 RequestConfigEditor 内嵌 tabs，无外层 tabs）
const jdbcTab = ref<'sql' | 'validators' | 'extractors'>('sql')

// 空态默认行：字段全空，序列化时被过滤，仅提供即时输入起点（避免默认 target/source 被持久化）
function emptyValidatorRow(): ValidatorItem {
  return { id: crypto.randomUUID(), name: '', enabled: true, target: '', condition: '', expected: '', expression: '' }
}
function emptyExtractorRow(): ExtractorItem {
  return { id: crypto.randomUUID(), name: '', enabled: true, source: '', expression: '', variableName: '' }
}

// 当前编辑的目标（编辑时浅拷贝对象字段，保存时写回并 emit commit）
let source: ApiSceneStepItem | null = null

watch(() => props.step, (s) => {
  if (!s) return
  source = s
  formName.value = s.name
  formEnabled.value = s.enabled
  formStepType.value = s.stepType === 'jdbc' ? 'jdbc' : 'http'
  const cfg = parseRequestConfig(s.requestConfig)
  formMethod.value = String(cfg.method ?? 'GET')
  formUrl.value = String(cfg.url ?? '')
  httpRefName.value = String(s.requestConfig?.refName ?? '')
  reqHeaders.value = (cfg.headers ?? []).map((h) => ({ ...h }))
  reqParams.value = (cfg.params ?? []).map((p) => ({ ...p }))
  reqBody.value = cfg.body ?? { type: 'none', content: null }
  jdbcDatasource.value = String(s.requestConfig?.datasource ?? '')
  jdbcSql.value = String(s.requestConfig?.sql ?? '')
  jdbcArgs.value = Array.isArray(s.requestConfig?.args)
    ? (s.requestConfig.args as unknown[]).map((a) => String(a))
    : []
  validators.value = (s.validators ?? []).map((v) => ({ ...(v as unknown as ValidatorItem) }))
  extractors.value = (s.extractors ?? []).map((e) => ({ ...(e as unknown as ExtractorItem) }))
  if (validators.value.length === 0) validators.value = [emptyValidatorRow()]
  if (extractors.value.length === 0) extractors.value = [emptyExtractorRow()]
}, { immediate: true })

function addValidator() { validators.value.push(createValidator()) }
function addExtractor() { extractors.value.push(createExtractor()) }

// ==================== 从公共组件获取（验证器/提取器，复制引入） ====================
type StepAssetKind = 'validator' | 'extractor'
const STEP_ASSET_TYPE: Record<StepAssetKind, ApiComponentType> = {
  validator: 'validator',
  extractor: 'extractor',
}
const STEP_ASSET_TITLE: Record<StepAssetKind, string> = {
  validator: '从公共组件获取验证器',
  extractor: '从公共组件获取提取器',
}
const STEP_ASSET_NAME: Record<StepAssetKind, string> = {
  validator: '验证器',
  extractor: '提取器',
}
const assetPickerVisible = ref(false)
const assetPickerLoading = ref(false)
const assetPickerItems = ref<ApiComponentListItem[]>([])
const assetPickerKeyword = ref('')
const assetPickerKind = ref<StepAssetKind>('validator')

async function loadAssetPicker(): Promise<void> {
  assetPickerLoading.value = true
  try {
    const result = await fetchComponents({
      type: STEP_ASSET_TYPE[assetPickerKind.value],
      enabled: true,
      pageNo: 1,
      pageSize: 100,
      keyword: assetPickerKeyword.value.trim() || undefined,
    })
    assetPickerItems.value = result.list
  } catch {
    ElMessage.error('公共组件加载失败')
  } finally {
    assetPickerLoading.value = false
  }
}

function openAssetPicker(kind: StepAssetKind) {
  assetPickerKind.value = kind
  assetPickerKeyword.value = ''
  assetPickerVisible.value = true
  void loadAssetPicker()
}

function handleAssetPicked(rows: ApiComponentListItem[]) {
  if (rows.length === 0) return
  if (assetPickerKind.value === 'validator') {
    validators.value.push(...stepValidatorsFromComponents(rows))
  } else {
    extractors.value.push(...stepExtractorsFromComponents(rows))
  }
  ElMessage.success(`已引入 ${rows.length} 个${STEP_ASSET_NAME[assetPickerKind.value]}`)
}

function buildRequestConfig(): Record<string, unknown> {
  if (formStepType.value === 'jdbc') {
    return {
      datasource: jdbcDatasource.value.trim(),
      sql: jdbcSql.value,
      // 仅保留非空参数
      args: jdbcArgs.value.filter((a) => a).length ? jdbcArgs.value.filter((a) => a) : undefined,
    }
  }
  return {
    method: formMethod.value,
    url: formUrl.value,
    refName: httpRefName.value || undefined,
    headers: reqHeaders.value.filter((h) => h.key.trim()),
    params: reqParams.value.filter((p) => p.key.trim()),
    body: reqBody.value,
  }
}

// 编辑即生效：任一字段变更立即写回源步骤对象（后续随场景保存统一持久化）
watch(
  [formName, formEnabled, formStepType, formMethod, formUrl, httpRefName, reqHeaders, reqParams, reqBody, jdbcDatasource, jdbcSql, jdbcArgs, validators, extractors],
  () => {
    if (!source) return
    const cfg = buildRequestConfig()
    source.name = formName.value.trim()
    source.enabled = formEnabled.value
    source.stepType = formStepType.value
    source.requestConfig = cfg
    source.validators = serializeValidators(validators.value)
    source.extractors = serializeExtractors(extractors.value)
  },
  { deep: true },
)
</script>

<template>
  <div class="step-inline" data-test="step-inline-editor">
    <header class="step-inline__head">
      <el-input v-model="formName" placeholder="步骤名称" class="step-inline__name" data-test="step-name" />
      <el-switch v-model="formEnabled" active-text="启用" />
      <el-divider direction="vertical" />
      <el-radio-group v-model="formStepType" size="small">
        <el-radio-button v-for="opt in STEP_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <el-select
        v-if="formStepType === 'http'"
        v-model="httpRefName"
        placeholder="选择环境 HTTP 配置"
        filterable
        class="step-inline__ref-select"
      >
        <el-option v-for="opt in httpConfigOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
      </el-select>
      <el-select
        v-else
        v-model="jdbcDatasource"
        placeholder="选择环境数据源"
        filterable
        class="step-inline__ref-select"
      >
        <el-option v-for="opt in datasourceOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
      </el-select>
    </header>

    <div class="step-inline__body">
      <template v-if="formStepType === 'http'">
        <!-- http：验证器/提取器直接并入请求配置 tabs（请求头/Query/请求体/验证器/提取器） -->
        <RequestConfigEditor
          :method="formMethod"
          :url="formUrl"
          :headers="reqHeaders"
          :params="reqParams"
          :body="reqBody"
          :validators="validators"
          :extractors="extractors"
          @update:method="(v: string) => (formMethod = v)"
          @update:url="(v: string) => (formUrl = v)"
          @update:headers="(v: typeof reqHeaders) => (reqHeaders = v)"
          @update:params="(v: typeof reqParams) => (reqParams = v)"
          @update:body="(v: typeof reqBody) => (reqBody = v)"
          @update:validators="(v) => (validators = v as ValidatorItem[])"
          @update:extractors="(v) => (extractors = v as ExtractorItem[])"
          @add-validator="addValidator"
          @add-extractor="addExtractor"
          @import-validators="() => openAssetPicker('validator')"
          @import-extractors="() => openAssetPicker('extractor')"
        />
      </template>

      <template v-else>
        <!-- jdbc：与 http 同构的 tabs，SQL 为请求配置 tab，验证器/提取器随后 -->
        <el-tabs v-model="jdbcTab" class="step-inline__tabs">
          <el-tab-pane label="SQL" name="sql">
            <section class="step-inline__section">
              <el-form label-position="top">
                <el-form-item label="SQL 语句">
                  <el-input v-model="jdbcSql" type="textarea" :rows="5" placeholder="SELECT * FROM table WHERE id = ?" />
                </el-form-item>
                <el-form-item class="step-inline__args-item">
                  <template #label>
                    参数（? 占位符对应）
                    <el-button link type="primary" size="small" class="step-inline__args-add" @click="jdbcArgs.push('')">+ 添加参数</el-button>
                  </template>
                  <div class="step-inline__args">
                    <div v-for="(_, i) in jdbcArgs" :key="i" class="step-inline__arg-row">
                      <el-input v-model="jdbcArgs[i]" placeholder="参数值" />
                      <el-button link size="small" type="danger" @click="jdbcArgs.splice(i, 1)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </el-form-item>
              </el-form>
            </section>
          </el-tab-pane>

          <ValidatorsExtractorsPanes
            :validators="validators"
            :extractors="extractors"
            @update:validators="(v) => (validators = v as ValidatorItem[])"
            @update:extractors="(v) => (extractors = v as ExtractorItem[])"
            @add-validator="addValidator"
            @add-extractor="addExtractor"
            @import-validators="() => openAssetPicker('validator')"
            @import-extractors="() => openAssetPicker('extractor')"
          />
        </el-tabs>
      </template>
    </div>
  </div>

  <!-- 从公共组件获取 验证器/提取器 -->
  <ExtractorAssetPicker
    v-model="assetPickerVisible"
    :loading="assetPickerLoading"
    :items="assetPickerItems"
    :keyword="assetPickerKeyword"
    :title="STEP_ASSET_TITLE[assetPickerKind]"
    @update:keyword="assetPickerKeyword = $event"
    @search="loadAssetPicker"
    @confirm="handleAssetPicked"
  />
</template>

<style scoped lang="scss">
.step-inline {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;

  &__head {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: var(--space-md);
    padding: var(--space-md) var(--space-lg);
    border-bottom: 1px solid var(--color-neutral-100);
    background: var(--color-neutral-50);
  }

  &__name {
    flex: 1;
    max-width: 320px;
  }

  &__body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    scrollbar-width: none;
    display: flex;
    flex-direction: column;
    gap: var(--space-lg);
    padding: var(--space-lg);

    &::-webkit-scrollbar {
      display: none;
    }
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: var(--space-sm);
  }

  // tabs 统一 http/jdbc 分区结构；收窄默认间距，内容区零内边距
  &__tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--space-sm);
    }

    :deep(.el-tabs__content) {
      padding: 0;
    }
  }

  // 环境引用选择器：置于头部类型选择右侧水平对齐，定宽不收缩（标签过长溢出省略）
  &__ref-select {
    width: 240px;
    flex-shrink: 0;
  }

  &__args-item {
    // label 本身作为整行 flex，按钮推到最右侧
    :deep(.el-form-item__label) {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
    }
  }

  &__args {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    width: 100%;
  }

  &__arg-row {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
  }
}
</style>