import 'kityminder-core/dist/kityminder.core.css'
// kity/kityminder-core 是 2014 年的老库：给原始值挂属性、使用 arguments.callee，
// 均与 ESM 强制的严格模式冲突，只能以经典 script 标签（非严格模式）加载
import kityUrl from 'kity/dist/kity.js?url'
import kityminderUrl from 'kityminder-core/dist/kityminder.core.js?url'
import type { KityMinderGlobal } from './types'
import { registerBadgesModule } from './badges'

const loadedScripts = new Map<string, Promise<void>>()

// 以经典 script 标签加载（脚本在非严格模式下执行），并发调用时复用同一 Promise
function loadLegacyScript(src: string): Promise<void> {
  let pending = loadedScripts.get(src)
  if (!pending) {
    // HMR 重求值会清空模块级缓存，但 head 中的 script 标签仍在；
    // 直接复用可避免脚本二次执行整体替换 window.kityminder（连同已注册的模块池）
    if (document.querySelector(`script[src="${src}"]`)) {
      pending = Promise.resolve()
    } else {
      pending = new Promise<void>((resolve, reject) => {
        const el = document.createElement('script')
        el.src = src
        el.onload = () => resolve()
        el.onerror = () => {
          loadedScripts.delete(src)
          el.remove()
          reject(new Error(`脚本加载失败: ${src}`))
        }
        document.head.appendChild(el)
      })
    }
    loadedScripts.set(src, pending)
  }
  return pending
}

/**
 * 加载脑图引擎并注册徽标模块（三模式组件共用入口）。
 * kityminder-core 求值时直接读取 window.kity，必须保证 kity 先完成加载。
 */
export async function loadMinderEngine(): Promise<KityMinderGlobal> {
  await loadLegacyScript(kityUrl)
  await loadLegacyScript(kityminderUrl)
  const km = window.kityminder
  if (!km?.Minder) throw new Error('脑图引擎加载失败')
  // 徽标模块须在 new Minder 之前注册；失败不阻断加载但必须留痕
  if (!registerBadgesModule()) {
    console.warn('[minder] 徽标模块注册失败，节点类型/优先级色标将不可见')
  }
  return km
}
