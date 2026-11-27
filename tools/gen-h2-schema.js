#!/usr/bin/env node
/**
 * 把 db/schema.sql（MySQL 8 DDL）机械转换为 H2 2.x（MODE=MySQL）可执行的开发库 DDL。
 *
 * 转换点：
 *  1. 去掉 DROP/CREATE DATABASE、USE 语句；
 *  2. CREATE TABLE 变成 CREATE TABLE IF NOT EXISTS；
 *  3. 普通 KEY/INDEX 行删除；UNIQUE KEY name (cols) 转成 CONSTRAINT name UNIQUE (cols)；
 *  4. 去掉列内联 COMMENT '...'、ON UPDATE CURRENT_TIMESTAMP；
 *  5. 去掉表尾 ENGINE=... DEFAULT CHARSET=... COMMENT=... 。
 *
 * 用法：node tools/gen-h2-schema.js
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const src = path.join(root, 'db', 'schema.sql');
const out = path.join(root, 'server', 'src', 'main', 'resources', 'schema-h2.sql');

const sql = fs.readFileSync(src, 'utf8');

// 注释块保留（H2 支持 -- 注释），只剔除库级语句
const lines = sql.split(/\r?\n/).filter((line) => {
  const t = line.trim();
  if (/^DROP DATABASE/i.test(t)) return false;
  if (/^CREATE DATABASE/i.test(t)) return false;
  if (/^USE\s+/i.test(t)) return false;
  return true;
});

const stripeComment = (s) => s.replace(/COMMENT\s+'(?:[^'\\]|\\.|'')*'/gi, '');

let currentTable = '';
const result = lines.map((raw) => {
  let line = raw;
  const t = line.trim();

  const ct = t.match(/^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?/i);
  if (ct) currentTable = ct[1];

  // 表级 KEY / INDEX 删除
  if (/^KEY\s+/i.test(t) || /^INDEX\s+/i.test(t)) return null;

  // UNIQUE KEY name (cols) -> CONSTRAINT <table>_<name> UNIQUE (cols)
  // H2 的约束名在同一 schema 内必须全局唯一，MySQL 里各表可以重名，所以要加表名前缀
  const uk = t.match(/^UNIQUE\s+KEY\s+(\w+)\s*(\(.*?\))\s*,?$/i);
  if (uk) {
    return `    CONSTRAINT ${currentTable}_${uk[1]} UNIQUE ${uk[2]},`;
  }

  line = stripeComment(line);
  line = line.replace(/\s+ON\s+UPDATE\s+CURRENT_TIMESTAMP/gi, '');
  line = line.replace(/^CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)/i, 'CREATE TABLE IF NOT EXISTS ');

  // 表尾 ENGINE=... 去掉，保留分号
  if (/\)\s*ENGINE\s*=/i.test(line)) {
    line = line.replace(/\)\s*ENGINE\s*=.*$/i, ');');
  }
  return line;
}).filter((l) => l !== null);

let text = result.join('\n');

// 去掉行尾空白与连续空行
text = text.split(/\r?\n/).map((l) => l.replace(/[ \t]+$/, '')).join('\n').replace(/\n{3,}/g, '\n\n');
// 删掉 KEY 行后，最后一个约束/列可能留下尾逗号，H2 不接受 ",)"
text = text.replace(/\s*,\s*\n(\s*\)\s*;)/g, '\n$1');
// "AUTO_INCREMENT ," 这类空格逗号统一成 ","
text = text.replace(/[ \t]+,/g, ',');

const header = `-- =============================================================================
-- 同窗书 · H2 开发库 DDL（由 tools/gen-h2-schema.js 从 db/schema.sql 自动生成，请勿手改）
-- 用途 : spring.sql.init 首次启动建表（MODE=MySQL, DATABASE_TO_LOWER=TRUE）
-- =============================================================================

`;
fs.writeFileSync(out, header + text, 'utf8');

// 自检
const tables = (text.match(/CREATE TABLE IF NOT EXISTS\s+(\w+)/gi) || []).length;
const leftovers = (text.match(/ENGINE=|UNIQUE KEY|\bKEY\s+\w+\s*\(|COMMENT\s+'/gi) || []).length;
console.log(`写入 ${path.relative(root, out)}：${tables} 张表，残留需清理片段 ${leftovers} 处`);
if (leftovers > 0) process.exitCode = 1;
