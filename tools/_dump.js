const fs=require('fs');const s=fs.readFileSync('db/schema.sql','utf8');
const SKIP=/^(PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|FOREIGN|CHECK)$/i;
const ch=s.split(/CREATE TABLE\s+`?(\w+)`?/);
for(let i=1;i<ch.length;i+=2){const t=ch[i];const b=ch[i+1]||'';const e=b.indexOf('\n)');const inner=e>=0?b.slice(0,e):b;
const cols=[];for(const l of inner.split(/\r?\n/)){const m=/^\s*([A-Za-z_]\w*)\s+([A-Za-z]+(?:\([\d,]+\))?)([^,]*?),?\s*$/.exec(l);if(!m||SKIP.test(m[1]))continue;const c=(/COMMENT\s+'([^']*)'/.exec(m[3])||[])[1]||'';cols.push(m[1]+' '+m[2]+(c?' #'+c:''));}
console.log(t+': '+cols.join(' | '));}
