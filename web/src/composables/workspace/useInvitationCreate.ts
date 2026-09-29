import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createInvitation } from '@/services/workspace'
import { buildInviteUrl, buildInvitationShareText } from '@/utils/workspaceInvitation'

export function useInvitationCreate(onCreated: () => void) {
  const authStore = useAuthStore()

  const createDialogVisible = ref(false)
  const createForm = reactive({ expiresAt: '' as string, maxUses: null as number | null })
  const createSubmitting = ref(false)
  const createdInviteLink = ref('')
  const linkDialogVisible = ref(false)

  function openCreateDialog(): void {
    createForm.expiresAt = ''
    createForm.maxUses = null
    createDialogVisible.value = true
  }

  function setExpiresAt(value: string): void {
    createForm.expiresAt = value
  }

  function setMaxUses(value: number | null): void {
    createForm.maxUses = value
  }

  async function submitCreateInvitation(): Promise<void> {
    createSubmitting.value = true
    try {
      const invitation = await createInvitation({
        expiresAt: createForm.expiresAt || null,
        maxUses: createForm.maxUses,
      })
      ElMessage.success('邀请链接已创建')
      createDialogVisible.value = false
      createdInviteLink.value = buildInviteUrl(invitation.token)
      linkDialogVisible.value = true
      onCreated()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '创建邀请链接失败')
    } finally {
      createSubmitting.value = false
    }
  }

  async function handleCopyLink(url: string): Promise<void> {
    try {
      if (!navigator.clipboard) throw new Error('当前浏览器不支持自动复制')
      await navigator.clipboard.writeText(
        buildInvitationShareText(url, {
          workspaceName: authStore.activeWorkspace?.name,
          inviterUsername: authStore.user?.username,
        }),
      )
      ElMessage.success('邀请链接及说明已复制')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '复制失败，请手动复制')
    }
  }

  return {
    createDialogVisible,
    createForm,
    createSubmitting,
    createdInviteLink,
    linkDialogVisible,
    openCreateDialog,
    setExpiresAt,
    setMaxUses,
    submitCreateInvitation,
    handleCopyLink,
  }
}
