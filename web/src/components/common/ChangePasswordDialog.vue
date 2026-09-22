<script setup lang="ts">
import { useChangePassword } from '@/composables/auth/useChangePassword'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'

const visible = defineModel<boolean>({ required: true })

const {
  oldPassword,
  newPassword,
  confirmPassword,
  submitting,
  submit,
} = useChangePassword(visible)
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
