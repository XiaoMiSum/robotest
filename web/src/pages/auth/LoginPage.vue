<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/services'
import type { Result, LoginResult } from '@/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  identifier: '',
  password: '',
})
const loading = ref(false)

async function handleLogin() {
  if (!form.identifier || !form.password) {
    ElMessage.warning('请输入用户名/邮箱和密码')
    return
  }

  loading.value = true
  try {
    const result = (await api.post<Result<LoginResult>>('/auth/login', {
      identifier: form.identifier,
      password: form.password,
    })) as unknown as LoginResult

    authStore.setLogin(result.accessToken, result.refreshToken, result.user, result.activeWorkspace)

    // Set workspace context in localStorage for API interceptor
    if (result.activeWorkspace) {
      authStore.setActiveWorkspace(result.activeWorkspace)
    }

    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
    ElMessage.success('登录成功')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-card__title">软件测试平台</h2>
      <el-form :model="form" class="login-card__form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input
            v-model="form.identifier"
            placeholder="用户名 / 邮箱"
            size="large"
            prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-card__btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.login-card__title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 32px;
}

.login-card__form {
  width: 100%;
}

.login-card__btn {
  width: 100%;
}
</style>
