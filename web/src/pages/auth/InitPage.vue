<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { checkInitStatus, setupInit } from '@/services/init'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const form = reactive({
  password: '',
  confirmPassword: '',
})
const loading = ref(false)
const checking = ref(true)

onMounted(async () => {
  try {
    const status = await checkInitStatus()
    if (status.initialized) {
      // 系统已初始化，跳转到登录页
      router.replace('/login')
      return
    }
  } catch {
    // 网络错误等：继续展示初始化页面
  } finally {
    checking.value = false
  }
})

async function handleSetup() {
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码长度至少为 8 个字符')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await setupInit(form.password)
    ElMessage.success('系统初始化成功，请登录')
    router.push('/login')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '初始化失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="init-page">
    <div class="init-card">
      <div class="init-card__brand">
        <span class="init-card__logo">RoboTest</span>
      </div>
      <div class="init-card__title">系统初始化</div>
      <div class="init-card__desc">首次使用，请设置系统管理员密码</div>

      <div v-if="checking" class="init-card__loading">
        <el-icon class="is-loading" :size="24"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/></svg></el-icon>
        <span>检查系统状态...</span>
      </div>

      <el-form v-else :model="form" class="init-card__form" @submit.prevent="handleSetup">
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="设置管理员密码（至少 8 位）"
            size="large"
            prefix-icon="Lock"
            show-password
          />
          <PasswordStrengthBar :password="form.password" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleSetup"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="init-card__btn"
            @click="handleSetup"
          >
            初始化
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.init-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, var(--color-primary-600) 0%, #4f46e5 50%, #7c3aed 100%);
}

.init-card {
  width: 400px;
  padding: var(--space-2xl) var(--space-xl);
  background: var(--color-neutral-0);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.init-card__brand {
  text-align: center;
  margin-bottom: var(--space-md);
}

.init-card__logo {
  font-size: 24px;
  font-weight: 800;
  color: var(--color-primary-600);
  letter-spacing: -0.03em;
}

.init-card__title {
  text-align: center;
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-xs);
}

.init-card__desc {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  margin-bottom: var(--space-xl);
}

.init-card__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-xl) 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.init-card__form {
  width: 100%;
}

.init-card__btn {
  width: 100%;
}
</style>
