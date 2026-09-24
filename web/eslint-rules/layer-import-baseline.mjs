const exception = {
  reason: '存量纯展示适配函数仍位于 components；本次先冻结既有边界，新增依赖继续阻断',
  owner: 'frontend',
  approvedBy: 'CODE-007 主会话任务负责人',
  reviewedAt: '2026-09-24',
  expiresOn: '2026-12-31',
  remediation: '将展示适配函数下沉到 composables/types 或上移调用后删除此基线条目',
}

export const layerImportBaseline = [
  {
    file: 'src/composables/project/functional-testing/plan/usePlanOrderRecommend.ts',
    importSource: '@/components/project/functional-testing/plan/planOrderRecommend',
    ...exception,
  },
  {
    file: 'src/composables/project/functional-testing/review/useReviewAiSummary.ts',
    importSource: '@/components/project/functional-testing/review/reviewSummary',
    ...exception,
  },
  {
    file: 'src/composables/project/functional-testing/review/useReviewAiConclusion.ts',
    importSource: '@/components/project/functional-testing/review/reviewSummary',
    ...exception,
  },
  {
    file: 'src/composables/project/functional-testing/review/useReviewAiConclusion.ts',
    importSource: '@/components/project/functional-testing/review/conclusionPresentation',
    ...exception,
  },
]
