#!/usr/bin/env node
// scripts/check-docs.mjs — 文档链接、元信息与规则编号检查（GOV-008，依赖 DOC-008）
// 由 scripts/validate-docs.sh 调用，validate.sh --all 编排同一入口后即可发现断链、旧锚点与未登记规则。
//
// 限制（避免误报，按设计跳过）：
//   1. 外链（http/https/mailto 等）不联网校验，仅校验本地相对链接与锚点；
//   2. 反引号路径仅识别 docs/、scripts/、.github/ 前缀与 *.md 文件——web/、server/ 下的
//      生成物或运行期产物（如 web/openapi/contract.json）不作为断链依据；
//   3. 规则编号仅识别 C<数字> 与 UI-<域>-<数字>：SEC-/DB-/API- 等前缀与 backlog 任务编号
//      同形，暂不纳入登记校验。

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rel = (p) => p.slice(ROOT.length + 1);

const failures = [];
const fail = (file, line, msg) => failures.push({ file: rel(file), line, msg });

let linkCount = 0;
let externalCount = 0;
let metaCount = 0;
let ruleRefCount = 0;

function walkMd(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    const p = join(dir, e.name);
    if (e.isDirectory()) walkMd(p, out);
    else if (e.isFile() && e.name.endsWith('.md')) out.push(p);
  }
  return out;
}

// GitHub 风格 slug：小写，保留字母/数字/空格/下划线/连字符（CJK 属 \p{L}），其余标点删除，空格转连字符
function slugify(heading) {
  return heading
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/ /g, '-');
}

const parsedCache = new Map();

