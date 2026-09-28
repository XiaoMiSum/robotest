#!/bin/bash
# scripts/validate-backend.sh — 后端（server）质量验证（POSIX 薄封装，实现见 scripts/validate.mjs）
# 用法: bash scripts/validate-backend.sh
# Windows 无 bash 时等价: node scripts/validate.mjs --backend
# 内容: C7 提交格式 + mvn test

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v node >/dev/null 2>&1; then
  echo "✗ node 未安装（缺少依赖），无法运行质量验证"
  exit 1
fi

exec node "$SCRIPT_DIR/validate.mjs" --backend
