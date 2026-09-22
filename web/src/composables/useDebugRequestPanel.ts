import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ApiDebugBodyKind,
  ApiDebugRawSubtype,
  ApiEnvironmentListItem,
  DebugTab,
} from '@/types'
import { fetchEnvironments } from '@/services/project/environment'
import { HTTP_METHODS, setBodyContentTypeHeader } from '@/pages/project/debugModel'

export type ParamTab = 'params' | 'auth' | 'headers' | 'body'

const BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'urlencoded', label: 'x-www-form-urlencoded' },
  { value: 'raw', label: 'raw' },
] as const satisfies ReadonlyArray<{ value: ApiDebugBodyKind; label: string }>

const SUBTYPES: ApiDebugRawSubtype[] = ['text', 'json', 'xml', 'html', 'javascript']

const COMMON_HEADERS = [
  'Accept',
  'Authorization',
  'Content-Type',
  'Cookie',
  'User-Agent',
  'X-Requested-With',
  'If-None-Match',
  'Origin',
] as const

const METHOD_COLORS: Record<string, string> = {
  GET: '#61affe',
  POST: '#49cc90',
  PUT: '#fca130',
  PATCH: '#50e3c2',
  DELETE: '#f93e3e',
  OPTIONS: '#0d5aa7',
  HEAD: '#9012fe',
  CONNECT: '#e8d44d',
}

export function useDebugRequestPanel(
  tab: () => DebugTab,
  environmentId: { value: string },
) {
  const environments = ref<ApiEnvironmentListItem[]>([])

  onMounted(async () => {
    try {
      environments.value = await fetchEnvironments()
      environmentId.value =
        environments.value.find((env) => env.isDefault)?.id ?? environments.value[0]?.id ?? ''
    } catch {
      // environment load failure should not block debugging
    }
  })

  const activeParamTab = ref<ParamTab>('params')

  function pickBodyType(type: ApiDebugBodyKind) {
    const t = tab()
    t.bodyType = type
    if (type === 'raw' && !t.bodies.raw) {
      t.bodies.raw = { text: '', subtype: 'json' }
    }
    setBodyContentTypeHeader(t, type, t.bodies.raw?.subtype)
  }

  const rawSubtype = computed<ApiDebugRawSubtype>({
    get() {
      return tab().bodies.raw?.subtype ?? 'json'
    },
    set(subtype: ApiDebugRawSubtype) {
      const t = tab()
      if (!t.bodies.raw) t.bodies.raw = { text: '', subtype }
      else t.bodies.raw.subtype = subtype
      if (t.bodyType === 'raw') setBodyContentTypeHeader(t, 'raw', subtype)
    },
  })

  const rawText = computed<string>({
    get() {
      return tab().bodies.raw?.text ?? ''
    },
    set(text: string) {
      const t = tab()
      if (!t.bodies.raw) t.bodies.raw = { text, subtype: 'json' }
      else t.bodies.raw.text = text
    },
  })

  function formatJsonBody() {
    try {
      const parsed: unknown = JSON.parse(rawText.value)
      rawText.value = JSON.stringify(parsed, null, 2)
    } catch {
      ElMessage.warning('请求体不是合法 JSON，无法格式化')
    }
  }

  const methodColor = computed(() => METHOD_COLORS[tab().method.toUpperCase()] ?? '#999')

  return {
    environments,
    activeParamTab,
    BODY_TYPES,
    SUBTYPES,
    COMMON_HEADERS,
    HTTP_METHODS,
    pickBodyType,
    rawSubtype,
    rawText,
    formatJsonBody,
    methodColor,
  }
}
