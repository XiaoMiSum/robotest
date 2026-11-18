/** 只比较集合：顺序变化不算关联需求变化，避免误触发重新规划 */
export function isRequirementSetChanged(before: string[], after: string[]): boolean {
  if (before.length !== after.length) return true
  const beforeSet = new Set(before)
  return after.some((id) => !beforeSet.has(id))
}
