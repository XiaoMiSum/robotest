#!/usr/bin/env node
// scripts/validate.mjs — 质量验证编排器（Node 单一实现，跨平台）
// 用法: node scripts/validate.mjs [--docs|--frontend|--backend|--all]（默认 --all）
// scripts/validate.sh 与 scripts/validate-*.sh 是本文件的 POSIX 薄封装，两端行为一致：
//   文档: C7 提交格式 + 文档检查（check-docs.mjs）
//   前端: C7 + lint + typecheck + 测试/覆盖率（C8）+ any 检查（C1）+ 契约一致性
//   后端: C7 + mvn test
// 环境变量 VALIDATE_SKIP_COMMIT_CHECK=1 跳过单端模式的 C7 检查；--all 恒定只查一次。
// 缺依赖（node/pnpm/mvn/依赖目录）一律计为失败，不得静默跳过（QA-004）。

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { banner, fail, paint, pass, state, warn } from './lib/report.mjs';
import { checkCommitFormat } from './lib/commit-format.mjs';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const IS_WIN = process.platform === 'win32';

// Windows 下 pnpm/mvn/npx 是 .cmd 批处理，必须经 shell 解析；
// 经 shell 时含空格的参数需自带引号，否则会被拆分。
function run(cmd, args, { cwd, shell = false, stdio = 'inherit' } = {}) {
  const useShell = shell && IS_WIN;
  const finalArgs = useShell ? args.map((a) => (/[ "]/.test(a) ? `"${a}"` : a)) : args;
  const res = spawnSync(cmd, finalArgs, { cwd, shell: useShell, stdio, encoding: 'utf8' });
  return !res.error && res.status === 0;
}

function hasCommand(cmd) {
  if (IS_WIN) return run('where', [cmd], { shell: true, stdio: 'ignore' });
  return run('which', [cmd], { stdio: 'ignore' });
}

function exists(relPath) {
  return existsSync(path.resolve(process.cwd(), relPath));
}

// ─── 文档检查（GOV-008：链接、元信息、规则编号） ────────────────
function checkDocs() {
  console.log('');
  console.log('=== 文档检查 ===');
  // node 由本进程保证存在（POSIX 封装缺 node 时无法启动，等价于缺依赖）
  if (run(process.execPath, [path.join(SCRIPT_DIR, 'check-docs.mjs')])) {
    pass('文档链接、元信息与规则编号检查通过');
  } else {
    fail('文档检查失败（断链、旧锚点、元信息缺失或未登记规则）');
  }
}

// ─── 前端验证（lint / typecheck / 测试与覆盖率 C8） ─────────────
function checkFrontend() {
  console.log('');
  console.log('=== 前端验证 ===');

  // 缺依赖必须计为失败（QA-004），跳过会让门禁形同虚设
  if (!exists('web/node_modules')) {
    fail('web/node_modules 不存在（缺少依赖），请先执行 pnpm install');
    return;
  }
  if (!hasCommand('pnpm')) {
    fail('pnpm 未安装（缺少依赖）');
    return;
  }

  console.log('--- lint ---');
  if (run('pnpm', ['run', 'lint'], { cwd: 'web', shell: true })) {
    pass('lint 通过');
  } else {
    fail('lint 失败');
  }

  console.log('--- typecheck ---');
  if (run('pnpm', ['run', 'typecheck'], { cwd: 'web', shell: true })) {
    pass('typecheck 通过');
  } else {
    fail('typecheck 失败');
  }

  console.log('--- unit tests + coverage (C8) ---');
  // test:unit 已内联 --coverage，低于 70% 阈值即失败（QA-005）
  if (run('pnpm', ['run', 'test:unit'], { cwd: 'web', shell: true })) {
    pass('单元测试与覆盖率通过（C8 ≥ 70%）');
  } else {
    fail('单元测试或覆盖率阈值失败（C8）');
  }
}

// ─── Any 类型检查（前端 C1，等价 grep -r ': any\b|as any\b|<any>'） ──
function countAnyUsage(root) {
  let count = 0;
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (/\.(ts|vue)$/.test(entry.name) && !entry.name.endsWith('.d.ts')) {
        for (const line of readFileSync(full, 'utf8').split(/\r?\n/)) {
          if (/: any\b|as any\b|<any>/.test(line)) count += 1;
        }
      }
    }
  };
  walk(root);
  return count;
}

function checkAnyUsage() {
  console.log('');
  console.log('=== TypeScript any 使用检查 ===');

  if (!exists('web/src')) return;

  const count = countAnyUsage(path.resolve('web/src'));
  if (count > 0) {
    fail(`发现 ${count} 处 any 类型使用（C1 违规）`);
  } else {
    pass('无 any 类型使用');
  }
}

// ─── 契约一致性检查（07 §4.1 / 08 §4.2：openapi-typescript 生成类型漂移校验） ─
// 基线 web/openapi/contract.json 由后端运行期导出（pnpm contract:gen）。
// 校验：用基线重新生成类型并与提交的 types/generated/contract.d.ts 比对，漂移即失败。
// 基线缺失、依赖缺失、重新生成失败、类型漂移均计为失败（QA-008 转阻断）。
// 本检查只读，不修改任何源代码（lint 为 check-only，lint:fix 不被本脚本调用）。
function sameContent(a, b) {
  try {
    return readFileSync(a, 'utf8') === readFileSync(b, 'utf8');
  } catch {
    return false;
  }
}

function checkContract() {
  console.log('');
  console.log('=== 前端契约一致性 ===');
  if (!exists('web/openapi/contract.json')) {
    fail('缺少 OpenAPI 基线 web/openapi/contract.json（QA-008 阻断），请运行后端后执行 pnpm contract:gen');
    return;
  }
  // 依赖缺失计为失败（QA-004）：跳过会让契约门禁形同虚设
  if (!exists('web/node_modules') || !exists('web/node_modules/openapi-typescript')) {
    fail('openapi-typescript 未安装（缺少依赖），请先执行 pnpm install');
    return;
  }

  const tmp = path.join(os.tmpdir(), 'contract.check.gen.d.ts');
  // 重新生成失败属契约失败，必须计为失败（QA-004），不得静默跳过
  if (!run('npx', ['openapi-typescript', 'openapi/contract.json', '-o', tmp], { cwd: 'web', shell: true, stdio: 'ignore' })) {
    fail('契约类型重新生成失败（契约校验失败），请检查 openapi/contract.json 与 openapi-typescript');
  } else if (sameContent(path.resolve('web/src/types/generated/contract.d.ts'), tmp)) {
    pass('契约类型与基线一致');
  } else {
    fail('契约漂移：src/types/generated/contract.d.ts 与 openapi/contract.json 不一致，请运行 pnpm contract:gen');
  }
  rmSync(tmp, { force: true });
}

// ─── 后端验证（mvn test） ───────────────────────────────────────
function checkBackend() {
  console.log('');
  console.log('=== 后端验证 ===');

  if (!exists('server')) {
    warn('server/ 目录不存在，跳过后端验证');
    return;
  }
  // 缺依赖必须计为失败（QA-004）
  if (!hasCommand('mvn')) {
    fail('mvn 未安装（缺少依赖）');
    return;
  }

  console.log('--- tests ---');
  if (run('mvn', ['test', '-q'], { cwd: 'server', shell: true })) {
    pass('单元测试通过');
  } else {
    fail('单元测试失败');
  }
}

const CHECKS = {
  docs: { title: 'RoboTest 质量验证 — 文档端', run: checkDocs },
  frontend: {
    title: 'RoboTest 质量验证 — 前端（web）',
    // 与 POSIX 封装一致：前端 = 基础检查 + any 扫描 + 契约一致性
    run: () => {
      checkFrontend();
      checkAnyUsage();
      checkContract();
    },
  },
  backend: { title: 'RoboTest 质量验证 — 后端（server）', run: checkBackend },
};

// 运行单项检查并返回是否通过（按执行前后的失败计数差判定）
function runCheck(check) {
  const before = state.failures;
  check.run();
  return state.failures === before;
}

function exitWith(code) {
  console.log('');
  console.log('========================================');
  if (code === 0) {
    console.log(paint('0;32', '验证完成: 全部通过'));
  } else {
    console.log(paint('0;31', `验证完成: ${state.failures} 项失败`));
  }
  process.exit(code);
}

const MODES = {
  '--docs': 'docs',
  '--frontend': 'frontend',
  '-f': 'frontend',
  '--backend': 'backend',
  '-b': 'backend',
  '--all': 'all',
  '-a': 'all',
};

const mode = process.argv[2] ?? '--all';
if (!MODES[mode]) {
  console.log('用法: node scripts/validate.mjs [--docs|--frontend|--backend|--all]');
  process.exit(1);
}

const skipCommitCheck = process.env.VALIDATE_SKIP_COMMIT_CHECK === '1';

if (MODES[mode] === 'all') {
  banner('  RoboTest 质量验证 — 全量编排');
  // 全量编排统一只查一次 C7（不接受跳过）
  checkCommitFormat();
  const commitFailures = state.failures;

  let subFailures = 0;
  for (const key of ['docs', 'frontend', 'backend']) {
    // 与 POSIX 编排一致：每个端子脚本各自打印标题头
    banner(`  ${CHECKS[key].title}`);
    if (!runCheck(CHECKS[key])) subFailures += 1;
  }

  const total = commitFailures + subFailures;
  console.log('');
  console.log('========================================');
  if (total > 0) {
    console.log(
      paint('0;31', `验证完成: ${total} 项失败（提交格式/检查 ${commitFailures} 项，未通过端脚本 ${subFailures} 个）`),
    );
    process.exit(1);
  }
  console.log(paint('0;32', '验证完成: 全部通过'));
  process.exit(0);
}

const check = CHECKS[MODES[mode]];
banner(`  ${check.title}`);
// C7 检查先于子检查执行，需单独快照失败数，否则其失败会被 runCheck 的前后对比吞掉
const failuresBeforeCommitCheck = state.failures;
if (!skipCommitCheck) checkCommitFormat();
const commitOk = state.failures === failuresBeforeCommitCheck;
const ok = runCheck(check);
exitWith(ok && commitOk ? 0 : 1);
