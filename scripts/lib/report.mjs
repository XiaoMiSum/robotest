// scripts/lib/report.mjs — 验证输出与失败计数（供 validate.mjs 使用）
// 颜色仅在 TTY 下启用，避免重定向到文件/CI 日志时产生控制字符。

const ESC = String.fromCharCode(27);
const colored = Boolean(process.stdout.isTTY) && !process.env.NO_COLOR;

export const state = { failures: 0 };

export function paint(code, text) {
  return colored ? `${ESC}[${code}m${text}${ESC}[0m` : text;
}

export function pass(msg) {
  console.log(`${paint('0;32', '✓')} ${msg}`);
}

// 失败计入 state.failures，退出码由调用方按计数决定
export function fail(msg) {
  console.log(`${paint('0;31', '✗')} ${msg}`);
  state.failures += 1;
}

export function warn(msg) {
  console.log(`${paint('1;33', '⚠')} ${msg}`);
}

export function banner(title) {
  console.log('========================================');
  console.log(`  ${title}`);
  console.log('========================================');
}
