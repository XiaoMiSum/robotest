<script setup lang="ts">
import { useGitLabRepo } from '@/composables/project/api-testing/gitlab/useGitLabRepo'

const {
  canEdit,
  loading,
  loadFailed,
  repo,
  creating,
  activeTab,
  form,
  testing,
  testOk,
  testMessage,
  connDetailVisible,
  saving,
  isPlatformMode,
  connDown,
  load,
  startCreate,
  setImportMode,
  toggleConnDetail,
  save,
  runConnectionTest,
  handleDelete,
} = useGitLabRepo()

const PLATFORM_MODE_HINT =
  '平台执行：完整解析步骤与 YAML 并合并环境，导入后场景在平台内可编辑、可直接执行；无需改造仓库 CI。'
const CI_MODE_HINT =
  '流水线执行：仅登记用例清单，平台侧只读不可编辑；执行与用例维护一律在仓库侧，需按约定改造 CI。'
</script>

<template>
  <div class="gl-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">GitLab 集成</h1>
        <p class="page-head__desc">
          维护本项目与 GitLab 仓库的绑定，并将 Maven 测试工程中的测试方法导入为接口测试场景。执行不在本页发生，由场景侧执行入口承载
        </p>
      </div>
    </header>

    <!-- 加载失败（UI-PAGE-11）：页面内错误块 + 重试 -->
    <div v-if="loadFailed" class="gl-card">
      <div class="gl-state">
        <span class="gl-state__icon gl-state__icon--danger"><el-icon :size="40"><WarningFilled /></el-icon></span>
        <div class="gl-state__title">仓库配置加载失败</div>
        <p class="gl-state__desc">请检查网络后重试</p>
        <el-button size="small" @click="load()">重试</el-button>
      </div>
    </div>

    <div v-else-if="loading" class="gl-card">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 空态（交互稿 27 §4）：整页居中引导 + 接入前置清单 -->
    <div v-else-if="!repo && !creating" class="gl-card">
      <div class="gl-state">
        <span class="gl-state__icon"><el-icon :size="40"><Connection /></el-icon></span>
        <div class="gl-state__title">尚未配置 GitLab 仓库</div>
        <p class="gl-state__desc">连接 GitLab 上的 Maven 测试工程，把其中的测试方法导入为接口测试场景</p>
        <el-button v-if="canEdit" type="primary" @click="startCreate">立即配置</el-button>
        <ul class="gl-reqs">
          <li><el-icon><CircleCheckFilled /></el-icon><span>测试工程为 Maven + TestNG + Ryze 结构</span></li>
          <li>
            <el-icon><CircleCheckFilled /></el-icon>
            <span>测试方法<b>同时</b>标注 <code class="mono">@Test</code> 与 <code class="mono">@RyzeTest</code></span>
          </li>
          <li><el-icon><CircleCheckFilled /></el-icon><span>访问令牌具备仓库读取与 API 访问权限</span></li>
          <li>
            <el-icon><CircleCheckFilled /></el-icon>
            <span>流水线执行另需按约定改造仓库内 CI 配置（取清单、回报结果、上传结果制品）</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- 配置态 / 首次配置态：整页单面板，无列表、无新增入口（交互稿 27 §1） -->
    <div v-else class="gl-card">
      <div class="gl-bar">
        <el-tabs v-model="activeTab" class="gl-bar__tabs">
          <el-tab-pane label="基础配置" name="basic" />
          <el-tab-pane label="导入" name="import" />
        </el-tabs>

        <!-- 连通性徽标：失败转告警色并可点击展开错误详情（交互稿 27 §1） -->
        <button
          v-if="!creating"
          type="button"
          class="gl-badge"
          :class="{ 'gl-badge--down': connDown }"
          @click="toggleConnDetail"
        >
          连通性
          <span class="gl-dot" :class="connDown ? 'gl-dot--down' : 'gl-dot--ok'" />
          {{ connDown ? '不可达' : '已连通' }}
        </button>

        <!-- 删除不与保存平级：移入 [⋯] 更多菜单（交互稿 27 §1） -->
        <el-dropdown v-if="canEdit && !creating" trigger="click" @command="handleDelete">
          <el-button class="gl-more" circle size="small" title="更多操作">
            <el-icon><MoreFilled /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="delete" class="gl-danger-item">删除配置</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-alert
        v-if="connDown && connDetailVisible"
        class="gl-conn-detail"
        type="warning"
        :closable="false"
        show-icon
        :title="testMessage"
      />

      <!-- ============ 基础配置 ============ -->
      <div v-show="activeTab === 'basic'" class="gl-panel">
        <el-alert
          v-if="!canEdit"
          class="gl-readonly"
          type="info"
          :closable="false"
          title="当前账号缺少 api-env:edit 权限，本页只读；连接测试与配置维护操作不可用"
        />

        <form class="gl-form" @submit.prevent="save">
          <div class="gl-grid">
            <div class="gl-field">
              <label class="gl-field__label">名称<span class="gl-field__req">*</span></label>
              <el-input v-model="form.name" :disabled="!canEdit" maxlength="200" placeholder="如：订单接口自动化" />
              <div class="gl-field__hint">≤ 200 字符</div>
            </div>
            <div class="gl-field">
              <label class="gl-field__label">GitLab 地址<span class="gl-field__req">*</span></label>
              <el-input v-model="form.gitlabBaseUrl" :disabled="!canEdit" placeholder="https://gitlab.example.com" />
              <div class="gl-field__hint">仅 http/https，结尾不留 / · 支持自建实例</div>
            </div>
            <div class="gl-field">
              <label class="gl-field__label">访问令牌<span v-if="creating" class="gl-field__req">*</span></label>
              <el-input
                v-model="form.token"
                type="password"
                show-password
                :disabled="!canEdit"
                :placeholder="creating ? '需 api scope 与仓库读取权限' : '仅写不读，留空表示不修改'"
              />
              <div class="gl-field__hint">
                需 <code class="mono">api</code> scope 与仓库读取权限；仅写不读，不回显、不落本地存储
              </div>
            </div>
            <div class="gl-field">
              <label class="gl-field__label">测试源码目录</label>
              <el-input v-model="form.testSourceDir" :disabled="!canEdit" placeholder="src/test/java" />
              <div class="gl-field__hint">缺省 src/test/java</div>
            </div>
            <div class="gl-field">
              <label class="gl-field__label">资源目录</label>
              <el-input v-model="form.resourceDir" :disabled="!canEdit" placeholder="src/test/resources" />
              <div class="gl-field__hint">缺省 src/test/resources</div>
            </div>
            <div class="gl-field">
              <label class="gl-field__label">环境声明文件路径</label>
              <el-input
                v-model="form.envFilePath"
                :disabled="!canEdit || !isPlatformMode"
                placeholder="common/env/env.yaml"
              />
              <div class="gl-field__hint">
                {{ isPlatformMode ? '缺省 common/env/env.yaml' : '流水线执行不读取环境声明' }}
              </div>
            </div>
          </div>

          <!-- 重复导入覆盖：流水线执行下不生效，不渲染（交互稿 27 §3.1） -->
          <div v-if="isPlatformMode" class="gl-switch-field">
            <el-switch v-model="form.reimportOverwrite" :disabled="!canEdit" />
            <div class="gl-switch-field__text">
              <span class="gl-switch-field__label">重复导入覆盖</span>
              <span class="gl-field__hint">默认关；开启后再次导入将用仓库内容覆盖平台侧场景内容</span>
            </div>
          </div>

          <!-- 创建后锁定：只读文本呈现，不渲染为禁用输入框（交互稿 27 §3.1） -->
          <div v-if="!creating && repo" class="gl-locked">
            <div class="gl-locked__head">
              <span class="gl-locked__title">创建后锁定</span>
              <span class="gl-locked__note">变更须删除后重建</span>
            </div>
            <div class="gl-locked__grid">
              <div>
                <div class="gl-locked__k">项目路径</div>
                <div class="gl-locked__v mono">{{ repo.repoPath }}</div>
              </div>
              <div>
                <div class="gl-locked__k">默认分支</div>
                <div class="gl-locked__v mono">{{ repo.defaultBranch }}</div>
              </div>
              <div>
                <div class="gl-locked__k">接入方式</div>
                <div class="gl-locked__v">
                  <span>{{ isPlatformMode ? '平台执行' : '流水线执行' }}</span>
                  <code class="mono gl-locked__tag">{{ form.importMode }}</code>
                </div>
              </div>
            </div>
            <div class="gl-mode-hint">{{ isPlatformMode ? PLATFORM_MODE_HINT : CI_MODE_HINT }}</div>
          </div>

          <!-- 首次配置：三个锁定字段在创建时才可填 -->
          <div v-if="creating">
            <div class="gl-grid">
              <div class="gl-field">
                <label class="gl-field__label">项目路径<span class="gl-field__req">*</span></label>
                <el-input v-model="form.repoPath" :disabled="!canEdit" placeholder="group/subgroup/repo" />
                <div class="gl-field__hint">不含 .git 后缀与协议前缀</div>
              </div>
              <div class="gl-field">
                <label class="gl-field__label">默认分支</label>
                <el-input v-model="form.defaultBranch" :disabled="!canEdit" placeholder="master" />
                <div class="gl-field__hint">缺省 master，连通性测试通过后回显实际分支名</div>
              </div>
            </div>

            <!-- 该选择创建后锁定，差异说明随选项就地呈现，不依赖隐藏在只读区的提示 -->
            <div class="gl-field">
              <label class="gl-field__label">接入方式<span class="gl-field__req">*</span></label>
              <div class="gl-modes">
                <button
                  type="button"
                  class="gl-mode"
                  :class="{ 'is-active': form.importMode === 'platform' }"
                  :disabled="!canEdit"
                  @click="setImportMode('platform')"
                >
                  <span class="gl-mode__head">
                    <span class="gl-mode__name">平台执行</span>
                    <code class="gl-mode__tag mono">platform</code>
                  </span>
                  <span class="gl-mode__desc">
                    完整解析步骤与 YAML 并合并环境，场景在平台内可编辑、可直接执行；无需改造仓库 CI
                  </span>
                </button>
                <button
                  type="button"
                  class="gl-mode"
                  :class="{ 'is-active': form.importMode === 'gitlab_ci' }"
                  :disabled="!canEdit"
                  @click="setImportMode('gitlab_ci')"
                >
                  <span class="gl-mode__head">
                    <span class="gl-mode__name">流水线执行</span>
                    <code class="gl-mode__tag mono">gitlab_ci</code>
                  </span>
                  <span class="gl-mode__desc">
                    仅登记用例清单，平台侧只读不可编辑；执行与用例维护一律在仓库侧，需按约定改造 CI
                  </span>
                </button>
              </div>
              <div class="gl-field__hint">创建后不可修改，变更绑定仓库或接入方式须先删除再重建</div>
            </div>
          </div>

          <!-- 只读态整行不渲染，避免留下无按钮的分隔线（交互稿 27 §4） -->
          <div v-if="canEdit" class="gl-actions">
            <el-button :loading="testing" @click="runConnectionTest">连通性测试</el-button>
            <span v-if="testOk !== null" class="gl-result" :class="testOk ? 'gl-result--ok' : 'gl-result--fail'">
              <template v-if="testOk">
                <el-icon><CircleCheckFilled /></el-icon>
                <span>{{ testMessage }}</span>
              </template>
              <template v-else>
                <el-icon><WarningFilled /></el-icon>
                <span class="mono">{{ testMessage }}</span>
              </template>
            </span>
            <el-button type="primary" :loading="saving" class="gl-actions__save" @click="save">保存</el-button>
          </div>
        </form>
      </div>

      <!-- ============ 导入（占位，随 WP-9.2 交付） ============ -->
      <div v-show="activeTab === 'import'" class="gl-panel">
        <div class="gl-state gl-state--panel">
          <span class="gl-state__icon"><el-icon :size="40"><UploadFilled /></el-icon></span>
          <div class="gl-state__title">导入功能待交付</div>
          <p class="gl-state__desc">扫描预览、执行导入与失败明细将随工作包 WP-9.2 交付后开放</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.gl-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

