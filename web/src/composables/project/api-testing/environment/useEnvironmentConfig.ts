import { ref, computed, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { testHttpConfig, testDataSourceConfig } from '@/services/project/api-testing/environment'
import { createEmptyHttpConfig, DRIVER_OPTIONS, resolveEnvironmentError } from '@/composables/project/api-testing/environment/environmentsModel'
import type { ApiHeaderItem, ApiHttpConfigPayload, ApiDataSourcePayload } from '@/types'

export interface HttpConfigForm extends ApiHttpConfigPayload {
  id: string
  headers: ApiHeaderItem[]
}

export interface DsForm extends ApiDataSourcePayload {
  id: string
}

/** 连接测试内联结果：与 Toast 并存，便于对照表单持续展示（docs34 §1.5） */
export interface ConnResult {
  ok: boolean
  text: string
}

function cloneHeaders(source: ApiHttpConfigPayload): ApiHeaderItem[] {
  return (source.headers ?? []).map((h) => ({ ...h }))
}

/**
 * 环境 HTTP 配置管理（从 EnvironmentDetailPanel 提取）。
 */
export function useEnvironmentHttpConfig(configForms: Ref<HttpConfigForm[]>, localId: () => string) {
  const activeConfigId = ref('')
  const activeConfig = computed(() => configForms.value.find((c) => c.id === activeConfigId.value))
  const orderedConfigForms = computed(() =>
    [...configForms.value].sort((a, b) => Number(b.isDefault ?? false) - Number(a.isDefault ?? false)),
  )

  function selectConfig(config: HttpConfigForm) { activeConfigId.value = config.id }

  function addHttpConfig() {
    const source = createEmptyHttpConfig(configForms.value.length + 1)
    const next: HttpConfigForm = { id: localId(), name: source.name, refName: source.refName, baseUrl: source.baseUrl, isDefault: source.isDefault, headers: cloneHeaders(source) }
    configForms.value.push(next)
    activeConfigId.value = next.id
  }

  function removeHttpConfig(form: HttpConfigForm) {
    configForms.value = configForms.value.filter((c) => c !== form)
    if (activeConfigId.value === form.id) activeConfigId.value = configForms.value[0]?.id ?? ''
  }

  const testingHttpId = ref('')
  const httpConnResult = ref<ConnResult | null>(null)
  // 切换配置即失去结果所对应的上下文，避免残留上一条的结论
  watch(activeConfigId, () => { httpConnResult.value = null })

  async function runHttpTest(form: HttpConfigForm, environmentId?: string) {
    if (!environmentId) { ElMessage.warning('环境ID缺失'); return }
    if (!form.baseUrl?.trim()) { ElMessage.warning('请先填写 Base URL 再测试连接'); return }
    testingHttpId.value = form.id
    httpConnResult.value = null
    try {
      const result = await testHttpConfig(environmentId, { baseUrl: form.baseUrl.trim(), refName: form.refName })
      const text = result.success
        ? `连接成功：状态码 ${result.statusCode ?? '-'}，耗时 ${result.durationMs ?? '-'}ms`
        : result.message || '连接失败'
      httpConnResult.value = { ok: result.success, text }
      if (result.success) ElMessage.success(text)
      else ElMessage.error(text)
    } catch (err) {
      const text = resolveEnvironmentError(err)
      httpConnResult.value = { ok: false, text }
      ElMessage.error(text)
    } finally { testingHttpId.value = '' }
  }

  return { activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig, testingHttpId, runHttpTest, httpConnResult }
}

/**
 * 环境数据源管理（从 EnvironmentDetailPanel 提取）。
 */
export function useEnvironmentDatasource(dsForms: Ref<DsForm[]>, localId: () => string) {
  const activeDsId = ref('')
  const activeDs = computed(() => dsForms.value.find((d) => d.id === activeDsId.value))
  const orderedDsForms = computed(() =>
    [...dsForms.value].sort((a, b) => Number(b.isDefault ?? false) - Number(a.isDefault ?? false)),
  )

  function selectDs(form: DsForm) { activeDsId.value = form.id }

  const selectedDsDriverOption = computed(() => DRIVER_OPTIONS.find((o) => o.driver === activeDs.value?.driver))
  function handleDsDriverChange(driver: string) {
    const option = DRIVER_OPTIONS.find((o) => o.driver === driver)
    if (option && activeDs.value && !activeDs.value.url) activeDs.value.url = option.urlExample
  }

  function addDataSource() {
    const next: DsForm = { id: localId(), name: '', refName: `db_${dsForms.value.length + 1}`, driver: DRIVER_OPTIONS[0]?.driver ?? '', url: '', isDefault: false }
    dsForms.value.push(next)
    activeDsId.value = next.id
  }

  function removeDataSource(form: DsForm) {
    dsForms.value = dsForms.value.filter((d) => d !== form)
    if (activeDsId.value === form.id) activeDsId.value = dsForms.value[0]?.id ?? ''
  }

  const testingDsId = ref('')
  const dsConnResult = ref<ConnResult | null>(null)
  // 切换数据源即失去结果所对应的上下文，避免残留上一条的结论
  watch(activeDsId, () => { dsConnResult.value = null })

  async function runDsTest(form: DsForm, environmentId?: string) {
    if (!environmentId) { ElMessage.warning('环境ID缺失'); return }
    if (!form.url?.trim()) { ElMessage.warning('请先填写 URL 再测试连接'); return }
    testingDsId.value = form.id
    dsConnResult.value = null
    try {
      const result = await testDataSourceConfig(environmentId, { driver: form.driver, url: form.url.trim(), connectionProperties: form.connectionProperties })
      const text = result.success
        ? `连接成功${result.databaseVersion ? `：${result.databaseVersion}` : ''}`
        : result.message || '连接失败'
      dsConnResult.value = { ok: result.success, text }
      if (result.success) ElMessage.success(text)
      else ElMessage.error(text)
    } catch (err) {
      const text = resolveEnvironmentError(err)
      dsConnResult.value = { ok: false, text }
      ElMessage.error(text)
    } finally { testingDsId.value = '' }
  }

  return { activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange, addDataSource, removeDataSource, testingDsId, runDsTest, dsConnResult }
}
