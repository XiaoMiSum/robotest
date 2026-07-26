<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import api from '@/services'
import type { Result, LoginResult } from '@/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()

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

    authStore.setLogin(result.accessToken, result.refreshToken, result.user, null)

    await authStore.loadPermissions()

    if (result.hasWorkspace) {
      router.push('/workspaces')
    } else if (authStore.hasSystemPermission) {
      navStore.setMode('admin')
      router.push('/admin/dashboard')
    } else {
      router.push('/workspaces')
    }
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
      <div class="login-card__brand">
        <span class="login-card__logo">RoboTest</span>
      </div>
      <div class="login-card__title">软件测试平台</div>
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
  background: linear-gradient(135deg, var(--color-primary-600) 0%, #4f46e5 50%, #7c3aed 100%);
}

.login-card {
  width: 400px;
  padding: var(--space-2xl) var(--space-xl);
  background: var(--color-neutral-0);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.login-card__brand {
  text-align: center;
  margin-bottom: var(--space-md);
}

.login-card__logo {
  font-size: 24px;
  font-weight: 800;
  color: var(--color-primary-600);
  letter-spacing: -0.03em;
}

.login-card__title {
  text-align: center;
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-xl);
}

.login-card__form {
  width: 100%;
}

.login-card__btn {
  width: 100%;
}
</style>
