<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createUser,
  fetchRoleList,
  fetchUserDetail,
  resetUserPassword,
  updateUser,
} from '@/services/admin'
import type { RoleSimple } from '@/types'
import PasswordStrengthBar from '@/components/common/PasswordStrengthBar.vue'

const route = useRoute()
const router = useRouter()

const userId = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => !!userId.value)

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)

const form = reactive({
  username: '',
  name: '',
  email: '',
  password: '',
  roleIds: [] as string[],
})

const roleOptions = ref<RoleSimple[]>([])

function validatePassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!isEdit.value && !value) {
    callback(new Error('请输入密码'))
    return
  }
  if (!value) {
    callback()
    return
  }
  if (value.length < 8 || value.length > 64) {
    callback(new Error('密码长度为 8-64 个字符'))
    return
  }
  callback()
}

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 30, message: '用户名长度为 3-30 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线、连字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过 50 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: !isEdit.value, message: '请输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' },
  ],
}))

async function loadRoleOptions() {
  try {
    const list = await fetchRoleList('system')
    roleOptions.value = list.map((r) => ({
      id: r.id,
      name: r.name,
      type: 'system',
    }))
  } catch {
    // 角色选项加载失败不阻塞表单
  }
}

async function loadUser() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const user = await fetchUserDetail(userId.value)
    form.username = user.username
    form.name = user.name
    form.email = user.email
    form.roleIds = user.roles.map((r) => r.id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载用户信息失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUser(userId.value, { name: form.name, email: form.email, roleIds: form.roleIds })
    } else {
      await createUser({
        username: form.username,
        name: form.name,
        email: form.email,
        password: form.password,
        roleIds: form.roleIds,
      })
    }
    ElMessage.success('保存成功')
    router.push('/admin/users')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

const pwdDialogVisible = ref(false)
const newPassword = ref('')
const pwdSubmitting = ref(false)

async function submitChangePassword() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  pwdSubmitting.value = true
  try {
    await resetUserPassword(userId.value, newPassword.value)
    ElMessage.success('密码已修改')
    pwdDialogVisible.value = false
    newPassword.value = ''
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '修改密码失败')
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(() => {
  loadRoleOptions()
  loadUser()
})
</script>

<template>
  <div v-loading="loading" class="user-form">
    <div class="user-form__breadcrumb">
      <router-link to="/admin/users">用户管理</router-link>
      <el-icon :size="12"><ArrowRight /></el-icon>
      <span>{{ isEdit ? '编辑用户' : '新建用户' }}</span>
    </div>

    <div class="user-form__head">
      <div>
        <h1 class="user-form__title">{{ isEdit ? '编辑用户' : '新建用户' }}</h1>
        <p class="user-form__desc">
          {{ isEdit ? '更新账号信息与系统角色分配' : '创建平台账号并分配系统级角色' }}
        </p>
      </div>
      <div class="user-form__actions">
        <el-button link @click="router.push('/admin/users')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="user-form__form"
    >
      <div class="user-form__grid">
        <section class="user-form__card">
          <header class="user-form__card-head">
            <h3 class="user-form__card-title">基本信息</h3>
          </header>
          <div class="user-form__card-body">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号" />
              <div class="user-form__hint">3-30 个字符，允许字母、数字、_、-</div>
            </el-form-item>
            <el-form-item label="姓名" prop="name">
              <el-input
                v-model="form.name"
                placeholder="请输入姓名"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>

            <el-form-item v-if="!isEdit" label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="8-64 字符"
              />
              <PasswordStrengthBar :password="form.password" />
            </el-form-item>
            <el-form-item v-else label="密码">
              <el-button link type="primary" @click="pwdDialogVisible = true">修改密码</el-button>
            </el-form-item>
          </div>
        </section>

        <section class="user-form__card">
          <header class="user-form__card-head">
            <h3 class="user-form__card-title">
              系统角色<span class="user-form__card-subtitle">决定全局管理权限</span>
            </h3>
          </header>
          <div class="user-form__card-body">
            <el-checkbox-group v-model="form.roleIds" class="user-form__roles">
              <el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id">
                {{ role.name }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </section>
      </div>
    </el-form>

    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px">
      <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
      <PasswordStrengthBar :password="newPassword" />
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePassword">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* 面包屑对齐演示稿基准（xs 灰字、链接 hover 主色、分隔符浅灰） */
.user-form__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
}

.user-form__breadcrumb a {
  color: var(--color-neutral-500);
  text-decoration: none;
}

.user-form__breadcrumb a:hover {
  color: var(--color-primary-500);
}

.user-form__breadcrumb .el-icon {
  color: var(--color-neutral-400);
}

.user-form__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.user-form__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-neutral-900);
}

.user-form__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.user-form__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* 双列卡片对齐演示稿 card-grid--2，列间距同 block-gap(24px) */
.user-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-xl);
}

.user-form__card {
  min-width: 0;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.user-form__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.user-form__card-title {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-neutral-900);
}

.user-form__card-subtitle {
  margin-left: 8px;
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
}

/* 字段纵向间距对齐演示稿 .field(20px)；标签 13px/500 对齐 field__label */
.user-form__card-body {
  padding: 24px;
  --el-form-item-margin-bottom: 20px;
}

.user-form__card-body :deep(.el-form-item__label) {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-700);
  line-height: 1.4;
}

/* 宽度撑满，保证提示行换行到输入框下方（表单内容为 flex 布局） */
.user-form__hint {
  width: 100%;
  margin-top: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  line-height: 1.5;
}

/* 角色复选竖排，仅展示角色名（角色描述无数据源，见交互设计 4.3.1） */
.user-form__roles {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
</style>
