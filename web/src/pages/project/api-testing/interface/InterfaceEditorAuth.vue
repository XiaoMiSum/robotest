<script setup lang="ts">
import type { InterfaceEditorForm } from '@/composables/project/api-testing/interface/interfacesModel'

defineProps<{ form: InterfaceEditorForm }>()
</script>

<template>
  <div class="interface-editor__auth">
    <el-form label-width="90px" @submit.prevent>
      <el-form-item label="认证方式">
        <el-select v-model="form.auth.type" data-test="editor-auth-type">
          <el-option label="No Auth" value="none" />
          <el-option label="Bearer Token" value="bearer" />
          <el-option label="API Key" value="apiKey" />
          <el-option label="Basic Auth" value="basic" />
          <el-option label="Digest Auth" value="digest" disabled />
        </el-select>
      </el-form-item>
      <template v-if="form.auth.type === 'bearer'">
        <el-form-item label="Token">
          <el-input v-model="form.auth.token" type="password" show-password placeholder="输入 Bearer Token" />
        </el-form-item>
        <p class="interface-editor__tip">执行时换算为 Authorization: Bearer 头；手工同名头优先</p>
      </template>
      <template v-else-if="form.auth.type === 'apiKey'">
        <el-form-item label="Key 名">
          <el-input v-model="form.auth.apiKeyName" placeholder="缺省为 X-API-Key" />
        </el-form-item>
        <el-form-item label="Key 值">
          <el-input v-model="form.auth.apiKeyValue" type="password" show-password />
        </el-form-item>
        <p class="interface-editor__tip">执行时换算为自定义请求头；手工同名头优先</p>
      </template>
      <template v-else-if="form.auth.type === 'basic'">
        <el-form-item label="用户名">
          <el-input v-model="form.auth.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.auth.password" type="password" show-password />
        </el-form-item>
        <p class="interface-editor__tip">执行时换算为 Authorization: Basic 头；手工同名头优先</p>
      </template>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.interface-editor__auth {
  max-width: 420px;
}

.interface-editor__tip {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--color-neutral-400, #909399);
}
</style>
