# -*- coding: utf-8 -*-
"""生成毕设选题汇总表（单行，六列），A4 横向，套参考表样式。"""
import os
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_ORIENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_ALIGN_VERTICAL
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = r"C:/Users/86180/Desktop/second-hand_bookstore/docs/毕设选题表-同窗书.docx"

FONT = "宋体"
SIZE = Pt(9.5)        # 介于小五与五号之间
LINE = Pt(12)

TITLE = [
    "基于 SpringBoot + Vue 的校园二手教材循环交易",
    "与信用担保平台的设计与实现",
    "——「同窗书」二手教材循环平台",
]

TECH = [
    "1. 后端框架：Spring Boot 3.2.5 + Java 17（分层单体：controller / service / mapper，16 个控制器、117 个 /v1 接口）",
    "2. 持久层：MyBatis-Plus 3.5.5（Lambda 条件构造器 + 分页插件）+ MySQL 8.0（37 张表），开发期 H2 2.2（MODE=MySQL 文件库）",
    "3. 认证鉴权：JJWT 0.12.5（7 天免登录 Token）+ BCrypt（spring-security-crypto）+ 拦截器解析 + ThreadLocal 用户上下文；五角色 STUDENT / LEADER / MERCHANT / OPERATOR / ADMIN 数据权限隔离",
    "4. 用户端前端：Vue 3.5 + Vite 5.4 + Vant 4.9 + Pinia 2.2 + Vue Router 4.4 + Axios（响应式 H5，可封装为 APK）",
    "5. 管理后台：Vue 3.4 + Element Plus 2.7 + ECharts 5.5 + Vite 5.4",
    "6. 事务与定时：Spring @Transactional 声明式事务 + 条件更新扣库存（防超卖）+ 幂等表防重复提交 + @Scheduled 每分钟扫描超时关单",
    "7. 文件存储：本地磁盘 uploads/（按月分目录）+ 静态资源映射，图片与视频统一上传接口，可平替 OSS / MinIO",
    "8. 部署运维：Docker Compose（MySQL 8.0 + server + Nginx 静态托管）+ Maven Wrapper + 冒烟脚本",
]

REQ = [
    ("h", "【题目概述】"),
    ("p", "针对校园教材“一学期一换、毕业即闲置”造成的低效流通问题，构建以课程—教材知识库为主线、以实名双认证与信用分为信任基础、以平台担保与线下约见核销为交割方式的二手教材循环交易平台。平台围绕发布、下单、支付、约见、核销、退款、评价七类交易环节，按课程、教材与品相维度沉淀同教材成交价指数用于定价参考；订单采用“待支付—待核销—已完成”三态主干与退款仲裁旁支，配合 5 分钟超时关单、条件更新锁库存与幂等表保证并发一致；核销码与约定时间绑定、失效后由卖家重新生成，退款区分退货退款与仅退款，纠纷由平台判责并扣减信用分。管理端提供商户与商品审核、纠纷仲裁、进阶码与提成管理、七日经营看板，帮助平台掌握真实成交并识别刷单风险。"),
    ("h", "【功能模块】"),
    ("p", "1. 用户注册登录模块：手机号短信验证码登录（未注册自动建号）、微信 code 登录、个人中心设置与修改密码、JWT 7 天免登录，平台运营 / 商户 / 学生三级账号与数据权限隔离。"),
    ("p", "2. 身份认证模块：上传身份证正反面与学生证封面、信息页，三方核验结果留痕；未完成双认证仅可浏览，不可下单、发布与私信。"),
    ("p", "3. 课程教材知识库模块：维护课程库、教材主数据（按版次一版一条 ISBN-13）与课程教材映射（必选 / 参考），支持 ISBN 查询自动带出出版信息与参考价。"),
    ("p", "4. 商品发布与品相模块：五档品相（全新 / 几乎全新 / 良好 / 一般 / 较差）、七张实拍照片（封面、封底、出版日期页、四页正文）与可选展示视频，发布时给出同教材成交价指数（最低 / 最高 / 均价 / 建议价）。"),
    ("p", "5. 检索与推荐模块：按课程、教材、关键词、品相组合筛选与四种排序，游客可浏览；推荐采用“收藏 + 同课程成交”规则召回，冷启动回退热门。"),
    ("p", "6. 订单与担保交易模块：下单即锁商品（条件更新防超卖）、幂等表防重复提交、5 分钟未付自动关单；支持买家取消、卖家取消与付款后取消，按责任方扣 3 分并记录关闭原因。"),
    ("p", "7. 约见与线下核销模块：买卖双方在会话内发起交易地点与时间并确认；买家出示 6 位核销码（15 分钟有效且不晚于约定时间后 15 分钟），失效由卖家重新生成、旧码立即作废；卖家核销并上传签到照，核销与结算分别落库。"),
    ("p", "8. 退款与平台仲裁模块：支持退货退款（需再次确认地点时间）与仅退款（卖家同意即原路退回）；卖家拒绝可申请平台介入，判卖家责任扣 5 分强制退款、判买家责任扣 5 分终止退货，纠纷工单支持多轮举证。"),
    ("p", "9. 信用评价与爽约模块：交易完成双向星级评价按 3 / 0 / -3 分增减信用并留流水；约定时间后 30 分钟未到场可上报爽约，被上报方扣 5 分。"),
    ("p", "10. 多角色经营模块：个体凭商户进阶码直接入驻并批量上下架（单批 ≤100 条）；团长按课程发起班级接龙（6 位邀请码、满额自动成团）；个体凭运营进阶码升级运营，获得专属推广码绑定下线并按成交额 1% 计提成。"),
    ("p", "11. 平台管理后台与看板模块：用户 / 商品 / 教材 / 订单管理、商户与商品审核、纠纷仲裁、进阶码批量生成与作废；看板提供 7 日成交趋势、转化漏斗（曝光→详情→发布→下单→支付→核销）、品相分布与 GMV。"),
]

