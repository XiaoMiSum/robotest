import type { AiMissingPoint, TestCaseModule } from '@/types'

/** 「转用例生成」目标文档选项：模块树展开为扁平文档清单，path 与 suggestedModulePath 同口径 */
export interface MissingPointDocumentOption {
  id: string
  name: string
  /** 「目录/文档」拼接路径（与后端 suggestedModulePath 同口径） */
  path: string
}

/** 勾选遗漏点拼接为需求文本（3.3 转用例生成）：序号 + 标题 + 说明 */
export function buildMissingPointText(points: AiMissingPoint[]): string {
  return points
    .map((point, index) => `${index + 1}. ${point.title}\n说明：${point.description}`)
    .join('\n\n')
}

/** 模块树展开为文档清单，path 为「目录A/目录B/文档」拼接 */
export function collectDocumentOptions(
  nodes: TestCaseModule[],
  parentPath = '',
): MissingPointDocumentOption[] {
  const options: MissingPointDocumentOption[] = []
  for (const node of nodes) {
    const path = parentPath ? `${parentPath}/${node.name}` : node.name
    if (node.type === 'document') {
      options.push({ id: node.id, name: node.name, path })
    } else {
      options.push(...collectDocumentOptions(node.children ?? [], path))
    }
  }
  return options
}

/**
 * 默认预选目标文档（3.3）：勾选点中出现次数最多的 suggestedModulePath 对应文档；
 * 路径无法匹配到现有文档时返回空串（不预选）。
 */
export function pickPreselectDocument(
  documents: MissingPointDocumentOption[],
  checkedPoints: AiMissingPoint[],
): string {
  const idByPath = new Map(documents.map((document) => [document.path, document.id]))
  const counts = new Map<string, number>()
  let bestId = ''
  let bestCount = 0
  for (const point of checkedPoints) {
    const docId = idByPath.get(point.suggestedModulePath ?? '')
    if (!docId) continue
    const count = (counts.get(docId) ?? 0) + 1
    counts.set(docId, count)
    if (count > bestCount) {
      bestCount = count
      bestId = docId
    }
  }
  return bestId
}
