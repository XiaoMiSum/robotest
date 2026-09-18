import type { AiReviewConclusionVerdict } from '@/types'

/** 结论判定展示模型（纯函数产出，便于单测） */
export interface VerdictPresentation {
  verdict: AiReviewConclusionVerdict
  label: string
  tagType: 'success' | 'danger' | 'warning'
}

/** 判定 → 中文标签与语义色（PASS 绿 / FAIL 红 / INCONCLUSIVE 黄，06 §5.2） */
export function verdictPresentation(verdict: AiReviewConclusionVerdict): VerdictPresentation {
  const map: Record<AiReviewConclusionVerdict, VerdictPresentation> = {
    PASS: { verdict: 'PASS', label: '通过', tagType: 'success' },
    FAIL: { verdict: 'FAIL', label: '不通过', tagType: 'danger' },
    INCONCLUSIVE: { verdict: 'INCONCLUSIVE', label: '无法判定', tagType: 'warning' },
  }
  return map[verdict]
}