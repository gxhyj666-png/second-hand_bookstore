/**
 * 由 db/schema.sql 生成 MyBatis-Plus 的实体类与 Mapper 接口。
 * 用法：node tools/gen-entities.js
 * 生成到 server/src/main/java/com/textbook/loop/{domain,mapper}
 * 说明：只做机械映射，生成后仍可手工调整；重跑会覆盖 domain/mapper 下同名文件。
 */
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const schemaPath = path.join(root, 'db', 'schema.sql');
const sql = fs.readFileSync(schemaPath, 'utf8');

const javaRoot = path.join(root, 'server', 'src', 'main', 'java', 'com', 'textbook', 'loop');
const domainDir = path.join(javaRoot, 'domain');
const mapperDir = path.join(javaRoot, 'mapper');
fs.mkdirSync(domainDir, { recursive: true });
fs.mkdirSync(mapperDir, { recursive: true });

const pascal = (s) => s.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join('');
const camel = (s) => {
  const p = pascal(s);
  return p.charAt(0).toLowerCase() + p.slice(1);
};

const JAVA_TYPES = [
  [/^bigint$/i, 'Long'],
  [/^int$/i, 'Integer'],
  [/^tinyint$/i, 'Integer'],
  [/^smallint$/i, 'Integer'],
  [/^decimal$/i, 'BigDecimal'],
  [/^double$/i, 'Double'],
  [/^float$/i, 'Double'],
  [/^datetime$/i, 'LocalDateTime'],
  [/^timestamp$/i, 'LocalDateTime'],
  [/^date$/i, 'LocalDate'],
  [/^time$/i, 'LocalTime'],
  [/^(varchar|char|text|longtext|mediumtext|json)$/i, 'String'],
];

function javaType(base) {
  for (const [re, t] of JAVA_TYPES) if (re.test(base)) return t;
  return 'String';
}

const colRe = /^\s*([A-Za-z_]\w*)\s+([A-Za-z]+)(\s*\([^)]*\))?([^,]*?),?\s*$/;
const SKIP_WORDS = /^(PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|FOREIGN|CHECK)$/i;

// 用 indexOf 切分，避免大文件上的正则回溯
const tables = [];
const chunks = sql.split(/CREATE TABLE\s+`?(\w+)`?/);
for (let i = 1; i < chunks.length; i += 2) {
  const table = chunks[i];
  const body = chunks[i + 1] || '';
  const end = body.indexOf('\n)');
  const inner = end >= 0 ? body.slice(0, end) : body;
  const cols = [];
  let pk = null;
  for (const line of inner.split(/\r?\n/)) {
    const cm = colRe.exec(line);
    if (!cm) continue;
    const name = cm[1];
    if (SKIP_WORDS.test(name)) continue;
    const type = cm[2];
    const rest = cm[3] + cm[4];
    const commentMatch = /COMMENT\s+'([^']*)'/.exec(rest);
    const isPk = /AUTO_INCREMENT/i.test(rest);
    if (isPk) pk = name;
    cols.push({
      name,
      type: javaType(type),
      comment: commentMatch ? commentMatch[1] : '',
      autoInc: isPk,
    });
  }
  if (cols.length === 0) continue;
  let pkInput = false;
  if (!pk) {
    // 无自增列（如 sys_idempotent 用 request_id 作主键）：取首列并标记为手工赋值
    pk = cols[0].name;
    pkInput = true;
  }
  tables.push({ table, cols, pk, pkInput });
}

function genEntity(t) {
  const cls = pascal(t.table);
  const imports = new Set(['com.baomidou.mybatisplus.annotation.TableName', 'java.io.Serializable', 'lombok.Data']);
  const fields = t.cols.map((c) => {
    const ann = [];
    if (c.name === t.pk) {
      imports.add('com.baomidou.mybatisplus.annotation.TableId');
      if (c.autoInc || t.pkInput) imports.add('com.baomidou.mybatisplus.annotation.IdType');
      ann.push(c.autoInc ? '    @TableId(type = IdType.AUTO)' : t.pkInput ? '    @TableId(type = IdType.INPUT)' : '    @TableId');
    } else if (/^(created_at|update_time|create_time)$/.test(c.name)) {
      imports.add('com.baomidou.mybatisplus.annotation.TableField');
      imports.add('com.baomidou.mybatisplus.annotation.FieldFill');
      ann.push('    @TableField(fill = FieldFill.INSERT)');
    } else if (/^(updated_at)$/.test(c.name)) {
      imports.add('com.baomidou.mybatisplus.annotation.TableField');
      imports.add('com.baomidou.mybatisplus.annotation.FieldFill');
      ann.push('    @TableField(fill = FieldFill.INSERT_UPDATE)');
    } else if (c.name === 'deleted') {
      imports.add('com.baomidou.mybatisplus.annotation.TableLogic');
      ann.push('    @TableLogic');
    }
    if (c.type === 'BigDecimal') imports.add('java.math.BigDecimal');
    if (c.type === 'LocalDateTime') imports.add('java.time.LocalDateTime');
    if (c.type === 'LocalDate') imports.add('java.time.LocalDate');
    if (c.type === 'LocalTime') imports.add('java.time.LocalTime');
    const doc = c.comment ? `    /** ${c.comment} */\n` : '';
    return `${doc}${ann.join('\n')}${ann.length ? '\n' : ''}    private ${c.type} ${camel(c.name)};`;
  });
  const importList = [...imports].sort();
  return `package com.textbook.loop.domain;

${importList.map((i) => `import ${i};`).join('\n')}

/**
 * ${t.table}
 */
@Data
@TableName("${t.table}")
public class ${cls} implements Serializable {

    private static final long serialVersionUID = 1L;

${fields.join('\n\n')}
}
`;
}

function genMapper(t) {
  const cls = pascal(t.table);
  return `package com.textbook.loop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.textbook.loop.domain.${cls};
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ${cls}Mapper extends BaseMapper<${cls}> {
}
`;
}

let count = 0;
for (const t of tables) {
  const cls = pascal(t.table);
  fs.writeFileSync(path.join(domainDir, `${cls}.java`), genEntity(t), 'utf8');
  fs.writeFileSync(path.join(mapperDir, `${cls}Mapper.java`), genMapper(t), 'utf8');
  count++;
}
console.log(`generated ${count} entities + ${count} mappers`);
console.log(tables.map((t) => `${t.table}(${t.cols.length})`).join(', '));

