<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { checkEmail, joinByInvitation, verifyInvitation } from '@/services/workspace'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()

const token = (route.query.token as string) || ''

// 步骤状态：verifying → email → password(已有用户) / create(新用户)
type Step = 'verifying' | 'email' | 'password' | 'create'
const step = ref<Step>('verifying')
const valid = ref(false)
const workspaceName = ref('')
const errorMsg = ref('')

const email = ref('')
const name = ref('')
const password = ref('')
const submitting = ref(false)

function validatePassword(value: string): string | null {
  if (!value) return '请输入密码'
  if (value.length < 8 || value.length > 64) return '密码长度为 8-64 个字符'
  return null
}

// 步骤1: 验证邀请链接
async function verify() {
  if (!token) {
    errorMsg.value = '邀请链接无效：缺少令牌参数'
    step.value = 'verifying'
    return
  }
  try {
    const result = await verifyInvitation(token)
    valid.value = result.valid
    workspaceName.value = result.workspaceName
    if (!result.valid) {
      errorMsg.value = '邀请链接已失效或不存在'
      step.value = 'verifying'
    } else {
      step.value = 'email'
    }
  } catch (err) {
    valid.value = false
    errorMsg.value = err instanceof Error ? err.message : '验证失败'
    step.value = 'verifying'
  }
}

// 步骤2: 输入邮箱后查询用户是否存在
async function handleCheckEmail() {
  if (!email.value) {
    ElMessage.warning('请输入邮箱')
    return
  }
  submitting.value = true
  try {
    const result = await checkEmail(token, email.value)
    step.value = result.exists ? 'password' : 'create'
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '查询失败')
  } finally {
    submitting.value = false
  }
}

// 步骤3a: 已有用户 → 密码验证 → 加入
async function handleLoginJoin() {
  const pwdErr = validatePassword(password.value)
  if (pwdErr) {
    ElMessage.warning(pwdErr)
    return
  }
  submitting.value = true
  try {
    const result = await joinByInvitation({ token, email: email.value, password: password.value })
    await loginAndRedirect(result.accessToken, result.refreshToken, result)
    ElMessage.success('已成功加入工作空间')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加入失败')
  } finally {
    submitting.value = false
  }
}

// 步骤3b: 新用户 → 创建账号 → 加入
async function handleCreateJoin() {
  if (!name.value.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  const pwdErr = validatePassword(password.value)
  if (pwdErr) {
    ElMessage.warning(pwdErr)
    return
  }
  submitting.value = true
  try {
    const result = await joinByInvitation({ token, email: email.value, password: password.value, name: name.value.trim() })
    await loginAndRedirect(result.accessToken, result.refreshToken, result)
    ElMessage.success('欢迎加入！已自动创建账号并登录。')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加入失败')
  } finally {
    submitting.value = false
  }
}

async function loginAndRedirect(accessToken: string, refreshToken: string, result: { user: { id: string; username: string; email: string }; activeWorkspace: { id: string; name: string; workspaceRole: string } }) {
  // setLogin 内部已持久化令牌，此处不重复 setTokens
  authStore.setLogin(
    accessToken,
    refreshToken,
    { id: result.user.id, username: result.user.username, email: result.user.email, status: 'active', roles: [], permissions: [], hasWorkspace: result.activeWorkspace != null },
    { id: result.activeWorkspace.id, name: result.activeWorkspace.name, workspaceRole: result.activeWorkspace.workspaceRole },
  )
  await authStore.loadPermissions()
  navStore.setMode('workspace')
  router.push('/workspace/projects')
}

function goBackToEmail() {
  password.value = ''
  name.value = ''
  step.value = 'email'
}

onMounted(verify)
</script>

<template>
  <div class="join-page">
    <div class="join-page__card">

      <!-- 加载中 / 无效链接 -->
      <div v-if="step === 'verifying'" class="join-page__loading">
        <template v-if="!valid">
          <div class="join-page__icon join-page__icon--danger">
            <el-icon :size="40"><CircleCloseFilled /></el-icon>
          </div>
          <div class="join-page__heading">邀请链接无效</div>
          <p class="join-page__hint">{{ errorMsg }}</p>
          <el-button @click="router.push('/login')">返回登录</el-button>
        </template>
        <template v-else>
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>正在验证邀请链接...</p>
        </template>
      </div>

      <!-- 步骤1: 输入邮箱 -->
      <div v-else-if="step === 'email'" class="join-page__form">
        <div class="join-page__icon join-page__icon--primary">
          <el-icon :size="40"><Link /></el-icon>
        </div>
        <div class="join-page__heading">加入工作空间</div>
        <p class="join-page__hint">您将被加入「{{ workspaceName }}」工作空间</p>

        <el-form label-position="top" class="join-page__el-form" @submit.prevent="handleCheckEmail">
          <el-form-item label="邮箱">
            <el-input v-model="email" type="email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" class="join-page__submit" @click="handleCheckEmail">
              下一步
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤2a: 已有用户 → 输入密码 -->
      <div v-else-if="step === 'password'" class="join-page__form">
        <div class="join-page__icon join-page__icon--primary">
          <el-icon :size="40"><Lock /></el-icon>
        </div>
        <div class="join-page__heading">验证密码</div>
        <p class="join-page__hint">检测到您已有账号，请输入密码验证</p>

        <el-form label-position="top" class="join-page__el-form" @submit.prevent="handleLoginJoin">
          <el-form-item label="邮箱">
            <el-input :model-value="email" disabled />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" show-password placeholder="8-64 字符" />
            <PasswordStrengthBar :password="password" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" class="join-page__submit" @click="handleLoginJoin">
              加入并登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="join-page__back">
          <el-button link type="primary" @click="goBackToEmail">← 返回</el-button>
        </div>
      </div>

      <!-- 步骤2b: 新用户 → 创建账号 -->
      <div v-else-if="step === 'create'" class="join-page__form">
        <div class="join-page__icon join-page__icon--primary">
          <el-icon :size="40"><UserFilled /></el-icon>
        </div>
        <div class="join-page__heading">创建账号</div>
        <p class="join-page__hint">未检测到账号，需创建新账号加入</p>

        <el-form label-position="top" class="join-page__el-form" @submit.prevent="handleCreateJoin">
          <el-form-item label="邮箱">
            <el-input :model-value="email" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="name" placeholder="请输入姓名" maxlength="50" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" show-password placeholder="8-64 字符" />
            <PasswordStrengthBar :password="password" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" class="join-page__submit" @click="handleCreateJoin">
              创建并加入
            </el-button>
          </el-form-item>
        </el-form>

        <div class="join-page__back">
          <el-button link type="primary" @click="goBackToEmail">← 返回</el-button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped lang="scss">
.join-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, var(--color-primary-50) 0%, var(--color-neutral-100) 50%, #eef2ff 100%);
}

.join-page__card {
  width: 420px;
  padding: var(--space-2xl) var(--space-xl);
  background: var(--color-neutral-0);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-neutral-200);
}

.join-page__loading,
.join-page__form {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.join-page__icon {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-md);
}

.join-page__icon--primary {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.join-page__icon--danger {
  background: var(--color-danger-50);
  color: var(--color-danger-500);
}

.join-page__heading {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--color-neutral-800);
  margin: 0 0 var(--space-sm);
}

.join-page__hint {
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-xl);
  font-size: var(--font-size-sm);
}

.join-page__loading p {
  color: var(--color-neutral-500);
  margin: var(--space-md) 0 0;
}

.join-page__el-form {
  width: 100%;
}

.join-page__submit {
  width: 100%;
}

.join-page__back {
  margin-top: var(--space-md);
}
</style>
