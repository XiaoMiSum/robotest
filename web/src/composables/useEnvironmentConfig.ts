import { ref, computed, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { testHttpConfig, testDataSourceConfig } from '@/services/project/environment'
import { createEmptyHttpConfig, DRIVER_OPTIONS, resolveEnvironmentError } from '@/pages/project/environmentsModel'
import type { ApiHeaderItem, ApiHttpConfigPayload, ApiDataSourcePayload } from '@/types'

export interface HttpConfigForm extends ApiHttpConfigPayload {
  id: string
  headers: ApiHeaderItem[]
}

export interface DsForm extends ApiDataSourcePayload {
  id: string
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
  async function runHttpTest(form: HttpConfigForm, environmentId?: string) {
    if (!environmentId) { ElMessage.warning('环境ID缺失'); return }
    if (!form.baseUrl?.trim()) { ElMessage.warning('请先填写 Base URL 再测试连接'); return }
    testingHttpId.value = form.id
    try {
      const result = await testHttpConfig(environmentId, { baseUrl: form.baseUrl.trim(), refName: form.refName })
      if (result.success) ElMessage.success(`连接成功：状态码 ${result.statusCode ?? '-'}，耗时 ${result.durationMs ?? '-'}ms`)
      else ElMessage.error(result.message || '连接失败')
    } catch (err) { ElMessage.error(resolveEnvironmentError(err)) } finally { testingHttpId.value = '' }
  }

  return { activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig, testingHttpId, runHttpTest }
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
  async function runDsTest(form: DsForm, environmentId?: string) {
    if (!environmentId) { ElMessage.warning('环境ID缺失'); return }
    if (!form.url?.trim()) { ElMessage.warning('请先填写 URL 再测试连接'); return }
    testingDsId.value = form.id
    try {
      const result = await testDataSourceConfig(environmentId, { driver: form.driver, url: form.url.trim(), connectionProperties: form.connectionProperties })
      if (result.success) ElMessage.success(`连接成功${result.databaseVersion ? `：${result.databaseVersion}` : ''}`)
      else ElMessage.error(result.message || '连接失败')
    } catch (err) { ElMessage.error(resolveEnvironmentError(err)) } finally { testingDsId.value = '' }
  }

  return { activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange, addDataSource, removeDataSource, testingDsId, runDsTest }
}
