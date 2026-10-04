import { describe, expect, it } from 'vitest'
import layoutSource from '@/layouts/AdminLayout.vue?raw'
import routerSource from '@/router/index.ts?raw'
import embeddingSource from './AiEmbeddingGroup.vue?raw'
import modelsSource from './AiModelsGroup.vue?raw'
import promptsSource from './AiPromptsGroup.vue?raw'
import shellSource from './AiSettingsPage.vue?raw'
import usageSource from './AiUsageGroup.vue?raw'

describe('AI 配置中心 · 壳层（交互 2.1）', () => {
  it('总开关关闭二次确认，未就绪与默认模型可用性由状态驱动', () => {
    expect(shellSource).toContain('关闭后业务端 AI 入口将隐藏，历史任务与产物保留')
    expect(shellSource).toContain('AI 总开关已关闭')
    expect(shellSource).toContain('请先添加并启用模型')
    expect(shellSource).toContain(
      ':disabled="store.settings === null || store.enabledModels.length === 0"',
    )
  })

  it('重建引导条覆盖进行中 / 待发起 / 失败三态并复用 aiTask 轮询收口', () => {
    expect(shellSource).toContain('向量重建进行中')
    expect(shellSource).toContain('向量维度已变更，需全量重建')
    expect(shellSource).toContain('向量重建失败：')
    expect(shellSource).toContain('发起重建')
    expect(shellSource).toContain('aiTaskStore.startPolling(store.reindexTaskId)')
    expect(shellSource).toContain('isTerminalTaskStatus(cached.status)')
    expect(shellSource).toContain("store.settleReindex(cached.error?.msg ?? '向量重建失败')")
  })

  it('设置加载失败由页面捕获并可重试，首载完成前不渲染分组（防表单闪空值）', () => {
    expect(shellSource).toContain('v-if="store.settingsError"')
    expect(shellSource).toContain('@click="store.loadSettings()"')
    expect(shellSource).toContain('v-if="shellReady"')
  })

  it('四分组导航与子路由渲染路由出口', () => {
    for (const label of ['模型配置', '向量 API', '场景提示词', '用量分析']) {
      expect(shellSource).toContain(label)
    }
    expect(shellSource).toContain('<router-view v-if="shellReady" />')
  })
})

describe('AI 配置中心 · 模型配置（交互 2.2）', () => {
  it('空态引导添加第一个模型，保存失败内联展示且保留输入', () => {
    expect(modelsSource).toContain('还没有模型配置，添加第一个模型')
    expect(modelsSource).toContain('保存失败')
    expect(modelsSource).toContain('formError.value = error instanceof Error && error.message')
  })

  it('编辑空密钥不入载荷，避免空串覆盖既有密钥', () => {
    expect(modelsSource).toContain('if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()')
    expect(modelsSource).toContain('已配置（输入新值以替换）')
  })

  it('默认模型不可停用，连通性测试失败不阻塞且刷新列表', () => {
    expect(modelsSource).toContain('该模型为默认模型，请先切换默认再停用')
    expect(modelsSource).toContain(':loading="testingId === row.id"')
    expect(modelsSource).toContain('void store.loadModels()')
    expect(modelsSource).toContain('连接成功（')
  })
})

describe('AI 配置中心 · 向量 API（交互 2.3 / 2.6）', () => {
  it('维度变更走危险确认，重建期间维度输入只读', () => {
    expect(embeddingSource).toContain('维度变更将使既有向量失效，需全量重建后方可继续检索')
    expect(embeddingSource).toContain('confirmButtonClass: \'el-button--danger\'')
    expect(embeddingSource).toContain(':disabled="dimensionsReadonly"')
    expect(embeddingSource).toContain('需全量重建完成前维度不可变更')
  })

  it('切换分组未保存编辑三选一拦截，保存失败留在当前分组', () => {
    expect(embeddingSource).toContain('onBeforeRouteLeave')
    expect(embeddingSource).toContain('保存后离开')
    expect(embeddingSource).toContain('放弃修改')
    expect(embeddingSource).toContain("return error !== 'close'")
    expect(embeddingSource).toContain('return await submit()')
  })

  it('首次保存密钥必填，已配置时空值不替换', () => {
    expect(embeddingSource).toContain('首次保存需填写密钥')
    expect(embeddingSource).toContain('if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()')
  })
})

describe('AI 配置中心 · 场景提示词（交互 2.4）', () => {
  it('恢复默认二次确认，内容非空校验内联报错', () => {
    expect(promptsSource).toContain('恢复后自定义内容将丢失')
    expect(promptsSource).toContain('提示词内容不能为空')
    expect(promptsSource).toContain('saveError.value =')
  })

  it('切换分组复用缓存，可用变量悬浮说明并点击插入占位', () => {
    expect(promptsSource).toContain('if (store.prompts.length === 0) await load()')
    expect(promptsSource).toContain('insertVariable(variable.name)')
    expect(promptsSource).toContain('${variable.desc}${variable.required ? \'（必填）\' : \'（可选）\'}')
  })
})

describe('AI 配置中心 · 用量分析（交互 2.5 / 2.6）', () => {
  it('范围预设与自定义、四指标页签、四分组维度齐备', () => {
    for (const label of ['近 7 天', '近 30 天', '自定义']) {
      expect(usageSource).toContain(label)
    }
    expect(usageSource).toContain('调用次数')
    expect(usageSource).toContain('Tokens')
    expect(usageSource).toContain('耗时')
    expect(usageSource).toContain('成本')
    for (const group of ['按天', '按模型', '按场景', '按调用类型']) {
      expect(usageSource).toContain(group)
    }
  })

  it('各分组数据点下钻均映射到接口筛选参数（含 callType）', () => {
    expect(usageSource).toContain('filters.from = item.key')
    expect(usageSource).toContain('filters.to = item.key')
    expect(usageSource).toContain('filters.modelId = item.key')
    expect(usageSource).toContain('filters.scene = item.key')
    expect(usageSource).toContain('filters.callType = item.key')
    // 无任务调用（taskId 为 null）不可跳转详情
    expect(usageSource).toContain('if (!row.taskId) return')
  })

  it('空区间给范围调整引导，echarts 按需注册', () => {
    expect(usageSource).toContain('所选范围暂无用量数据，可调整时间范围后重试')
    expect(usageSource).toContain('重置为近 30 天')
    expect(usageSource).toContain("import { BarChart, LineChart } from 'echarts/charts'")
    expect(usageSource).toContain("import { CanvasRenderer } from 'echarts/renderers'")
    expect(usageSource).toContain('请选择起止日期')
  })
})

describe('AI 配置中心 · 路由与侧栏高亮', () => {
  it('父路由挂菜单（AI 能力分组 / ai:admin），子路由叶子声明 mode，未知分组重定向默认组', () => {
    expect(routerSource).toContain("path: 'ai'")
    expect(routerSource).toContain("section: 'AI 能力'")
    expect(routerSource).toContain("permission: 'ai:admin'")
    expect(routerSource).toContain("{ path: ':pathMatch(.*)*', redirect: 'models' }")
    expect(routerSource).toContain("meta: { title: 'AI 配置 · 用量分析', mode: 'admin' }")
  })

  it('侧栏高亮取最长菜单路径前缀，子路由归属父菜单', () => {
    expect(layoutSource).toContain('current.startsWith(`${item.path}/`)')
    expect(layoutSource).toContain('item.path.length > matched.length')
  })
})
