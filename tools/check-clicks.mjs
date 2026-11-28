#!/usr/bin/env node
/**
 * 前端交互自检：确保“每一个能点的东西都真的有反应”。
 *
 * 一、静态检查（默认，秒级，不需要起服务）
 *   扫 web/src 与 admin/src 下所有 .vue：
 *     A 类：按钮 / 链接没有任何点击绑定，也没有 to / href / command
 *     B 类：@click 里调用的函数在这个文件里没有定义（点了要么报错、要么静默无效）
 *     C 类：router.push / router.replace 跳到 router/index.js 里不存在的路径
 *
 * 二、实机检查（--live，需要后端 8088 + 前端 dev server 在跑）
 *   起无头 Edge，登录一次，把路由表里每个静态路径都真的打开一遍，
 *   报告「白屏 / 控制台异常 / 被踢回登录页」的页面。
 *
 * 用法：
 *   node tools/check-clicks.mjs                       # 静态全量
 *   node tools/check-clicks.mjs web                   # 只扫 web
 *   node tools/check-clicks.mjs --live                # 静态 + web 实机（默认 5174）
 *   node tools/check-clicks.mjs --live --url=http://localhost:5173 --app=admin
 *   node tools/check-clicks.mjs --json                # 机器可读输出
 *
 * 退出码：0 = 没有问题；1 = 有问题（提交前必须为 0）。
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { spawn } from 'node:child_process';

const ROOT = path.resolve(import.meta.dirname, '..');
const APPS = [
  { name: 'web', dir: path.join(ROOT, 'web', 'src'), router: path.join(ROOT, 'web', 'src', 'router', 'index.js'), base: 'http://localhost:5174', tokenKey: 'tl_token', userKey: 'tl_user' },
  { name: 'admin', dir: path.join(ROOT, 'admin', 'src'), router: path.join(ROOT, 'admin', 'src', 'router', 'index.js'), base: 'http://localhost:5173', tokenKey: 'ts_admin_token', userKey: 'ts_admin_user' },
];

const args = process.argv.slice(2);
const ONLY = args.find((a) => a === 'web' || a === 'admin');
const AS_JSON = args.includes('--json');
const LIVE = args.includes('--live');
const ARG = (name) => (args.find((a) => a.startsWith(`--${name}=`)) || '').split('=').slice(1).join('=');

const appArg = ['web', 'admin'].includes(ARG('app')) ? ARG('app') : null;
const LIVE_APP = appArg || ONLY || 'web';

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (['node_modules', 'dist', '.vite'].includes(entry.name)) continue;
      walk(full, out);
    } else if (entry.name.endsWith('.vue')) {
      out.push(full);
    }
  }
  return out;
}

function lineOf(text, index) {
  let line = 1;
  for (let i = 0; i < index && i < text.length; i++) if (text.charCodeAt(i) === 10) line++;
  return line;
}

function splitSfc(text) {
  const tplStart = text.indexOf('<template>');
  let template = text;
  let tplOffset = 0;
  if (tplStart >= 0) {
    const tplEnd = text.lastIndexOf('</template>');
    tplOffset = tplStart + '<template>'.length;
    template = tplEnd > tplStart ? text.slice(tplOffset, tplEnd) : text.slice(tplOffset);
  }
  const scripts = [];
  for (const m of text.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) scripts.push(m[1]);
  return { template, tplOffset, script: scripts.join('\n') };
}

// 语言关键字与运行期全局，不算“未定义函数”
const KEYWORDS = new Set([
  'if', 'else', 'for', 'while', 'switch', 'case', 'return', 'function', 'new', 'await', 'async', 'this',
  'true', 'false', 'null', 'undefined', 'typeof', 'in', 'of', 'delete', 'void', 'try', 'catch', 'let', 'const', 'var',
  'event', '$event', 'window', 'document', 'console', 'Math', 'Number', 'String', 'Boolean', 'Object', 'Array',
  'JSON', 'Date', 'parseInt', 'parseFloat', 'setTimeout', 'setInterval', 'clearTimeout', 'clearInterval',
  'encodeURIComponent', 'decodeURIComponent', 'isNaN', 'RegExp', 'Promise', 'Error', 'structuredClone',
  'router', 'route', 'store', 'emit', 'props', '$router', '$route', '$emit', '$refs', '$nextTick',
  'set', 'get', 'length', 'value', 'trim', 'toFixed', 'toString', 'slice', 'split', 'join', 'map', 'filter',
  'find', 'includes', 'replace', 'push', 'pop', 'sort', 'reverse', 'concat', 'some', 'every', 'forEach',
  'reduce', 'keys', 'values', 'entries', 'then', 'catch', 'finally', 'toLowerCase', 'toUpperCase',
]);

/** script 里定义 / 导入过的名字 + 模板作用域变量（v-for / 插槽） */
function collectSymbols(script, template) {
  const names = new Set();
  const addAll = (raw) => {
    for (const part of raw.split(',')) {
      const name = part.trim().split(/\s+as\s+/).pop().trim()
        .replace(/^\.\.\./, '').replace(/[:=].*$/, '').replace(/\s.*$/, '');
      if (/^[A-Za-z_$][\w$]*$/.test(name)) names.add(name);
    }
  };
  for (const m of script.matchAll(/\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)/g)) names.add(m[1]);
  for (const m of script.matchAll(/\bfunction\s+([A-Za-z_$][\w$]*)/g)) names.add(m[1]);
  for (const m of script.matchAll(/\b(?:const|let|var)\s*\{([^}]*)\}\s*=/g)) addAll(m[1]);
  for (const m of script.matchAll(/import\s+([A-Za-z_$][\w$]*)\s*(?:,|from)/g)) names.add(m[1]);
  for (const m of script.matchAll(/import\s*\{([^}]*)\}\s*from/g)) addAll(m[1]);
  for (const m of script.matchAll(/import\s*\*\s*as\s+([A-Za-z_$][\w$]*)/g)) names.add(m[1]);
  // 模板作用域：v-for="(item, i) in list" / v-slot="{ row }" / #default="scope"
  for (const m of template.matchAll(/v-for\s*=\s*"\(?([^)"]*?)\)?\s+in\b/g)) addAll(m[1].replace(/\s+in.*$/, ''));
  for (const m of template.matchAll(/(?:v-slot|#default|slot-scope)\s*(?:=\s*)?"\{([^}]*)\}"/g)) addAll(m[1]);
  for (const m of template.matchAll(/(?:v-slot|#default|slot-scope)\s*(?:=\s*)?"([A-Za-z_$][\w$]*)"/g)) names.add(m[1]);
  return names;
}

function collectRoutes(routerFile) {
  const routes = new Set();
  if (!fs.existsSync(routerFile)) return routes;
  const text = fs.readFileSync(routerFile, 'utf8');
  for (const m of text.matchAll(/path:\s*['"]([^'"]*)['"]/g)) routes.add(m[1]);
  return routes;
}

function routeExists(target, routes) {
  if (!target || target.startsWith('http')) return true;
  const clean = target.split('?')[0].split('#')[0];
  if (!clean || clean === '/') return true;
  if (routes.has(clean)) return true;
  for (const r of routes) {
    // 跳过 404 兜底路由（/:pathMatch(.*)* 之类），否则任何路径都算“存在”
    if (r.includes('*') || r.includes('(')) continue;
    const re = new RegExp('^' + r.replace(/:[^/]+/g, '[^/]+').replace(/\//g, '\\/') + '$');
    if (re.test(clean)) return true;
  }
  return false;
}

// 只把“长得像按钮/链接”的标签算进来；容器类（van-cell-group / el-form 等）不算
const CLICKABLE_TAG = /<(van-button|button|a|router-link|el-button|el-link|el-dropdown-item)\b/g;
const ACTION_ATTR = /(?:@click(?:\.\w+)*|v-on:click(?:\.\w+)*|@tap(?:\.\w+)*|:to=|to=|href=|command=|@touchstart)/;

/**
 * 从 start 处扫描一个标签的属性串，正确跳过引号内的 ">"（如 :disabled="countdown > 0"）。
 */
function scanTag(template, start) {
  let i = start;
  let quote = null;
  while (i < template.length) {
    const ch = template[i];
    if (quote) {
      if (ch === quote) quote = null;
    } else if (ch === '"' || ch === "'" || ch === '`') {
      quote = ch;
    } else if (ch === '>') {
      return { attrs: template.slice(start, i), end: i + 1 };
    }
    i++;
  }
  return { attrs: template.slice(start), end: template.length };
}

/* ============================ 一、静态检查 ============================ */

const issues = [];
let scannedFiles = 0;
let scannedElements = 0;

for (const app of APPS) {
  if (ONLY && ONLY !== app.name) continue;
  const routes = collectRoutes(app.router);
  for (const file of walk(app.dir)) {
    scannedFiles++;
    const text = fs.readFileSync(file, 'utf8');
    const rel = path.relative(ROOT, file).replace(/\\/g, '/');
    const { template, tplOffset, script } = splitSfc(text);
    const symbols = collectSymbols(script, template);

    for (const m of template.matchAll(CLICKABLE_TAG)) {
      scannedElements++;
      const tag = m[1];
      const { attrs } = scanTag(template, m.index + m[0].length);
      const line = lineOf(text, tplOffset + m.index);
      const snippet = `<${tag}${attrs.slice(0, 90).replace(/\s+/g, ' ')}>`;
      if (!ACTION_ATTR.test(attrs)) {
        issues.push({ app: app.name, file: rel, line, kind: 'A', msg: `可点元素没有任何点击绑定：${snippet}` });
        continue;
      }
      for (const em of attrs.matchAll(/(?:@click|v-on:click|@tap)(?:\.\w+)*\s*=\s*"([^"]*)"/g)) {
        // 去掉字符串字面量，避免把 '/group-buys' 里的单词当成标识符
        const expr = em[1].replace(/'[^']*'/g, "''").replace(/"[^"]*"/g, '""').replace(/`[^`]*`/g, '``');
        const names = new Set();
        for (const cm of expr.matchAll(/(?<![.\w$])([A-Za-z_$][\w$]*)\s*\(/g)) names.add(cm[1]);
        for (const cm of expr.matchAll(/(?<![.\w$])([A-Za-z_$][\w$]*)(?!\s*[.\w$(])/g)) names.add(cm[1]);
        for (const name of names) {
          if (KEYWORDS.has(name) || symbols.has(name) || name.startsWith('$')) continue;
          issues.push({
            app: app.name, file: rel, line, kind: 'B',
            msg: `@click 调用了未定义的 \`${name}\`：${snippet}`,
          });
        }
      }
    }

    for (const m of text.matchAll(/router\.(?:push|replace)\(\s*(?:\{\s*path:\s*)?[`'"]([^`'"]+)[`'"]/g)) {
      const target = m[1];
      if (target.includes('${')) continue;
      if (!routeExists(target, routes)) {
        issues.push({ app: app.name, file: rel, line: lineOf(text, m.index), kind: 'C', msg: `router 跳转到不存在的路由：${target}` });
      }
    }
  }
}

function reportStatic() {
  if (AS_JSON) return;
  if (issues.length === 0) {
    console.log(`✅ 交互自检通过：扫描 ${scannedFiles} 个 .vue、${scannedElements} 个可点元素，没有发现无响应的按钮 / 未定义的点击处理 / 失效路由。`);
  } else {
    const byFile = new Map();
    for (const it of issues) {
      if (!byFile.has(it.file)) byFile.set(it.file, []);
      byFile.get(it.file).push(it);
    }
    for (const [file, list] of byFile) {
      console.log(`\n${file}`);
      for (const it of list) console.log(`  L${it.line}  [${it.kind}] ${it.msg}`);
    }
    console.log(`\n共 ${issues.length} 个问题（A 无点击绑定 / B 未定义函数 / C 路由不存在）。提交前必须为 0。`);
  }
}

/* ============================ 二、实机检查 ============================ */

const EDGE_CANDIDATES = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
];

function findEdge() {
  return EDGE_CANDIDATES.find((p) => p && fs.existsSync(p));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 极简 CDP 客户端：只为“打开页面 + 读 DOM + 收集异常”服务 */
class Cdp {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.id = 0;
    this.pending = new Map();
    this.errors = [];
    this.ready = new Promise((resolve, reject) => {
      this.ws.addEventListener('open', () => resolve());
      this.ws.addEventListener('error', (e) => reject(new Error('WebSocket 连接失败：' + (e.message || 'unknown'))));
    });
    this.ws.addEventListener('message', (ev) => {
      let msg;
      try { msg = JSON.parse(ev.data); } catch { return; }
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        if (msg.error) reject(new Error(msg.error.message));
        else resolve(msg.result);
        return;
      }
      if (msg.method === 'Runtime.exceptionThrown') {
        const d = msg.params.exceptionDetails;
        this.errors.push(d.exception?.description || d.text || 'Runtime exception');
      } else if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
        this.errors.push(msg.params.entry.text);
      } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        this.errors.push(msg.params.args.map((a) => a.value ?? a.description ?? '').join(' '));
      }
    });
  }

  call(method, params = {}) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error(`${method} 超时`));
        }
      }, 15000);
    });
  }

  close() {
    try { this.ws.close(); } catch { /* ignore */ }
  }
}

async function launchBrowser(port) {
  const edge = findEdge();
  if (!edge) throw new Error('找不到 Edge；请手动指定或在有 Edge 的机器上跑');
  const userDir = path.join(os.tmpdir(), `tl-clicks-live-${Date.now()}`);
  const proc = spawn(edge, [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDir}`,
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    'about:blank',
  ], { stdio: 'ignore', windowsHide: true });

  const endpoint = `http://127.0.0.1:${port}/json/list`;
  let target = null;
  for (let i = 0; i < 60 && !target; i++) {
    await sleep(300);
    try {
      const list = await (await fetch(endpoint)).json();
      target = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl);
    } catch { /* 还没起来 */ }
  }
  if (!target) {
    proc.kill();
    throw new Error('Edge 无头实例启动超时（remote debugging 端口没响应）');
  }
  return { proc, wsUrl: target.webSocketDebuggerUrl, userDir };
}

async function liveScan() {
  const app = APPS.find((a) => a.name === LIVE_APP);
  const base = (ARG('url') || app.base).replace(/\/$/, '');
  // 后端的真实入口：web 开发期走 Vite 代理 /api（会去掉前缀），admin 的 .env.development 直连 8088
  const api = (ARG('api') || (app.name === 'admin' ? 'http://127.0.0.1:8088' : `${base}/api`)).replace(/\/$/, '');
  const routes = [...collectRoutes(app.router)]
    .filter((p) => !p.includes('*') && !p.includes('(') && !p.includes(':'))
    .map((p) => (p.startsWith('/') ? p : `/${p}`));
  const account = ARG('account') || (app.name === 'admin' ? '13800000001' : '13800000002');
  const password = ARG('password') || (app.name === 'admin' ? 'admin123' : '123456');

  console.log(`\n[实机] 目标 ${base}（${app.name}），路由 ${routes.length} 条，登录接口 ${api}`);
  const { proc, wsUrl, userDir } = await launchBrowser(9333);
  const cdp = new Cdp(wsUrl);
  let failures = 0;
  let skipped = 0;
  const details = [];

  try {
    await cdp.ready;
    await cdp.call('Runtime.enable');
    await cdp.call('Log.enable');
    await cdp.call('Page.enable');

    // 登录拿 token（拿不到就只做游客可见页面的检查）
    let session = null;
    try {
      const res = await fetch(`${api}/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ account, password }),
      });
      const json = await res.json();
      if (json.code === 0) session = json.data;
      else console.log(`[实机] 登录被拒：${json.message}（改为游客态检查）`);
    } catch (e) {
      console.log(`[实机] 登录请求失败：${e.message}（改为游客态检查）`);
    }

    // 首页先落地，才能写 localStorage
    await cdp.call('Page.navigate', { url: `${base}/` });
    await sleep(1200);
    if (session) {
      const payload = JSON.stringify({ token: session.token, user: session.user });
      await cdp.call('Runtime.evaluate', {
        expression: `localStorage.setItem(${JSON.stringify(app.tokenKey)}, ${JSON.stringify(session.token)});
                     localStorage.setItem(${JSON.stringify(app.userKey)}, ${JSON.stringify(JSON.stringify(session.user))});
                     localStorage.setItem('tl_roles', ${JSON.stringify((session.roles || []).join(','))});
                     void 0;`,
        awaitPromise: false,
      });
      void payload;
    }

    let index = 0;
    for (const route of routes) {
      cdp.errors = [];
      // 带一个变化的 query 强制整页加载：只改 hash 不会重载页面，store 会沿用上一个页面的状态
      const url = `${base}/?live=${++index}#${route}`;
      await cdp.call('Page.navigate', { url });
      await sleep(1100);
      let info = null;
      try {
        const r = await cdp.call('Runtime.evaluate', {
          expression: `(() => {
            const app = document.querySelector('#app');
            return {
              hash: location.hash,
              title: document.title,
              nodes: app ? app.querySelectorAll('*').length : -1,
              text: (document.body.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 60),
            };
          })()`,
          returnByValue: true,
        });
        info = r.result.value;
      } catch (e) {
        details.push({ route, problem: `读取页面失败：${e.message}` });
        failures++;
        continue;
      }

      const problems = [];
      const redirectedToLogin = info.hash.startsWith('#/login') && route !== '/login';
      if (info.nodes <= 1) problems.push(`白屏（#app 子节点 ${info.nodes}）`);
      if (cdp.errors.length) problems.push(`控制台异常：${cdp.errors[0].slice(0, 160)}`);
      if (redirectedToLogin && session) problems.push('已登录却被踢回登录页');

      if (problems.length === 0) {
        if (redirectedToLogin) {
          skipped++;
          console.log(`  - ${route}  需登录（游客态跳过）`);
        } else {
          console.log(`  ✓ ${route}  ${info.title} ｜ ${info.text.slice(0, 34)}`);
        }
      } else {
        failures++;
        details.push({ route, problem: problems.join('；') });
        console.log(`  ✗ ${route}  ${problems.join('；')}`);
      }
    }
  } finally {
    cdp.close();
    proc.kill();
    setTimeout(() => {
      try { fs.rmSync(userDir, { recursive: true, force: true }); } catch { /* ignore */ }
    }, 500);
  }

  console.log(`\n[实机] ${routes.length} 条路由：${routes.length - failures - skipped} 正常 / ${failures} 异常 / ${skipped} 需登录跳过`);
  if (failures && AS_JSON) console.log(JSON.stringify({ live: { failures, details } }, null, 2));
  return failures;
}

/* ============================ 输出与退出码 ============================ */

const staticExit = issues.length === 0 ? 0 : 1;
if (AS_JSON && !LIVE) {
  console.log(JSON.stringify({ total: issues.length, scannedFiles, scannedElements, issues }, null, 2));
} else {
  reportStatic();
}

let liveFailures = 0;
if (LIVE) {
  try {
    liveFailures = await liveScan();
  } catch (e) {
    console.error(`[实机] 检查没跑起来：${e.message}`);
    liveFailures = 1;
  }
}

process.exit(staticExit === 0 && liveFailures === 0 ? 0 : 1);
