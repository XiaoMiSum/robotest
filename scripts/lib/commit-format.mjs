// scripts/lib/commit-format.mjs — C7 提交格式检查（唯一实现，供 validate.mjs 调用）
// C7 格式: <emoji> <type>(<scope>): <description>
// 校验: emoji 必填且与 type 匹配（02-workflow §2.1 映射表）、type 取自该表、
// scope 为小写标识（§2.2 关键字或业务域，可带 / 子路径）、description 非占位文案。
// 合并提交由托管平台生成，不适用 C7（git log --no-merges），跳过。

import { spawnSync } from 'node:child_process';
import { fail, pass, warn } from './report.mjs';

// type 对应的标准 emoji（02-workflow §2.1）
const EMOJI = new Map([
  ['feat', '✨'],
  ['fix', '🐛'],
  ['refactor', '♻'],
  ['style', '💄'],
  ['docs', '📝'],
  ['test', '✅'],
  ['chore', '🔧'],
  ['perf', '⚡'],
  ['deps', '⬆'],
  ['security', '🔒'],
]);

// 去掉变体选择符 U+FE0F，使 ♻ 与 ♻️、⚡ 与 ⚡️ 等价比较
const stripVs16 = (s) => s.replace(/\uFE0F/g, '');

// 校验单条提交标题；通过返回 null，失败返回原因
export function validateCommitMsg(msg) {
  const matched = /^(\S+)\s+([a-z]+)\(([^)]+)\):\s*(.+)$/.exec(msg);
  if (!matched) {
    // 无 emoji 的提交单独归因，便于按 C7 修正
    if (/^([a-z]+)\(([^)]+)\):\s*(.+)$/.test(msg)) {
      return '缺少 emoji 前缀（C7: <emoji> <type>(<scope>): <description>）';
    }
    return '不符合 C7 格式 <emoji> <type>(<scope>): <description>';
  }

  const [, emoji, type, scope, desc] = matched;
  const expect = EMOJI.get(type);
  if (expect === undefined) {
    return `未知 type: ${type}（允许: ${[...EMOJI.keys()].join('|')}）`;
  }
  if (stripVs16(emoji) !== stripVs16(expect)) {
    return `emoji 与 type 不匹配: ${type} 应为 ${expect}，实际为 ${emoji}`;
  }
  if (!/^[a-z][a-z0-9-]*(\/[a-z][a-z0-9-]*)*$/.test(scope)) {
    return `scope 非法: ${scope}（小写标识，可带 / 子路径）`;
  }
  const lower = desc.toLowerCase();
  if (/^(wip|tmp|temp|fixup|squash|misc|xxx+)([\s\p{P}].*)?$|^commit all pending.*$/u.test(lower)) {
    return `占位/临时描述不符合 C7: ${desc}`;
  }
  return null;
}

// 检查工作区是否干净（有未提交变更时告警，不计失败）
function hasUncommittedChanges() {
  const res = spawnSync('git', ['diff', '--quiet', 'HEAD'], { stdio: 'ignore' });
  return !res.error && res.status !== 0;
}

function recentSubjects(count) {
  const res = spawnSync('git', ['log', '--no-merges', '--format=%s', '-n', String(count)], {
    encoding: 'utf8',
  });
  return (res.stdout ?? '').split(/\r?\n/).filter((line) => line.trim() !== '');
}

export function checkCommitFormat() {
  console.log('');
  console.log('=== Git 提交格式检查 ===');

  if (hasUncommittedChanges()) {
    warn('存在未提交的变更，请先 commit 再运行验证');
  }

  const N = 5;
  let bad = 0;
  for (const msg of recentSubjects(N)) {
    const err = validateCommitMsg(msg);
    if (err !== null) {
      fail(`提交格式错误: ${msg}（${err}）`);
      bad += 1;
    }
  }

  if (bad === 0) {
    pass(`最近 ${N} 条提交格式正确`);
  }
}
