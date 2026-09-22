import { ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/services/auth'
import { useAuthStore } from '@/stores/auth'

export function useChangePassword(visible: Ref<boolean>) {
  const router = useRouter()
  const authStore = useAuthStore()

  const oldPassword = ref('')
  const newPassword = ref('')
  const confirmPassword = ref('')
  const submitting = ref(false)

  watch(visible, (val) => {
    if (val) {
      oldPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
    }
  })

  async function submit() {
    if (!oldPassword.value) {
      ElMessage.warning('请输入原密码')
      return
    }
    if (newPassword.value.length < 8 || newPassword.value.length > 64) {
      ElMessage.warning('新密码长度为 8-64 个字符')
      return
    }
    if (newPassword.value !== confirmPassword.value) {
      ElMessage.warning('两次输入的新密码不一致')
      return
    }
    submitting.value = true
    try {
      await changePassword(oldPassword.value, newPassword.value)
      visible.value = false
      ElMessage.success('密码已修改，请重新登录')
      authStore.logout()
      router.push('/login')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '修改密码失败')
    } finally {
      submitting.value = false
    }
  }

  return {
    oldPassword,
    newPassword,
    confirmPassword,
    submitting,
    submit,
  }
}