// 解析单个 md：标题 slug（含重复消歧）、非代码区链接、行内代码路径候选
function parseMd(file) {
  const cached = parsedCache.get(file);
  if (cached) return cached;

  const lines = readFileSync(file, 'utf8').split(/\r?\n/);
  const headings = new Map();
  const links = [];
  const inlineTokens = [];
  const slugSeen = new Map();
  let inFence = null;

  lines.forEach((raw, i) => {
    const fence = raw.match(/^\s*(`{3,}|~{3,})/);
    if (fence) {
      const marker = fence[1][0];
      if (!inFence) inFence = marker;
      else if (inFence === marker) inFence = null;
      return;
    }
    for (const m of raw.matchAll(/`([^`\n]+)`/g)) {
      inlineTokens.push({ line: i + 1, token: m[1] });
    }
    if (inFence) return;

    const noCode = raw.replace(/`[^`]*`/g, ' ');
    const h = noCode.match(/^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$/);
    if (h) {
      const base = slugify(h[1]);
      const n = slugSeen.get(base) ?? 0;
      slugSeen.set(base, n + 1);
      headings.set(n === 0 ? base : `${base}-${n}`, true);
    }
    for (const m of noCode.matchAll(/!?\[[^\]]*\]\(\s*<?([^)<\s]+)>?(?:\s+"[^"]*")?\s*\)/g)) {
      links.push({ line: i + 1, target: m[1] });
    }
  });

  const parsed = { headings, links, inlineTokens };
  parsedCache.set(file, parsed);
  return parsed;
}

function decodeAnchor(a) {
  try {
    return decodeURIComponent(a);
  } catch {
    return a;
  }
}

function checkLinks(file, parsed) {
  for (const { line, target } of parsed.links) {
    if (/^[a-z][a-z0-9+.-]*:/i.test(target)) {
      externalCount += 1;
      continue;
    }
    linkCount += 1;
    let path = target;
    let anchor = '';
    const hash = path.indexOf('#');
    if (hash >= 0) {
      anchor = decodeAnchor(path.slice(hash + 1));
      path = path.slice(0, hash);
    }
    let targetFile;
    if (path === '') targetFile = file;
    else if (path.startsWith('/')) targetFile = join(ROOT, path);
    else targetFile = resolve(dirname(file), path);

    if (!existsSync(targetFile)) {
      fail(file, line, `断链: ${target}（目标不存在）`);
      continue;
    }
    if (anchor && targetFile.endsWith('.md')) {
      const tp = parseMd(targetFile);
      if (!tp.headings.has(anchor)) {
        fail(file, line, `旧锚点: ${target}（目标文件无对应标题）`);
      }
    }
  }
}

// 行内反引号路径：仅 docs/、scripts/、.github/ 前缀与 *.md 才做存在性校验
const LOOKS_LIKE_PATH = /^[A-Za-z0-9._\-/#]+$/;

function checkInlinePaths(file, parsed) {
  for (const { line, token } of parsed.inlineTokens) {
    const bare = token.split('#')[0];
    if (!bare || !LOOKS_LIKE_PATH.test(bare)) continue;
    let check = null;
    if (/^(docs|scripts|\.github)\//.test(bare)) check = join(ROOT, bare);
    // 裸文件名（无目录段）多为表格首列简写或命名约定提及，不构成自包含路径，跳过
    else if (bare.includes('/') && /^(?:[^/]+\/)+[^/]+\.md$/.test(bare)) {
      check = existsSync(resolve(dirname(file), bare)) ? resolve(dirname(file), bare) : join(ROOT, bare);
    }
    if (!check) continue;
    if (!existsSync(check)) fail(file, line, `反引号路径不存在: ${token}`);
  }
}

const META_RULES = [
  { key: '文档版本', re: /^\*\*文档版本\*\*：\s*V\d+\.\d+\s*$/, bad: '文档版本须为 V<主>.<次>（如 V1.0）' },
  { key: '日期', re: /^\*\*日期\*\*：\s*(\d{4})-(\d{2})-(\d{2})\s*$/, bad: '日期须为合法的 YYYY-MM-DD' },
  { key: '状态', re: /^\*\*状态\*\*：\s*(已发布|起草中|已废止)\s*$/, bad: '状态须为 已发布/起草中/已废止' },
];

function isValidDate(y, m, d) {
  const dt = new Date(Date.UTC(Number(y), Number(m) - 1, Number(d)));
  return dt.getUTCFullYear() === Number(y) && dt.getUTCMonth() === Number(m) - 1 && dt.getUTCDate() === Number(d);
}

function checkMeta() {
  const specRoot = join(ROOT, 'docs/00-spec');
  for (const file of walkMd(specRoot)) {
    metaCount += 1;
    const lines = readFileSync(file, 'utf8').split(/\r?\n/).slice(0, 12);
    for (const rule of META_RULES) {
      const hit = lines.map((l, idx) => ({ l, idx })).find(({ l }) => rule.re.test(l));
      if (!hit) {
        fail(file, 1, `缺少元信息 **${rule.key}**（前 12 行内）`);
        continue;
      }
      if (rule.key === '日期') {
        const [, y, m, d] = hit.l.match(rule.re);
        if (!isValidDate(y, m, d)) fail(file, hit.idx + 1, rule.bad);
      }
    }
  }
}

function loadRegistry() {
  const file = join(ROOT, 'docs/00-spec/00-governance/01-overview.md');
  const exact = new Set();
  const ranges = [];
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\|\s*(C\d+|UI-[A-Z]+-\d+(?:[～~]\d+)?)\s*\|/);
    if (!m) continue;
    const id = m[1];
    // 区间形如 "UI-SC-01～09"：前缀取到数字前，min/max 取两侧数字
    const pm = id.match(/^(UI-[A-Z]+)-(\d+)[～~](\d+)$/);
    if (pm) ranges.push({ prefix: pm[1], min: Number(pm[2]), max: Number(pm[3]) });
    else exact.add(id);
  }
  return { exact, ranges };
}

function checkRules(files) {
  const { exact, ranges } = loadRegistry();
  for (const file of files) {
    const lines = readFileSync(file, 'utf8').split(/\r?\n/);
    lines.forEach((line, i) => {
      // 限 1–3 位数字并排除 # 前缀，避免把 #C45656 等十六进制颜色当作规则号
      for (const m of line.matchAll(/(?<!#)\b(?:C\d{1,3}|UI-[A-Z]+-\d{1,3})\b/g)) {
        ruleRefCount += 1;
        const id = m[0];
        if (exact.has(id)) continue;
        const um = id.match(/^UI-([A-Z]+)-(\d+)$/);
        if (um && ranges.some((r) => r.prefix === `UI-${um[1]}` && Number(um[2]) >= r.min && Number(um[2]) <= r.max)) continue;
        fail(file, i + 1, `未登记规则: ${id}（登记册: docs/00-spec/00-governance/01-overview.md §2）`);
      }
    });
  }
}

const files = [
  ...walkMd(join(ROOT, 'docs')),
  ...['AGENTS.md', 'web/AGENTS.md', 'server/AGENTS.md'].map((p) => join(ROOT, p)).filter(existsSync),
];

for (const file of files) {
  const parsed = parseMd(file);
  checkLinks(file, parsed);
  checkInlinePaths(file, parsed);
}
checkMeta();
checkRules(files);

if (failures.length > 0) {
  for (const f of failures) console.log(`✗ ${f.file}:${f.line}  ${f.msg}`);
  console.log(`\n文档检查失败: ${failures.length} 处（链接 ${linkCount}、外链跳过 ${externalCount}、元信息 ${metaCount} 篇、规则引用 ${ruleRefCount} 处）`);
  process.exit(1);
}
console.log(`链接 ${linkCount} 条（外链跳过 ${externalCount}）、元信息 ${metaCount} 篇、规则引用 ${ruleRefCount} 处`);
console.log('✓ 无断链、旧锚点、元信息缺失与未登记规则');
