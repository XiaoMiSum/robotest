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

const route = useRoute()
const router = useRouter()

const userId = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => !!userId.value)

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)

const form = reactive({
  username: '',
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
  const kinds = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(value)).length
  if (kinds < 3) {
    callback(new Error('需包含大写、小写、数字、特殊字符中至少三种'))
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
    const tree = await fetchRoleList()
    const systemGroup = tree.find((node) => node.type === 'system')
    roleOptions.value = (systemGroup?.children ?? []).map((r) => ({
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
      await updateUser(userId.value, { email: form.email, roleIds: form.roleIds })
    } else {
      await createUser({
        username: form.username,
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
  <div class="user-form">
    <el-page-header class="user-form__header" @back="router.push('/admin/users')">
      <template #content>
        <span class="user-form__title">{{ isEdit ? '编辑用户' : '新建用户' }}</span>
      </template>
    </el-page-header>

    <el-card v-loading="loading" shadow="never" class="user-form__card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
        class="user-form__el-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="isEdit"
            placeholder="3-30 个字符，字母/数字/_/-"
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
            placeholder="8-64 字符，至少三种字符类型"
          />
        </el-form-item>
        <el-form-item v-else label="密码">
          <el-button link type="primary" @click="pwdDialogVisible = true">修改密码</el-button>
        </el-form-item>

        <el-form-item label="系统角色">
          <el-select
            v-model="form.roleIds"
            multiple
            filterable
            clearable
            placeholder="可为空，可多选"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
          <el-button @click="router.push('/admin/users')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px">
      <p class="user-form__pwd-tip">
        新密码需 8-64 字符，且包含大写、小写、数字、特殊字符中至少三种。
      </p>
      <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.user-form__header {
  margin-bottom: var(--space-xl);
}

.user-form__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.user-form__el-form {
  max-width: 560px;
}

.user-form__pwd-tip {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-md);
  line-height: 1.6;
}
</style>