/* 整页单面板：一项目一仓库，无列表与新增入口（交互稿 27 §1） */
.gl-card {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-lg);
  padding: 0 var(--space-lg) var(--space-lg);
}

/* 页签栏：sticky，右侧常驻连通性徽标与 [⋯] 更多菜单（交互稿 27 §1） */
.gl-bar {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin: 0 calc(0px - var(--space-lg));
  padding: 0 var(--space-lg);
  background: var(--color-neutral-0);
  border-bottom: 1px solid var(--color-neutral-100);
}

.gl-bar__tabs {
  flex: 1;
  min-width: 0;

  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__nav-wrap)::after {
    display: none;
  }

  :deep(.el-tabs__item) {
    height: 46px;
    line-height: 46px;
    font-size: var(--font-size-sm);
    color: var(--color-neutral-500);
  }

  :deep(.el-tabs__item.is-active) {
    color: var(--color-primary-500);
    font-weight: 600;
  }

  :deep(.el-tabs__active-bar) {
    background-color: var(--color-primary-500);
  }
}

.gl-more {
  flex-shrink: 0;
}

.gl-danger-item {
  color: var(--color-danger-strong);

  &:hover {
    background: var(--color-danger-light);
    color: var(--color-danger-strong);
  }
}

/* 连通性徽标：失败转告警色，点击展开错误详情 */
.gl-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 5px 10px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  border: 1px solid var(--color-neutral-100);
  border-radius: 999px;
  background: none;
  cursor: default;
}

