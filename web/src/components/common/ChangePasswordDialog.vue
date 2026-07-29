<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/services/auth'
import { useAuthStore } from '@/stores/auth'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'

const visible = defineModel<boolean>({ required: true })

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
</script>

<template>
  <el-dialog v-model="visible" title="修改密码" width="420px">
    <el-form label-width="80px" @submit.prevent>
      <el-form-item label="原密码">
        <el-input v-model="oldPassword" type="password" show-password placeholder="请输入原密码" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input
          v-model="newPassword"
          type="password"
          show-password
          placeholder="8-64 字符，建议包含大小写字母、数字、特殊字符"
        />
        <PasswordStrengthBar :password="newPassword" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>
