import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchInvitationCopyLink, fetchInvitations, revokeInvitation } from '@/services/workspace'
import type { InvitationListItem } from '@/types'
import {
  buildInviteUrl,
  buildInvitationShareText,
  canCopyInvitation,
  canExpireInvitation,
} from '@/utils/workspaceInvitation'

export function useInvitationList() {
  const authStore = useAuthStore()

  const canManageInvitation = computed(() => authStore.hasPermission('ws-invitation:manage'))
  const invitations = ref<InvitationListItem[]>([])
  const invitationsLoading = ref(false)
  const invitationsLoaded = ref(false)
  const invitationsLoadError = ref(false)
  const invitationTotal = ref(0)
  const invQuery = reactive({ pageNo: 1, pageSize: 20 })
  const copyingInvitationId = ref('')
  const copyingLatestInvitation = ref(false)
  const revokingInvitationId = ref('')

  let invitationRequestId = 0

  async function loadInvitations(): Promise<boolean> {
    const requestId = ++invitationRequestId
    // 权限是异步拉取的，硬刷新或深链进入时晚于页面挂载；等它就绪再判定，
    // 否则有权限的用户会看到一张从未请求过的空表
    if (!authStore.permissionsLoaded) {
      await authStore.whenPermissionsReady()
      if (requestId !== invitationRequestId) return false
    }
    if (!canManageInvitation.value) return false
    invitationsLoading.value = true
    invitationsLoadError.value = false
    try {
      const page = await fetchInvitations({ pageNo: invQuery.pageNo, pageSize: invQuery.pageSize })
      if (requestId !== invitationRequestId) return false
      invitations.value = page.list
      invitationTotal.value = page.total
      invitationsLoaded.value = true
      return true
    } catch (err) {
      if (requestId === invitationRequestId) {
        invitationsLoadError.value = true
        ElMessage.error(err instanceof Error ? err.message : '加载邀请链接失败')
      }
      return false
    } finally {
      if (requestId === invitationRequestId) {
        invitationsLoading.value = false
      }
    }
  }

  function handleInvitationPageChange(pageNo: number): void {
    invQuery.pageNo = pageNo
    void loadInvitations()
  }

  function buildCopyText(url: string, invitation?: InvitationListItem): string {
    return buildInvitationShareText(url, {
      workspaceName: authStore.activeWorkspace?.name,
      inviterUsername: authStore.user?.username,
      exhausted: invitation?.effectiveStatus === 'exhausted',
    })
  }

  async function copyInvitation(invitation: InvitationListItem): Promise<void> {
    if (!canCopyInvitation(invitation) || copyingInvitationId.value) return
    copyingInvitationId.value = invitation.id
    try {
      const result = await fetchInvitationCopyLink(invitation.id)
      if (!navigator.clipboard) throw new Error('当前浏览器不支持自动复制')
      await navigator.clipboard.writeText(
        buildCopyText(buildInviteUrl(result.token), invitation),
      )
      ElMessage.success('邀请链接及说明已复制')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '复制邀请链接失败')
    } finally {
      copyingInvitationId.value = ''
    }
  }

  async function handleCopyLatestInvitation(): Promise<void> {
    if (copyingLatestInvitation.value) return
    copyingLatestInvitation.value = true
    try {
      if (!invitationsLoaded.value && !(await loadInvitations())) return
      const latest = invitations.value.find(
        (invitation) => invitation.effectiveStatus === 'active',
      )
      if (!latest) {
        ElMessage.warning('暂无可复制的有效邀请链接')
        return
      }
      await copyInvitation(latest)
    } finally {
      copyingLatestInvitation.value = false
    }
  }

  async function handleExpireInvitation(invitation: InvitationListItem): Promise<void> {
    if (!canExpireInvitation(invitation) || revokingInvitationId.value) return
    try {
      await ElMessageBox.confirm('确定要使该邀请链接失效吗？失效后将不可再使用。', '确认失效', {
        type: 'warning',
      })
    } catch {
      return
    }

    revokingInvitationId.value = invitation.id
    try {
      await revokeInvitation(invitation.id)
      ElMessage.success('邀请链接已失效')
      void loadInvitations()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '邀请链接失效失败')
    } finally {
      revokingInvitationId.value = ''
    }
  }

  function dispose(): void {
    invitationRequestId += 1
    // 页面卸载后不再有渲染方消费 loading，直接复位避免残留
    invitationsLoading.value = false
  }

  return {
    canManageInvitation,
    invitations,
    invitationsLoading,
    invitationsLoaded,
    invitationsLoadError,
    invitationTotal,
    invQuery,
    loadInvitations,
    handleInvitationPageChange,
    copyingInvitationId,
    copyInvitation,
    copyingLatestInvitation,
    handleCopyLatestInvitation,
    revokingInvitationId,
    handleExpireInvitation,
    dispose,
  }
}
