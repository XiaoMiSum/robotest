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

/** 密码强度（0-4级，映射为 弱/中/强） */
const passwordStrength = computed(() => {
  const val = password.value
  if (!val || val.length < 8) return 0
  const kinds = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(val)).length
  return kinds // 1=弱, 2=弱, 3=中, 4=强
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
  if (s <= 2) return 'var(--el-color-danger)'
  if (s === 3) return 'var(--el-color-warning)'
  return 'var(--el-color-success)'
})

/** 密码强度校验 */
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
      { id: result.user.id, username: result.user.username, email: result.user.email, status: 'active', roles: [] },
      { id: result.activeWorkspace.id, name: result.activeWorkspace.name, workspaceRole: result.activeWorkspace.workspaceRole },
    )
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
      <!-- 验证中 -->
      <div v-if="verifying" class="join-page__loading">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在验证邀请链接...</p>
      </div>

      <!-- 验证失败 -->
      <div v-else-if="!valid" class="join-page__error">
        <el-icon :size="48" color="var(--el-color-danger)"><CircleCloseFilled /></el-icon>
        <h2>邀请链接无效</h2>
        <p>{{ errorMsg }}</p>
        <el-button @click="router.push('/login')">返回登录</el-button>
      </div>

      <!-- 验证通过 -->
      <div v-else class="join-page__form">
        <el-icon :size="48" color="var(--el-color-primary)"><Link /></el-icon>
        <h2>加入工作空间</h2>
        <p class="join-page__hint">您将被加入「{{ workspaceName }}」工作空间</p>

        <el-form @submit.prevent="handleSubmit" label-position="top" style="width: 100%">
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
            <el-button type="primary" :loading="submitting" style="width: 100%" @click="handleSubmit">
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
  background-color: var(--el-fill-color-lighter);
}

.join-page__card {
  width: 420px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.join-page__loading,
.join-page__error,
.join-page__form {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.join-page__loading p,
.join-page__error p {
  color: var(--el-text-color-secondary);
  margin: 12px 0 0;
}

.join-page__error h2 {
  margin: 16px 0 8px;
}

.join-page__form h2 {
  margin: 16px 0 8px;
}

.join-page__hint {
  color: var(--el-text-color-secondary);
  margin: 0 0 24px;
}

.join-page__tips {
  margin-top: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}

.join-page__tips p {
  margin: 0;
}

.pwd-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  width: 100%;
}

.pwd-strength__bar {
  flex: 1;
  height: 4px;
  background-color: var(--el-border-color-lighter);
  border-radius: 2px;
  overflow: hidden;
}

.pwd-strength__fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.2s, background-color 0.2s;
}

.pwd-strength__label {
  font-size: 12px;
  white-space: nowrap;
}
</style>
