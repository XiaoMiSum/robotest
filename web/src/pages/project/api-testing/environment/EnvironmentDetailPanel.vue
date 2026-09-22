<script setup lang="ts">
import { useEnvironmentDetailState } from '@/composables/project/api-testing/environment/useEnvironmentDetailState'
import KeyValueTable from '@/pages/project/api-testing/debug/KeyValueTable.vue'
import ExtractorAssetPicker from '@/components/project/api-testing/ExtractorAssetPicker.vue'
import EnvironmentProcessorPane from './EnvironmentProcessorPane.vue'
import { DRIVER_OPTIONS } from './environmentsModel'

const props = defineProps<{ environmentId: string; canEdit: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const {
  loading, loadError, detail, saving, configForms, dsForms, variableRows, activeTab,
  activeConfigId, activeConfig, orderedConfigForms, selectConfig, addHttpConfig, removeHttpConfig, testingHttpId, runHttpTest,
  activeDsId, activeDs, orderedDsForms, selectDs, selectedDsDriverOption, handleDsDriverChange, addDataSource, removeDataSource, testingDsId, runDsTest,
  activeProcId, selectedProcessor, preProcCount, postProcCount,
  procList, selectProcessor, addProcessor, removeProcessor,
  moveProcessor, copyProcessor, procTestclass, procHttpRefOptions, procDsRefOptions,
  procHttpRef, procDsRef, procTags, procDisplayName,
  variableCount, load, saveAll,
  extractorPickerVisible, extractorPickerLoading, extractorPickerItems, extractorPickerKeyword,
  openExtractorPicker, handleExtractorPicked, loadExtractorAssets,
  procAssetPickerVisible, procAssetPickerLoading, procAssetPickerItems, procAssetPickerKeyword,
  openProcessorAssetPicker, handleProcessorAssetPicked, loadProcAssets,
} = useEnvironmentDetailState(props, emit)
</script>

<template>
  <div v-loading="loading" class="env-detail">
    <div v-if="loadError" class="env-detail__empty">
      <p>环境详情加载失败</p>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else-if="detail">
      <div class="env-detail__head">
        <span class="env-detail__name">{{ detail.name }}</span>
        <el-button v-if="canEdit" type="primary" :loading="saving" @click="saveAll">保存全部</el-button>
      </div>
      <el-tabs v-model="activeTab" class="env-detail__tabs">
        <!-- ============ HTTP 默认配置 ============ -->
        <el-tab-pane :label="`HTTP (${configForms.length})`" name="http">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in orderedConfigForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeConfigId }"
                @click="selectConfig(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="env-detail__config-add">
                <el-button link type="primary" @click="addHttpConfig"><el-icon><Plus /></el-icon>新增配置</el-button>
              </li>
            </ul>

            <div v-if="activeConfig" class="env-detail__config-form">
              <el-form label-width="110px" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeConfig.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeConfig.refName" placeholder="场景中通过该名引用此配置" />
                </el-form-item>
                <el-form-item label="Base URL" required>
                  <el-input v-model="activeConfig.baseUrl" placeholder="https://api.example.com" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeConfig.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认 HTTP 配置</span>
                </el-form-item>
              </el-form>

              <div class="env-detail__headers">
                <div class="env-detail__section-title">请求头</div>
                <KeyValueTable v-model:entries="activeConfig.headers" placeholder-key="Header" :disabled="!canEdit" />
              </div>

              <div class="env-detail__config-footer">
                <el-button :loading="testingHttpId === activeConfig.id" @click="runHttpTest(activeConfig, props.environmentId)">
                  连接测试
                </el-button>
                <el-button v-if="canEdit" type="danger" plain @click="removeHttpConfig(activeConfig)">
                  删除配置
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- ============ 全局变量 ============ -->
        <el-tab-pane :label="`变量 (${variableCount})`" name="variables">
          <KeyValueTable
            v-model:entries="variableRows"
            placeholder-key="变量名"
            show-description
            :show-enabled="false"
            :disabled="!canEdit"
          />
          <p class="env-detail__syntax-tip">
            引用语法：<code>${变量名}</code>，如 <code>${BASE_URL}</code>
          </p>
        </el-tab-pane>

        <!-- ============ 数据源（交互同 HTTP：左列表 + 右内联表单） ============ -->
        <el-tab-pane :label="`数据源 (${dsForms.length})`" name="datasources">
          <div class="env-detail__split">
            <ul class="env-detail__config-list">
              <li
                v-for="form in orderedDsForms"
                :key="form.id"
                :class="{ 'is-active': form.id === activeDsId }"
                @click="selectDs(form)"
              >
                {{ form.name || '(未命名)' }}
              </li>
              <li v-if="canEdit" class="env-detail__config-add">
                <el-button link type="primary" @click="addDataSource"><el-icon><Plus /></el-icon>新增数据源</el-button>
              </li>
            </ul>

            <div v-if="activeDs" class="env-detail__config-form">
              <el-form label-width="110px" :disabled="!canEdit">
                <el-form-item label="名称" required>
                  <el-input v-model="activeDs.name" maxlength="100" />
                </el-form-item>
                <el-form-item label="引用名" required>
                  <el-input v-model="activeDs.refName" placeholder="场景中通过该名引用此数据源" />
                </el-form-item>
                <el-form-item label="驱动" required>
                  <el-select v-model="activeDs.driver" @change="handleDsDriverChange">
                    <el-option
                      v-for="option in DRIVER_OPTIONS"
                      :key="option.label"
                      :label="option.label"
                      :value="option.driver"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="URL" required>
                  <el-input
                    v-model="activeDs.url"
                    type="textarea"
                    :rows="2"
                    :placeholder="selectedDsDriverOption?.urlExample"
                  />
                  <span class="env-detail__hint">用户名/密码通过 URL 设置</span>
                </el-form-item>
                <el-form-item label="连接池上限">
                  <el-input-number v-model="activeDs.maxPoolSize" :min="1" :max="100" />
                </el-form-item>
                <el-form-item label="设为默认">
                  <el-switch v-model="activeDs.isDefault" />
                  <span class="env-detail__hint">同一环境内至多一个默认数据源</span>
                </el-form-item>
              </el-form>

              <div class="env-detail__config-footer">
                <el-button :loading="testingDsId === activeDs.id" @click="runDsTest(activeDs, props.environmentId)">
                  连接测试
                </el-button>
                <el-button v-if="canEdit" type="danger" plain @click="removeDataSource(activeDs)">
                  删除数据源
                </el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ 前置处理器 ============ -->
        <el-tab-pane :label="`前置处理器 (${preProcCount})`" name="preprocessors">
          <EnvironmentProcessorPane
            processor-type="preprocessor"
            title="前置处理器"
            empty-description="暂无前置处理器"
            :count="preProcCount"
            :can-edit="canEdit"
            :processors="procList('preprocessor')"
            :active-proc-id="activeProcId"
            :selected-processor="selectedProcessor"
            :proc-testclass="procTestclass"
            :proc-http-ref="procHttpRef"
            :proc-ds-ref="procDsRef"
            :proc-http-ref-options="procHttpRefOptions"
            :proc-ds-ref-options="procDsRefOptions"
            :config-forms="configForms"
            :ds-forms="dsForms"
            :proc-tags="procTags"
            :proc-display-name="procDisplayName"
            @select="selectProcessor"
            @add="addProcessor('preprocessor')"
            @remove="removeProcessor"
            @move="(i, d) => moveProcessor('preprocessor', i, d)"
            @copy="copyProcessor"
            @import="openProcessorAssetPicker('preprocessor')"
            @import-extractors="openExtractorPicker"
          />
        </el-tab-pane>

        <!-- ============ 后置处理器 ============ -->
        <el-tab-pane :label="`后置处理器 (${postProcCount})`" name="postprocessors">
          <EnvironmentProcessorPane
            processor-type="postprocessor"
            title="后置处理器"
            empty-description="暂无后置处理器"
            :count="postProcCount"
            :can-edit="canEdit"
            :processors="procList('postprocessor')"
            :active-proc-id="activeProcId"
            :selected-processor="selectedProcessor"
            :proc-testclass="procTestclass"
            :proc-http-ref="procHttpRef"
            :proc-ds-ref="procDsRef"
            :proc-http-ref-options="procHttpRefOptions"
            :proc-ds-ref-options="procDsRefOptions"
            :config-forms="configForms"
            :ds-forms="dsForms"
            :proc-tags="procTags"
            :proc-display-name="procDisplayName"
            @select="selectProcessor"
            @add="addProcessor('postprocessor')"
            @remove="removeProcessor"
            @move="(i, d) => moveProcessor('postprocessor', i, d)"
            @copy="copyProcessor"
            @import="openProcessorAssetPicker('postprocessor')"
            @import-extractors="openExtractorPicker"
          />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 从公共组件引入处理器 -->
    <ExtractorAssetPicker
      v-model="procAssetPickerVisible"
      :loading="procAssetPickerLoading"
      :items="procAssetPickerItems"
      :keyword="procAssetPickerKeyword"
      title="从公共组件引入处理器"
      tip="仅展示启用的处理器资产；引入为复制，得到独立副本，与源资产无关联。"
      empty-text="暂无可用处理器"
      search-placeholder="搜索处理器名称..."
      @update:keyword="procAssetPickerKeyword = $event"
      @search="loadProcAssets"
      @confirm="handleProcessorAssetPicked"
    />

    <!-- 从公共组件引入提取器 -->
    <ExtractorAssetPicker
      v-model="extractorPickerVisible"
      :loading="extractorPickerLoading"
      :items="extractorPickerItems"
      :keyword="extractorPickerKeyword"
      @update:keyword="extractorPickerKeyword = $event"
      @search="loadExtractorAssets"
      @confirm="handleExtractorPicked"
    />
  </div>
