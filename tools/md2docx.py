#!/usr/bin/env python3
"""把 docs/ 下的 Markdown 转成可直接交的 Word 文档（.docx）。

用法：
    python tools/md2docx.py                # 转换 docs/*.md 到 docs/word/
    python tools/md2docx.py docs/01-xxx.md # 只转一个

排版按毕业设计常见格式：正文宋体小四（12pt）、1.5 倍行距；
一级标题黑体三号居中，二级标题黑体四号，三级/四级标题黑体小四；
表格用 Table Grid，表头加粗；代码块用 Consolas 9pt 加浅灰底纹。
支持：# 标题、段落、- 列表、1. 列表、| 表格 |、```代码```、> 引用、**粗体**、`行内代码`。
"""
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm

BODY_FONT = "宋体"
HEAD_FONT = "黑体"
CODE_FONT = "Consolas"


def set_run_font(run, font=BODY_FONT, size=12, bold=False, color=None):
    run.font.name = font
    run.font.size = Pt(size)
    run.font.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)
    # 中文字体需要单独设置 eastAsia
    run._element.rPr.rFonts.set(qn("w:eastAsia"), font)


def shade(paragraph, hex_fill="F2F2F2"):
    p_pr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    p_pr.append(shd)


INLINE = re.compile(r"(\*\*.+?\*\*|`[^`]+`)")


def add_inline(paragraph, text, size=12, base_bold=False):
    """把 **粗体** 与 `代码` 解析成 run。"""
    pos = 0
    for m in INLINE.finditer(text):
        if m.start() > pos:
            r = paragraph.add_run(text[pos:m.start()])
            set_run_font(r, size=size, bold=base_bold)
        token = m.group(0)
        if token.startswith("**"):
            r = paragraph.add_run(token[2:-2])
            set_run_font(r, size=size, bold=True)
        else:
            r = paragraph.add_run(token[1:-1])
            set_run_font(r, font=CODE_FONT, size=size - 1)
            r.font.color.rgb = RGBColor(0xC7, 0x25, 0x4E)
        pos = m.end()
    if pos < len(text):
        r = paragraph.add_run(text[pos:])
        set_run_font(r, size=size, bold=base_bold)


def style_document(doc):
    normal = doc.styles["Normal"]
    normal.font.name = BODY_FONT
    normal.font.size = Pt(12)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.space_after = Pt(6)

    for level, size, align in ((1, 16, WD_ALIGN_PARAGRAPH.CENTER), (2, 14, None), (3, 12, None), (4, 12, None)):
        st = doc.styles[f"Heading {level}"]
        st.font.name = HEAD_FONT
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = RGBColor(0, 0, 0)
        st.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT)
        if align is not None:
            st.paragraph_format.alignment = align
        st.paragraph_format.space_before = Pt(12 if level <= 2 else 8)
        st.paragraph_format.space_after = Pt(8 if level <= 2 else 6)


def add_table(doc, rows):
    header = [c.strip() for c in rows[0].strip().strip("|").split("|")]
    body = []
    for line in rows[2:]:
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < len(header):
            cells += [""] * (len(header) - len(cells))
        body.append(cells[: len(header)])

    table = doc.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, name in enumerate(header):
        cell = table.rows[0].cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        add_inline(p, name, size=10.5, base_bold=True)
    for row in body:
        cells = table.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            add_inline(cells[i].paragraphs[0], val, size=10)
    doc.add_paragraph()


def add_code(doc, lines, lang=""):
    for line in lines:
        p = doc.add_paragraph()
        p.paragraph_format.line_spacing = 1.0
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.left_indent = Cm(0.5)
        r = p.add_run(line if line.strip() else " ")
        set_run_font(r, font=CODE_FONT, size=9)
        shade(p)
    doc.add_paragraph()


def convert(md_path: Path, out_dir: Path):
    text = md_path.read_text(encoding="utf-8")
    doc = Document()
    style_document(doc)

    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # 代码块
        if stripped.startswith("```"):
            lang = stripped[3:].strip()
            i += 1
            block = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            i += 1
            if lang == "mermaid":
                p = doc.add_paragraph()
                add_inline(p, "（下图由 Mermaid 源码描述，可在支持 Mermaid 的编辑器预览）", size=10.5)
            add_code(doc, block)
            continue

        # 表格
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|$", lines[i + 1].strip()):
            block = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                block.append(lines[i])
                i += 1
            add_table(doc, block)
            continue

        # 标题
        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            level = min(len(m.group(1)), 4)
            doc.add_heading(m.group(2).strip(), level=level)
            i += 1
            continue

        # 分隔线
        if re.match(r"^([-*_])\1{2,}$", stripped):
            i += 1
            continue

        # 引用
        if stripped.startswith(">"):
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Cm(0.8)
            add_inline(p, stripped.lstrip("> ").strip(), size=11)
            for r in p.runs:
                r.font.italic = True
            i += 1
            continue

        # 无序列表
        m = re.match(r"^[-*+]\s+(.*)$", stripped)
        if m:
            p = doc.add_paragraph(style="List Bullet")
            add_inline(p, m.group(1))
            i += 1
            continue

        # 有序列表
        m = re.match(r"^(\d+)[.)]\s+(.*)$", stripped)
        if m:
            p = doc.add_paragraph(style="List Number")
            add_inline(p, m.group(2))
            i += 1
            continue

        # 空行
        if not stripped:
            i += 1
            continue

        # 普通段落：把连续非空行并成一段
        para = [stripped]
        i += 1
        while i < len(lines):
            nxt = lines[i].strip()
            if (not nxt or nxt.startswith(("#", "|", "```", ">", "- ", "* ", "+ "))
                    or re.match(r"^\d+[.)]\s", nxt)):
                break
            para.append(nxt)
            i += 1
        p = doc.add_paragraph()
        p.paragraph_format.first_line_indent = Cm(0.74)  # 首行缩进两字符
        add_inline(p, "".join(para) if all(len(x) > 0 for x in para) else " ".join(para))

    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / (md_path.stem + ".docx")
    doc.save(out)
    return out


def main():
    root = Path(__file__).resolve().parent.parent
    docs = root / "docs"
    out_dir = docs / "word"

    targets = [Path(a) for a in sys.argv[1:]] or sorted(docs.glob("*.md"))
    if not targets:
        print("docs/ 下没有 .md 文件")
        return
    for md in targets:
        md = md if md.is_absolute() else (Path.cwd() / md)
        if not md.exists():
            print(f"跳过（不存在）：{md}")
            continue
        out = convert(md, out_dir)
        print(f"已生成 {out.relative_to(root)}（{out.stat().st_size / 1024:.0f} KB）")


if __name__ == "__main__":
    main()