.gl-badge--down {
  color: var(--color-warning-strong);
  border-color: var(--color-warning-border);
  background: var(--color-warning-light);
  cursor: pointer;
}

.gl-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-neutral-300);
}

.gl-dot--ok {
  background: var(--color-success);
}

.gl-dot--down {
  background: var(--color-warning);
}

.gl-conn-detail {
  margin-top: var(--space-lg);
}

.gl-panel {
  padding-top: var(--space-lg);
}

.gl-readonly {
  margin-bottom: var(--space-md);
}

/* 窄窗口两列，宽窗口三列 */
.gl-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 var(--space-md);
}

@media (min-width: 1100px) {
  .gl-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.gl-field {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-md);
}

.gl-field__label {
  margin-bottom: 6px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-700);
}

.gl-field__req {
  margin-left: 2px;
  color: var(--color-danger);
}

.gl-field__hint {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  line-height: 1.6;
}

.mono {
  font-family: var(--font-mono);
  font-size: 0.95em;
}

.gl-switch-field {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.gl-switch-field__text {
  display: flex;
  flex-direction: column;
}

.gl-switch-field__label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-700);
}

/* 创建后锁定字段：只读文本，不渲染为禁用输入框（交互稿 27 §3.1） */
.gl-locked {
  border: 1px solid var(--color-neutral-100);
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
}

