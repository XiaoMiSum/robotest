#!/bin/bash
# scripts/validate.sh — 质量验证全量编排（POSIX 薄封装，实现见 scripts/validate.mjs）
# 用法: bash scripts/validate.sh [--docs|--frontend|--backend|--all]   （默认 --all）
# Windows 无 bash 时等价: node scripts/validate.mjs <同参数>
# 各端 AGENT 提交前只运行对应端脚本，不运行本文件：
#   文档: bash scripts/validate-docs.sh    前端: bash scripts/validate-web.sh
#   后端: bash scripts/validate-backend.sh
# C7 提交格式在全量编排时统一只查一次；单端模式可用 VALIDATE_SKIP_COMMIT_CHECK=1 跳过。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v node >/dev/null 2>&1; then
  echo "✗ node 未安装（缺少依赖），无法运行质量验证"
  exit 1
fi

exec node "$SCRIPT_DIR/validate.mjs" "$@"
