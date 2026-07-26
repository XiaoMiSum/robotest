<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { joinByInvitation, verifyInvitation } from '@/services/workspace'
import { setTokens } from '@/services'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()

const token = (route.query.token as string) || ''

const verifying = ref(true)
const valid = ref(false)
const workspaceName = ref('')
const errorMsg = ref('')

const email = ref('')
const password = ref('')
const submitting = ref(false)

const passwordStrength = computed(() => {
  const val = password.value
  if (!val || val.length < 8) return 0
  const kinds = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(val)).length
  return kinds
})
const strengthLabel = computed(() => {
  const s = passwordStrength.value
  if (s <= 0) return ''
  if (s <= 2) return '弱'
  if (s === 3) return '中'
  return '强'
})
const strengthColor = computed(() => {
  const s = passwordStrength.value
  if (s <= 2) return 'var(--color-danger-500)'
  if (s === 3) return 'var(--color-warning-500)'
  return 'var(--color-success-500)'
})

function validatePassword(value: string): string | null {
  if (!value) return '请输入密码'
  if (value.length < 8 || value.length > 64) return '密码长度为 8-64 个字符'
  const kinds = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(value)).length
  if (kinds < 3) return '需包含大写、小写、数字、特殊字符中至少三种'
  return null
}

async function verify() {
  if (!token) {
    valid.value = false
    errorMsg.value = '邀请链接无效：缺少令牌参数'
    verifying.value = false
    return
  }
  try {
    const result = await verifyInvitation(token)
    valid.value = result.valid
    workspaceName.value = result.workspaceName
    if (!result.valid) {
      errorMsg.value = '邀请链接已失效或不存在'
    }
  } catch (err) {
    valid.value = false
    errorMsg.value = err instanceof Error ? err.message : '验证失败'
  } finally {
    verifying.value = false
  }
}

async function handleSubmit() {
  if (!email.value) {
    ElMessage.warning('请输入邮箱')
    return
  }
  const pwdErr = validatePassword(password.value)
  if (pwdErr) {
    ElMessage.warning(pwdErr)
    return
  }
  submitting.value = true
  try {
    const result = await joinByInvitation({ token, email: email.value, password: password.value })
    setTokens(result.accessToken, result.refreshToken)
    authStore.setLogin(
      result.accessToken,
      result.refreshToken,
      { id: result.user.id, username: result.user.username, email: result.user.email, status: 'active', roles: [], permissions: [] },
      { id: result.activeWorkspace.id, name: result.activeWorkspace.name, workspaceRole: result.activeWorkspace.workspaceRole },
    )
    await authStore.loadPermissions()
    navStore.setMode('workspace')
    if (result.isNewUser) {
      ElMessage.success('欢迎加入！已自动创建账号并登录。')
    } else {
      ElMessage.success('已成功加入工作空间')
    }
    router.push('/workspace/projects')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加入失败')
  } finally {
    submitting.value = false
  }
}

onMounted(verify)
</script>

<template>
  <div class="join-page">
    <div class="join-page__card">
      <div v-if="verifying" class="join-page__loading">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在验证邀请链接...</p>
      </div>

      <div v-else-if="!valid" class="join-page__error">
        <div class="join-page__icon join-page__icon--danger">
          <el-icon :size="40"><CircleCloseFilled /></el-icon>
        </div>
        <div class="join-page__heading">邀请链接无效</div>
        <p class="join-page__hint">{{ errorMsg }}</p>
        <el-button @click="router.push('/login')">返回登录</el-button>
      </div>

      <div v-else class="join-page__form">
        <div class="join-page__icon join-page__icon--primary">
          <el-icon :size="40"><Link /></el-icon>
        </div>
        <div class="join-page__heading">加入工作空间</div>
        <p class="join-page__hint">您将被加入「{{ workspaceName }}」工作空间</p>

        <el-form label-position="top" class="join-page__el-form" @submit.prevent="handleSubmit">
          <el-form-item label="邮箱">
            <el-input v-model="email" type="email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" type="password" show-password placeholder="8-64 字符，至少三种字符类型" />
            <div v-if="password" class="pwd-strength">
              <div class="pwd-strength__bar">
                <div
                  class="pwd-strength__fill"
                  :style="{ width: `${passwordStrength * 25}%`, backgroundColor: strengthColor }"
                />
              </div>
              <span class="pwd-strength__label" :style="{ color: strengthColor }">{{ strengthLabel }}</span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" class="join-page__submit" @click="handleSubmit">
              加入并登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="join-page__tips">
          <p>已有账号？输入密码验证后直接加入</p>
          <p>没有账号？将自动创建并加入</p>
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
.join-page__error,
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

.join-page__tips {
  margin-top: var(--space-lg);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  line-height: 1.8;
}

.join-page__tips p {
  margin: 0;
}

.pwd-strength {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: 6px;
  width: 100%;
}

.pwd-strength__bar {
  flex: 1;
  height: 4px;
  background-color: var(--color-neutral-200);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.pwd-strength__fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: all 0.3s ease;
}

.pwd-strength__label {
  font-size: var(--font-size-2xs);
  font-weight: 500;
  white-space: nowrap;
}
</style>
