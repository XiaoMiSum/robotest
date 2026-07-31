<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { checkInitStatus } from '@/services/init'
import api from '@/services'
import type { Result, LoginResult } from '@/types'
import { ElMessage } from 'element-plus'
import BrandLogo from '@/components/common/BrandLogo.vue'

const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()

const currentYear = new Date().getFullYear()

const FEATURES = [
  { title: 'AI 智能用例生成', desc: '需求一键转用例，脑图智能编辑' },
  { title: '评审与计划协同', desc: '多人实时协作，评审进度一目了然' },
  { title: '缺陷全生命周期管理', desc: '智能查重分析，流转状态清晰可控' },
]

// 检查系统是否已初始化，未初始化则跳转到初始化页
onMounted(async () => {
  try {
    const status = await checkInitStatus()
    if (!status.initialized) {
      router.replace('/init')
    }
  } catch {
    // 网络错误：静默失败，不阻塞登录页展示
  }
})

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

    if (result.user.hasWorkspace) {
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
    <aside class="login-page__brand">
      <div class="login-page__logo">
        <BrandLogo :size="32" />
        <span class="login-page__logo-text">RoboTest</span>
      </div>

      <div class="login-page__hero">
        <span class="login-page__accent" />
        <h1 class="login-page__slogan">软件测试平台</h1>
        <p class="login-page__sub">
          覆盖用例管理、测试评审、测试计划与缺陷跟踪全流程，AI 深度赋能测试提效。
        </p>
        <ul class="login-page__features">
          <li v-for="item in FEATURES" :key="item.title" class="login-page__feature">
            <span class="login-page__feature-dot" />
            <div>
              <div class="login-page__feature-title">{{ item.title }}</div>
              <div class="login-page__feature-desc">{{ item.desc }}</div>
            </div>
          </li>
        </ul>
      </div>

      <div class="login-page__copyright">© {{ currentYear }} RoboTest</div>
    </aside>

    <main class="login-page__main">
      <div class="login-box">
        <div class="login-box__title">登录</div>
        <div class="login-box__subtitle">欢迎回来，请输入你的账号信息</div>
        <el-form
          :model="form"
          label-position="top"
          class="login-box__form"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号">
            <el-input
              v-model="form.identifier"
              placeholder="用户名 / 邮箱"
              size="large"
              prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
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
              class="login-box__btn"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="login-box__footer">首次部署？系统将自动引导完成初始化</div>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-neutral-0);
}

.login-page__brand {
  position: relative;
  width: 46%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  background: var(--color-neutral-900);
  overflow: hidden;

  // 细点阵纹理，向右下渐隐，避免大面积纯色的呆板感
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: radial-gradient(rgba(148, 163, 184, 0.14) 1px, transparent 1px);
    background-size: 26px 26px;
    mask-image: linear-gradient(160deg, #000 20%, transparent 70%);
  }
}

.login-page__logo {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}

.login-page__logo-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-neutral-0);
  letter-spacing: -0.01em;
}

.login-page__hero {
  position: relative;
}

.login-page__accent {
  display: block;
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--color-primary-500);
  margin-bottom: var(--space-xl);
}

.login-page__slogan {
  font-size: 32px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--color-neutral-100);
  margin-bottom: var(--space-lg);
}

.login-page__sub {
  font-size: var(--font-size-sm);
  line-height: 1.9;
  color: var(--color-neutral-400);
  max-width: 400px;
  margin-bottom: 48px;
}

.login-page__features {
  display: flex;
  flex-direction: column;
  gap: 20px;
  list-style: none;
  padding: 0;
  margin: 0;
}

.login-page__feature {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
}

.login-page__feature-dot {
  margin-top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-500);
  flex-shrink: 0;
}

.login-page__feature-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-200);
  margin-bottom: 3px;
}

.login-page__feature-desc {
  font-size: 13px;
  color: var(--color-neutral-500);
}

.login-page__copyright {
  position: relative;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-600);
}

.login-page__main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-box {
  width: 360px;
}

.login-box__title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-neutral-900);
  margin-bottom: var(--space-sm);
}

.login-box__subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  margin-bottom: var(--space-2xl);
}

.login-box__form {
  width: 100%;
}

.login-box__btn {
  width: 100%;
  margin-top: var(--space-sm);
  letter-spacing: 0.3em;
  text-indent: 0.3em;
}

.login-box__footer {
  margin-top: var(--space-xl);
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

// 窄屏下品牌区收起，退化为居中表单
@media (max-width: 960px) {
  .login-page__brand {
    display: none;
  }
}
</style>
