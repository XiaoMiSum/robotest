#!/bin/bash
# scripts/validate-web.sh — 前端（web）质量验证（POSIX 薄封装，实现见 scripts/validate.mjs）
# 用法: bash scripts/validate-web.sh
# Windows 无 bash 时等价: node scripts/validate.mjs --frontend
# 内容: C7 + lint + typecheck + 测试/覆盖率（C8）+ any 检查（C1）+ 契约一致性

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v node >/dev/null 2>&1; then
  echo "✗ node 未安装（缺少依赖），无法运行质量验证"
  exit 1
fi

exec node "$SCRIPT_DIR/validate.mjs" --frontend
