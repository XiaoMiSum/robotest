/** 项目动态中的资源类型。 */
export type ProjectActivityResourceType =
  | 'PROJECT'
  | 'TEST_CASE_DOCUMENT'
  | 'TEST_CASE_NODE'
  | 'TEST_REVIEW'
  | 'TEST_PLAN'
  | 'BUG'
  | 'REQUIREMENT'
  | string

/** 项目动态。 */
export interface ProjectActivity {
  id: string
  projectId: string
  actorId: string
  actorName: string
  resourceType: ProjectActivityResourceType
  resourceId: string
  resourceName: string
  action: string
  summary: string
  occurredAt: string
}