HEADERS = ["序号", "姓名", "学号", "选题名称", "技术选型", "需求描述"]
WIDTHS = [Cm(1.0), Cm(1.6), Cm(2.0), Cm(4.0), Cm(6.0), Cm(12.7)]


def set_run(run, size=SIZE, bold=False, font=FONT):
    run.font.name = font
    run.font.size = size
    run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    rf = rpr.find(qn("w:rFonts"))
    if rf is None:
        rf = OxmlElement("w:rFonts")
        rpr.append(rf)
    rf.set(qn("w:eastAsia"), font)
    rf.set(qn("w:ascii"), font)
    rf.set(qn("w:hAnsi"), font)


def fmt_par(p, align=WD_ALIGN_PARAGRAPH.LEFT, space_before=0, space_after=0):
    pf = p.paragraph_format
    pf.alignment = align
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    pf.line_spacing = LINE
    pf.first_line_indent = Pt(0)
    pf.left_indent = Pt(0)
    pf.right_indent = Pt(0)


def fill(cell, lines, align=WD_ALIGN_PARAGRAPH.LEFT, bold=False):
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    cell.text = ""
    first = True
    for ln in lines:
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        if first:
            first = False
        fmt_par(p, align)
        set_run(p.add_run(ln), bold=bold)


def cell_margins(table, left=0.12, right=0.12, top=0.05, bottom=0.05):
    tblPr = table._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for tag, v in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        node = OxmlElement(f"w:{tag}")
        node.set(qn("w:w"), str(int(Cm(v).twips)))
        node.set(qn("w:type"), "dxa")
        mar.append(node)
    tblPr.append(mar)


def borders(table, sz=4):
    tblPr = table._tbl.tblPr
    bd = OxmlElement("w:tblBorders")
    for tag in ("top", "left", "bottom", "right", "insideH", "insideV"):
        node = OxmlElement(f"w:{tag}")
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), str(sz))
        node.set(qn("w:space"), "0")
        node.set(qn("w:color"), "000000")
        bd.append(node)
    tblPr.append(bd)


def fixed_layout(table):
    tblPr = table._tbl.tblPr
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)


def row_height(row, cm):
    trPr = row._tr.get_or_add_trPr()
    h = OxmlElement("w:trHeight")
    h.set(qn("w:val"), str(int(Cm(cm).twips)))
    h.set(qn("w:hRule"), "atLeast")
    trPr.append(h)


doc = Document()

# 页面：A4 横向
sec = doc.sections[0]
sec.orientation = WD_ORIENT.LANDSCAPE
sec.page_width, sec.page_height = Cm(29.7), Cm(21.0)
sec.left_margin = sec.right_margin = Cm(1.2)
sec.top_margin = sec.bottom_margin = Cm(1.0)

# 默认样式
style = doc.styles["Normal"]
style.font.name = FONT
style.font.size = SIZE
style.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)

table = doc.add_table(rows=2, cols=6)
table.alignment = WD_TABLE_ALIGNMENT.CENTER
table.autofit = False
fixed_layout(table)
borders(table)
cell_margins(table)

# 列宽
for i, w in enumerate(WIDTHS):
    table.columns[i].width = w
for row in table.rows:
    for i, w in enumerate(WIDTHS):
        row.cells[i].width = w

# 表头
hdr = table.rows[0]
row_height(hdr, 0.9)
for i, text in enumerate(HEADERS):
    fill(hdr.cells[i], [text], WD_ALIGN_PARAGRAPH.CENTER, bold=True)
trPr = hdr._tr.get_or_add_trPr()
th = OxmlElement("w:tblHeader")
trPr.append(th)

# 数据行
body = table.rows[1]
fill(body.cells[0], ["0"], WD_ALIGN_PARAGRAPH.CENTER)
fill(body.cells[1], [""], WD_ALIGN_PARAGRAPH.CENTER)
fill(body.cells[2], [""], WD_ALIGN_PARAGRAPH.CENTER)
fill(body.cells[3], TITLE, WD_ALIGN_PARAGRAPH.CENTER)
fill(body.cells[4], TECH, WD_ALIGN_PARAGRAPH.LEFT)
fill(body.cells[5], [t for _, t in REQ], WD_ALIGN_PARAGRAPH.JUSTIFY)

# 需求描述里的小标题加粗
req_cell = body.cells[5]
for idx, (kind, _) in enumerate(REQ):
    if kind == "h":
        for r in req_cell.paragraphs[idx].runs:
            r.font.bold = True

os.makedirs(os.path.dirname(OUT), exist_ok=True)
doc.save(OUT)
print("saved:", OUT)
print("paras in req cell:", len(req_cell.paragraphs))
