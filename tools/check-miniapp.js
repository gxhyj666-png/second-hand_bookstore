#!/usr/bin/env node
/**
 * 小程序结构自检：app.json 里声明的每个页面都必须存在 .js/.wxml/.json/.wxss 四件套。
 * 用于 CI，避免出现"页面注册了但文件没提交"这类只在真机上才暴露的问题。
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', 'miniapp');
const appJsonPath = path.join(root, 'app.json');

if (!fs.existsSync(appJsonPath)) {
  console.error(`找不到 ${appJsonPath}`);
  process.exit(1);
}

const app = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'));
const pages = app.pages || [];
const exts = ['.js', '.wxml', '.json', '.wxss'];
const missing = [];

for (const page of pages) {
  for (const ext of exts) {
    const file = path.join(root, page + ext);
    if (!fs.existsSync(file)) {
      missing.push(path.relative(root, file));
    }
  }
}

if (missing.length > 0) {
  console.error(`缺少 ${missing.length} 个页面文件：`);
  missing.forEach((m) => console.error('  - ' + m));
  process.exit(1);
}

console.log(`小程序结构检查通过：${pages.length} 个页面，${pages.length * exts.length} 个文件齐全`);