.gl-locked__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: 10px;
}

.gl-locked__title {
  flex: 1;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.gl-locked__note {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gl-locked__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-sm) var(--space-md);
}

.gl-locked__k {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gl-locked__v {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--color-neutral-800);
  word-break: break-all;
}

.gl-locked__tag {
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: var(--color-neutral-500);
}

.gl-mode-hint {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-neutral-200);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  line-height: 1.6;
}

/* 接入方式：两选项并列成卡片，差异说明就地可见（交互稿 27 §3.1） */
.gl-modes {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-sm);
}

@media (max-width: 700px) {
  .gl-modes {
    grid-template-columns: 1fr;
  }
}

.gl-mode {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  width: 100%;
  padding: 12px 14px;
  text-align: left;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);

  &:hover:not(:disabled) {
    border-color: var(--color-primary-300);
    background: var(--color-primary-50);
  }

  &.is-active {
    border-color: var(--color-primary-500);
    background: var(--color-primary-50);
    box-shadow: inset 0 0 0 1px var(--color-primary-500);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.gl-mode__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
}

.gl-mode__name {
  flex: 1;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.gl-mode.is-active .gl-mode__name {
  color: var(--color-primary-600);
}

.gl-mode__tag {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gl-mode.is-active .gl-mode__tag {
  color: var(--color-primary-500);
}

.gl-mode__desc {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-500);
  line-height: 1.6;
}

/* 动作行：[连通性测试] 靠左，[保存] 固定右端（交互稿 27 §3.1） */
.gl-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding-top: var(--space-md);
  margin-top: var(--space-sm);
  border-top: 1px solid var(--color-neutral-100);
}

.gl-actions__save {
  margin-left: auto;
}

.gl-result {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
}

.gl-result--ok {
  color: var(--color-success-strong);
}

.gl-result--fail {
  color: var(--color-danger-strong);
}

/* 空态 / 错误态 / 占位态统一居中引导 */
.gl-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-2xl) 0;
}

.gl-state--panel {
  padding: var(--space-xl) 0;
}

.gl-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: var(--space-lg);
  border-radius: var(--radius-xl);
  background: var(--color-neutral-100);
  color: var(--color-neutral-400);
}

.gl-state__icon--danger {
  background: var(--color-danger-light);
  color: var(--color-danger-strong);
}

.gl-state__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-700);
}

.gl-state__desc {
  max-width: 480px;
  margin: var(--space-sm) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
  line-height: 1.6;
}

.gl-reqs {
  max-width: 440px;
  margin: var(--space-lg) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 9px;
  text-align: left;
}

.gl-reqs li {
  display: flex;
  gap: 8px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  line-height: 1.6;

  .el-icon {
    flex-shrink: 0;
    margin-top: 3px;
    color: var(--color-success);
  }
}
</style>