</template>

<style scoped lang="scss">
.env-detail {
  background: var(--color-neutral-0, #fff);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 0 var(--space-lg) var(--space-md);
  min-height: 320px;
}

.env-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) 0 var(--space-sm);
}

.env-detail__name {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.env-detail__tabs {
  margin-top: 0;

  :deep(.el-tabs__header) {
    margin-bottom: var(--space-sm);
  }

  :deep(.el-tabs__content) {
    overflow: visible;
  }
}

.env-detail__split {
  display: flex;
  gap: var(--space-lg);
}

.env-detail__config-list {
  list-style: none;
  margin: 0;
  padding: var(--space-sm);
  width: 180px;
  flex-shrink: 0;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 2px;

  li {
    position: relative;
    padding: 6px var(--space-sm);
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: var(--font-size-sm);
    display: flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--color-neutral-600);

    &:hover {
      background: var(--color-primary-50);
    }

    &.is-active {
      background: var(--color-primary-50);
      color: var(--color-primary-600);
      font-weight: 500;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 60%;
        border-radius: var(--radius-sm);
        background: var(--color-primary-500);
      }
    }
  }

  li.env-detail__config-add {
    margin-top: var(--space-xs);
    border: 1px dashed var(--color-neutral-300);
    justify-content: center;
    color: var(--color-primary-500);

    &:hover {
      border-color: var(--color-primary-500);
      background: var(--color-primary-50);
    }
  }
}

.env-detail__config-form {
  flex: 1;
  min-width: 0;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.env-detail__hint {
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.env-detail__headers {
  margin-top: var(--space-md);
}

.env-detail__section-title {
  font-size: var(--font-size-sm);
  font-weight: 500;
  margin-bottom: var(--space-sm);
}

.env-detail__config-footer {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-neutral-100);
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
  justify-content: flex-end;

  .el-button + .el-button {
    margin-left: 0;
  }
}

.env-detail__empty {
  text-align: center;
  padding: var(--space-xl) 0;
  color: var(--color-neutral-400);

  p {
    margin-bottom: var(--space-sm);
  }
}

.env-detail__syntax-tip {
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);

  code {
    font-family: var(--font-family-mono, monospace);
    background: var(--color-neutral-50);
    padding: 0 4px;
    border-radius: var(--radius-sm, 3px);
  }
}
</style>
