<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { checkInitStatus, setupInit } from '@/services/init'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const form = reactive({
  password: '',
  confirmPassword: '',
})
const loading = ref(false)
const checking = ref(true)

// 与提交校验同源的实时达标提示，帮助用户在输入过程中自查
const hints = computed(() => [
  { label: '至少 8 个字符', ok: form.password.length >= 8 },
  { label: '包含字母', ok: /[a-zA-Z]/.test(form.password) },
  { label: '包含数字与符号更安全', ok: /\d/.test(form.password) && /[^a-zA-Z0-9]/.test(form.password) },
])

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
        <BrandLogo :size="34" />
        <span class="init-card__logo-text">RoboTest</span>
      </div>

      <div class="init-card__desc">首次部署，请为系统管理员设置登录密码</div>

      <div v-if="checking" class="init-card__loading">
        <el-icon class="is-loading" :size="24"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" fill="none" stroke-dasharray="31.4 31.4" stroke-linecap="round"/></svg></el-icon>
        <span>检查系统状态...</span>
      </div>

      <el-form
        v-else
        :model="form"
        label-position="top"
        class="init-card__form"
        @submit.prevent="handleSetup"
      >
        <el-form-item label="登录名">
          <el-input model-value="admin" size="large" prefix-icon="User" disabled />
        </el-form-item>
        <el-form-item label="管理员密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（至少 8 位）"
            size="large"
            prefix-icon="Lock"
            show-password
          />
          <PasswordStrengthBar :password="form.password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleSetup"
          />
        </el-form-item>

        <div class="init-card__hints">
          <span
            v-for="hint in hints"
            :key="hint.label"
            class="init-card__hint"
            :class="{ 'init-card__hint--ok': hint.ok }"
          >
            {{ hint.ok ? '✓' : '○' }} {{ hint.label }}
          </span>
        </div>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="init-card__btn"
            @click="handleSetup"
          >
            完成初始化
          </el-button>
        </el-form-item>
      </el-form>

      <div class="init-card__footer">初始化完成后将跳转至登录页</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.init-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: var(--color-neutral-50);
  overflow: hidden;

  // 细点阵纹理，中心向四周渐隐，弱化空旷感
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: radial-gradient(rgba(100, 116, 139, 0.12) 1px, transparent 1px);
    background-size: 26px 26px;
    mask-image: radial-gradient(ellipse at 50% 35%, #000 30%, transparent 75%);
  }
}

.init-card {
  position: relative;
  width: 440px;
  padding: 40px 40px 32px;
  background: var(--color-neutral-0);
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-neutral-200);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06), 0 2px 6px rgba(15, 23, 42, 0.03);
}

.init-card__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: var(--space-xl);
}

.init-card__logo-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-neutral-900);
  letter-spacing: -0.01em;
}

.init-card__desc {
  text-align: center;
  font-size: 13px;
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

.init-card__hints {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 10px 12px;
  margin-bottom: var(--space-lg);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
}

.init-card__hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-neutral-400);
}

.init-card__hint--ok {
  color: #16a34a;
}

.init-card__btn {
  width: 100%;
  letter-spacing: 0.15em;
  text-indent: 0.15em;
}

.init-card__footer {
  text-align: center;
  font-size: 12px;
  color: var(--color-neutral-400);
  margin-top: var(--space-sm);
}
</style>
