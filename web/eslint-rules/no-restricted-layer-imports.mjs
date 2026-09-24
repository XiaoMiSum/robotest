import path from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))
const sourceRoot = 'src'
const sourceLayers = new Set([
  'components',
  'composables',
  'pages',
  'router',
  'services',
  'stores',
  'types',
  'utils',
])

const restrictedTargets = {
  components: new Set(['pages', 'services']),
  composables: new Set(['components', 'pages']),
  services: new Set(['components', 'pages', 'composables', 'stores']),
  stores: new Set(['components', 'pages', 'composables']),
  types: new Set(['components', 'pages', 'composables', 'services', 'stores']),
  utils: new Set(['components', 'pages', 'composables', 'services', 'stores']),
}

function toPosixPath(filePath) {
  return filePath.replaceAll('\\', '/')
}

function getLayer(filePath) {
  const relativePath = toPosixPath(path.relative(projectRoot, path.resolve(filePath)))
  if (!relativePath.startsWith(`${sourceRoot}/`)) return undefined

  return relativePath.slice(sourceRoot.length + 1).split('/')[0]
}

function resolveLayer(importingFile, importSource) {
  const cleanSource = importSource.split(/[?#]/, 1)[0]
  const importingDirectory = toPosixPath(
    path.posix.dirname(toPosixPath(path.relative(projectRoot, importingFile))),
  )
  let targetPath

  if (cleanSource.startsWith('@/')) {
    targetPath = path.posix.join(sourceRoot, cleanSource.slice(2))
  } else if (cleanSource.startsWith('.')) {
    targetPath = path.posix.normalize(path.posix.join(importingDirectory, cleanSource))
  } else {
    return undefined
  }

  const relativePath = targetPath.startsWith(`${sourceRoot}/`)
    ? targetPath.slice(sourceRoot.length + 1)
    : undefined
  const targetLayer = relativePath?.split('/')[0]
  return sourceLayers.has(targetLayer) ? targetLayer : undefined
}

function isBaselineActive(entry, now) {
  const expiresAt = Date.parse(`${entry.expiresOn}T23:59:59.999Z`)
  return Number.isFinite(expiresAt) && expiresAt >= now.getTime()
}

function createBaselineKey(filePath, importSource) {
  return `${toPosixPath(filePath).replaceAll('\\', '/')}\0${importSource}`
}

const rule = {
  meta: {
    type: 'problem',
    docs: {
      description: '禁止前端层级依赖发生反向引用，并支持精确迁移基线',
    },
    schema: [
      {
        type: 'object',
        properties: {
          baseline: {
            type: 'array',
            items: {
              type: 'object',
              properties: {
                file: { type: 'string' },
                importSource: { type: 'string' },
                expiresOn: { type: 'string' },
              },
              required: ['file', 'importSource', 'expiresOn'],
              additionalProperties: true,
            },
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      restrictedLayerImport:
        '{{sourceLayer}} 不得依赖 {{targetLayer}} 层导入“{{importSource}}”；请遵守 docs/00-spec/10-engineering/01-frontend.md 第 3 节。',
    },
  },
  create(context) {
    const filename = context.filename
    const sourceLayer = getLayer(filename)
    const forbiddenTargets = sourceLayer ? restrictedTargets[sourceLayer] : undefined
    if (!sourceLayer || !forbiddenTargets) return {}

    const baselineEntries = context.options[0]?.baseline ?? []
    const now = new Date()
    const baseline = new Set(
      baselineEntries
        .filter((entry) => isBaselineActive(entry, now))
        .map((entry) =>
          createBaselineKey(path.resolve(projectRoot, entry.file), entry.importSource),
        ),
    )

    function checkImportSource(sourceNode) {
      if (typeof sourceNode.value !== 'string') return

      const importSource = sourceNode.value
      const targetLayer = resolveLayer(filename, importSource)
      if (!targetLayer || !forbiddenTargets.has(targetLayer)) return
      if (baseline.has(createBaselineKey(filename, importSource))) return

      context.report({
        node: sourceNode,
        messageId: 'restrictedLayerImport',
        data: { sourceLayer, targetLayer, importSource },
      })
    }

    return {
      ImportDeclaration(node) {
        checkImportSource(node.source)
      },
      ExportNamedDeclaration(node) {
        if (node.source) checkImportSource(node.source)
      },
      ExportAllDeclaration(node) {
        checkImportSource(node.source)
      },
      ImportExpression(node) {
        checkImportSource(node.source)
      },
    }
  },
}

export default rule
