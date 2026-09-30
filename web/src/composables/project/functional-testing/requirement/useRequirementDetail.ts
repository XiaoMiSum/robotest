import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRequirement } from '@/services/project'

/** EP popper 以 popper-class 挂在 teleport 后的根节点，作为「点击在气泡内」的命中锚点 */
const POP_CLASS = 'req-detail-pop'
const CAPTURE = { capture: true }

/**
 * 需求条目行内 [明细] 气泡（交互设计 52 §1.2）：
 * 就近展示条目 Markdown 正文，关闭方式为 [×] / 再次点击 [明细] / 点击空白 / ESC 四种，且不联动勾选。
 * 正文拉取与关闭手势下沉于此，选取器组件保持纯展示。
 */
export function useRequirementDetail() {
  const detailId = ref('')
  const content = ref('')
  const loading = ref(false)

  async function load(id: string): Promise<void> {
    loading.value = true
    try {
      const detail = await getRequirement(id)
      // 响应可能晚于切换/关闭，迟到结果直接丢弃，避免展示错条目
      if (detailId.value === id) content.value = detail.content
    } catch (err) {
      if (detailId.value === id) {
        ElMessage.error(err instanceof Error ? err.message : '明细加载失败')
      }
    } finally {
      loading.value = false
    }
  }

  function handleOutsideClick(event: MouseEvent): void {
    const target = event.target
    if (!(target instanceof Element)) return
    // 气泡内交由 [×]，[明细] 上交由按钮自身切换：否则 capture 先收起、按钮再打开，表现为关不掉
    if (target.closest(`.${POP_CLASS}`) || target.closest('[data-req-detail]')) return
    close()
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Escape') return
    // 只收气泡，不越权关闭选取器弹窗（由内向外逐层关闭）
    event.stopPropagation()
    close()
  }

  // addEventListener 对同函数同选项幂等，切换条目时重复绑定无副作用
  function bind(): void {
    window.addEventListener('click', handleOutsideClick, CAPTURE)
    window.addEventListener('keydown', handleKeydown, CAPTURE)
  }

  function unbind(): void {
    window.removeEventListener('click', handleOutsideClick, CAPTURE)
    window.removeEventListener('keydown', handleKeydown, CAPTURE)
  }

  function open(id: string): void {
    detailId.value = id
    content.value = ''
    bind()
    void load(id)
  }

  function close(): void {
    detailId.value = ''
    unbind()
  }

  /** 再次点击同一 [明细] 收起，点击另一条则切换内容（52 §1.2） */
  function toggle(id: string): void {
    if (detailId.value === id) close()
    else open(id)
  }

  onBeforeUnmount(unbind)

  return { detailId, content, loading, toggle, close }
}
