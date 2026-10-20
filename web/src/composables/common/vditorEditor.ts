import type Vditor from 'vditor'
import { normalizeMarkdownImageUrls } from './markdownImage'

export type VditorInstance = InstanceType<typeof Vditor>

// 运行资源由 scripts/copy-vditor.mjs 构建期同步到 public/vditor：内网部署取不到 unpkg 公网 CDN
export const VDITOR_CDN = `${import.meta.env.BASE_URL}vditor`

// 所见即所得只保留写作与结构按钮；Vditor 自带全屏层级(90)会压在 Element Plus 弹层(2000+)之下，故不提供；
// 需要最大化时由调用方经 extraMenuItems 追加自有浮层按钮（层级 3000+，不受此限）
const TOOLBAR: string[] = [
  'headings',
  'bold',
  'italic',
  'strike',
  '|',
  'quote',
  'list',
  'ordered-list',
  'check',
  '|',
  'inline-code',
  'code',
  'link',
  'table',
  '|',
  'upload',
  '|',
  'undo',
  'redo',
  '|',
  'edit-mode',
]

export interface EditorCallbacks {
  /** 初始化完成；this.vditor 就绪前实例方法不可用，禁用与回填都挂在这一刻 */
  onReady(): void
  /** 输入回写（值已归一化为稳定下载路径） */
  onInput(markdown: string): void
  /** 自定义上传：Vditor 的 handler 不做校验也不回插，前置校验、上传与插入均由调用方完成 */
  onUpload(files: File[]): Promise<void>
}

/** Vditor 以 preview.maxWidth 居中正文栏（左右留白 = (编辑区宽 - maxWidth) / 2，桌面下限 35px），
 * 沿用其默认值作为还原基线；最大化时放宽该上限，正文即可撑满浮层宽度 */
export const DEFAULT_PREVIEW_MAX_WIDTH = 800

/** 预览居中上限挂在实例的 vditor.options 上（不在实例自身）；内核在窗口 resize 时按该值重算正文左右留白，
 * 最大化时放宽即撑满浮层宽度，退出还原为默认上限 */
export function setPreviewMaxWidth(vditor: VditorInstance, maxWidth: number): void {
  const options = vditor.vditor.options
  if (options.preview) {
    options.preview.maxWidth = maxWidth
  }
}

/** Vditor 4 未提供运行时改高度的方法：高度只能落在构造时传入的根元素内联样式上（内核 resize 拖拽同款），
 * 根元素为 flex 列布局、内容区 flex:1 自动跟随；options.height 供 hint 面板与滚动定位读取，需同步旧值 */
export function setEditorHeight(vditor: VditorInstance, height: string): void {
  vditor.vditor.options.height = height
  vditor.vditor.element.style.height = height
}

/** 组装所见即所得编辑器参数，屏蔽 Vditor 的原生默认值差异 */
export function createEditorOptions(
  config: {
    height?: string
    placeholder?: string
    value?: string
    /** 追加到内置工具栏末尾（最右侧）的自定义按钮 */
    extraMenuItems?: IMenuItem[]
  } & EditorCallbacks,
): IOptions {
  return {
    mode: 'wysiwyg',
    lang: 'zh_CN',
    theme: 'classic',
    height: config.height ?? '320px',
    placeholder: config.placeholder ?? '',
    value: config.value ?? '',
    cdn: VDITOR_CDN,
    // 正文可能带着换签后的临时地址，禁止落 localStorage 缓存
    cache: { enable: false },
    // 默认 800ms 才回写 v-model，紧跟着点保存会丢末尾输入；200ms 内连续输入仍合并为一次撤销
    undoDelay: 200,
    toolbar: [...TOOLBAR, ...(config.extraMenuItems ?? [])],
    // 展示他人写入的正文，沿用内置 GFM 过滤（替代原 dompurify）
    preview: { markdown: { sanitize: true } },
    upload: {
      accept: 'image/*',
      multiple: true,
      handler: async (files: File[]) => {
        await config.onUpload(files)
        return null
      },
    },
    input: (markdown: string) => config.onInput(normalizeMarkdownImageUrls(markdown)),
    after: config.onReady,
  }
}
