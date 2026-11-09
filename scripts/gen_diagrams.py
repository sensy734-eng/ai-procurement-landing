# -*- coding: utf-8 -*-
"""
流程图生成器：输出矢量 SVG 到 docs/diagrams/

设计约束：
  - 画布统一 1200 宽，高度按内容自适应，适配技术文档与演示；
  - 全部中文标注，字体优先微软雅黑；
  - 图内不放图号与图注（在引用图表的文档中说明），
    只在左上角标一个浅色小标题，便于单独打开时识别。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from svgkit import (Svg, measure, wrap, INK, INK2, LINE,
                    BLUE, BLUE_D, BLUE_L, GREEN, GREEN_L, ORANGE, ORANGE_L,
                    GREY_L, PURPLE)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "diagrams")


def head(svg, s):
    """左上角浅色小标题。"""
    svg.text(24, 30, s, 13, "#9aa9b8")


def step(svg, x, y, w, h, title, sub=None, accent=BLUE, fill="#ffffff",
         fs=12.5, fc=INK):
    """步骤条：左侧色条 + 标题（+副标题）。"""
    svg.rect(x, y, w, h, fill, "#dbe4ee", 6, 1)
    svg.rect(x, y, 3.5, h, accent, "none", 2, 0)
    if sub:
        svg.text(x + w / 2 + 2, y + h / 2 - 2, title, fs, fc, "middle", "600")
        svg.text(x + w / 2 + 2, y + h / 2 + 12, sub, 10, INK2, "middle")
    else:
        svg.text(x + w / 2 + 2, y + h / 2 + fs * 0.36, title, fs, fc, "middle", "600")


# ======================================================================
# 图 1　业务主流程（五环节）
# ======================================================================
def fig1():
    W, H = 1200, 452
    s = Svg(W, H)
    head(s, "图 1　采购管理系统业务主流程（五环节）")

    cols = [
        ("①", "采购申请", "M2", BLUE,
         [("建单与明细", "物料·数量·预算"), ("提交审批", "或签"),
          ("审批通过", "审核结果落库"), ("转采购订单", "同供应商合并")]),
        ("②", "采购订单", "M3", BLUE,
         [("建单", "含价格偏离判定"), ("提交会签", "双人会签"),
          ("交付登记", "分批到货的前置"), ("反审 / 作废", "只禁止不级联")]),
        ("③", "到货验收", "M4", PURPLE,
         [("到货登记", "受配额①约束"), ("合格/不合格判定", "守恒：合格+不合格"),
          ("验收提交", "受配额②约束"), ("让步接收", "唯一放行例外")]),
        ("④", "采购入库", "M5", GREEN,
         [("生成入库单", "只带合格量"), ("补登库位", "逐行必填"),
          ("★ 确认入库", "库存唯一写入点"), ("作废冲减", "写反向流水")]),
        ("⑤", "库存查询", "M5", ORANGE,
         [("库存台账", "物料+仓库+批次"), ("变动流水", "before→after"),
          ("可用量现算", "数量−冻结−预留"), ("预警与对账", "缺口现算")]),
    ]

    CW, GAP, X0 = 192, 56, 8
    HEAD_Y, HEAD_H = 58, 42
    BODY_Y = HEAD_Y + HEAD_H

    for i, (num, name, mod, color, steps) in enumerate(cols):
        x = X0 + i * (CW + GAP)
        # 列头
        s.rect(x, HEAD_Y, CW, HEAD_H, color, "none", 7, 1)
        s.text(x + 14, HEAD_Y + 26, "%s %s" % (num, name), 14.5, "#ffffff", "start", "600")
        s.text(x + CW - 12, HEAD_Y + 26, mod, 10.5, "#ffffff", "end", None, 0.85)
        # 列体
        s.rect(x, BODY_Y, CW, 250, GREY_L, "#e3e9f1", 0, 1)
        for j, (t, sub) in enumerate(steps):
            step(s, x + 10, BODY_Y + 14 + j * 58, CW - 20, 46, t, sub,
                 accent=color, fill="#ffffff")
        # 步骤之间的向下小箭头
        for j in range(len(steps) - 1):
            ya = BODY_Y + 14 + j * 58 + 46
            s.line(x + CW / 2, ya + 1, x + CW / 2, ya + 11, "#b9c7d6", 1.2)

        # 列间箭头（放在列体垂直中部，避开展示文字）
        if i < len(cols) - 1:
            x1 = x + CW + 6
            x2 = x + CW + GAP - 6
            ym = BODY_Y + 125
            s.line(x1, ym, x2, ym, "#41556b", 1.8)
            lab = ["转单", "到货", "合格量", "余额+流水"][i]
            s.text(x + CW + GAP / 2, ym - 9, lab, 10.5, "#41556b", "middle")

    # 底部两条说明
    y0 = 372
    s.rect(8, y0, W - 16, 62, "#fbfcfd", "#e3e9f1", 6, 1)
    t1 = ("审批不是独立环节：", "它是申请单（或签）与订单（会签）内部的步骤，两类单据共用一套审批配置（M11-5）。")
    t2 = ("读与写分离：", "环节④是库存的唯一写入点（确认入库），环节⑤全程只读——查余额与订单，不写任何业务表。")
    for k, (k1, k2) in enumerate((t1, t2)):
        yy = y0 + 24 + k * 22
        s.text(24, yy, k1, 11.5, "#41556b", "start", "600")
        s.text(24 + measure(k1, 11.5) + 3, yy, k2, 11.5, INK2)

    s.save(os.path.join(OUT, "01-业务主流程.svg"))
    return "01-业务主流程.svg"


# ======================================================================
# 图 2　单据链与追溯关系
# ======================================================================
def fig2():
    W, H = 1200, 442
    s = Svg(W, H)
    head(s, "图 2　单据链与追溯关系（五环节的关联键）")

    NW, NH = 220, 78
    XS = [150, 500, 850]   # 三列等宽居中，列间距 130 才放得下长字段名
    Y1, Y2 = 92, 268

    top = [
        ("purchase_request", "采购申请单", "生命周期 + 审核结果", "采购员建单提交"),
        ("purchase_order", "采购订单", "三状态正交（生命周期/审核/履约）", "采购主管会签"),
        ("arrival_inspection", "到货验收单", "到货量 = 合格量 + 不合格量", "仓管员登记验收"),
    ]
    bottom = [
        ("inbound_order", "采购入库单", "草稿 → 已确认 / 已作废", "仓管员确认入库"),
        ("stock_flow", "库存变动流水", "before_qty → after_qty", "系统自动写入"),
        ("stock_balance", "库存余额", "物料+仓库+批次+归属", "系统自动累加"),
    ]

    def node(x, y, en, cn, desc, role, role_above=True):
        s.rect(x, y, NW, NH, "#ffffff", LINE, 8, 1.3)
        s.rect(x + 10, y + 1, NW - 20, 4, BLUE, "none", 2, 0)
        s.text(x + NW / 2, y + 30, en, 13, INK, "middle", "600")
        s.text(x + NW / 2, y + 52, cn, 11.5, INK2, "middle")
        s.text(x + NW / 2, y + 69, desc, 9.6, "#9aa9b8", "middle")
        ry = y - 12 if role_above else y + NH + 18
        s.text(x + NW / 2, ry, role, 10.2, "#7c8fa3", "middle")

    for (en, cn, desc, role), x in zip(top, XS):
        node(x, Y1, en, cn, desc, role, role_above=True)
    for (en, cn, desc, role), x in zip(bottom, XS):
        node(x, Y2, en, cn, desc, role, role_above=False)

    def link(x1, x2, ym, fld, note):
        """水平箭头 + 两行标签（标签在箭头上方，不压线）。"""
        s.line(x1 + 8, ym, x2 - 8, ym, "#41556b", 1.8)
        cx = (x1 + x2) / 2
        ffs = 9.2 if measure(fld, 9.8) > 104 else 9.8
        w = max(measure(fld, ffs), measure(note, 9.2)) + 8
        s.rect(cx - w / 2, ym - 40, w, 34, "#ffffff", "none", 0, 0)
        s.text(cx, ym - 24, fld, ffs, "#41556b", "middle", "600")
        s.text(cx, ym - 11, note, 9.2, "#7c8fa3", "middle")

    # 行 1：申请 → 订单 → 验收
    link(XS[0] + NW, XS[1], Y1 + NH / 2, "source_request_id", "转单：同供应商合并")
    link(XS[1] + NW, XS[2], Y1 + NH / 2, "order_id", "按订单行登记到货")

    # 折返：验收 → 入库单
    xr = XS[2] + NW / 2
    xl = XS[0] + NW / 2
    ymid = (Y1 + NH + Y2) / 2
    s.poly([(xr, Y1 + NH + 2), (xr, ymid), (xl, ymid), (xl, Y2 - 8)], "#41556b", 1.8)
    s.text((xr + xl) / 2, ymid - 9, "inspection_id　验收通过才生成入库单（只带合格量，不合格不入库）",
           9.8, "#41556b", "middle", "600")

    # 行 2：入库单 → 流水 → 余额
    link(XS[0] + NW, XS[1], Y2 + NH / 2, "source_type / source_id", "冲减记负向流水")
    link(XS[1] + NW, XS[2], Y2 + NH / 2, "唯一键维度", "物料 + 仓库 + 批次 + 归属")

    # 底部行级追溯说明
    y0 = 380
    s.rect(8, y0, W - 16, 52, "#fbfcfd", "#e3e9f1", 6, 1)
    s.text(24, y0 + 20, "行级追溯：", 11.2, "#41556b", "start", "600")
    s.text(24 + measure("行级追溯：", 11.2) + 6, y0 + 20,
           "purchase_order_item.source_request_item_id 指回申请行，inbound_item.order_item_id 指回订单行。",
           11.2, INK2)
    s.text(24, y0 + 40, "追溯方向：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("追溯方向：", 10.8) + 8, y0 + 40,
           "stock_flow → inbound_order → arrival_inspection → purchase_order → purchase_request",
           10.4, "#7c8fa3")

    s.save(os.path.join(OUT, "02-单据链追溯.svg"))
    return "02-单据链追溯.svg"




# ======================================================================
# 图 3　订单三状态正交
# ======================================================================
def fig3():
    W, H = 1200, 534
    s = Svg(W, H)
    head(s, "图 3　采购订单的三状态正交模型（生命周期 / 审核结果 / 履约进度）")

    BW, BH, BY = 180, 54, 100
    xs = [80, 320, 560, 800]
    main = [("草稿", "lifecycle = 1"), ("已提交", "lifecycle = 2"),
            ("已审核", "lifecycle = 3"), ("已关闭", "lifecycle = 4")]
    for (t, sub), x in zip(main, xs):
        s.rect(x, BY, BW, BH, BLUE_L, BLUE, 8, 1.4)
        s.text(x + BW / 2, BY + 24, t, 14, BLUE_D, "middle", "600")
        s.text(x + BW / 2, BY + 42, sub, 9.6, "#5a7590", "middle")

    labels = ["提交审批", "审批通过", "整单关闭"]
    for i in range(3):
        x1, x2 = xs[i] + BW, xs[i + 1]
        ym = BY + BH / 2
        s.line(x1 + 4, ym, x2 - 4, ym, "#41556b", 1.8)
        s.text((x1 + x2) / 2, ym - 9, labels[i], 9.6, "#41556b", "middle")

    # 驳回 / 撤回回路
    xa, xb = xs[1] + BW / 2, xs[0] + BW / 2
    ylow = BY + 142
    s.poly([(xa, BY + BH + 2), (xa, ylow), (xb, ylow), (xb, BY + BH + 6)],
           "#c0562b", 1.6, dash="5,4")
    # 文字必须短于横段长度，否则会与右侧竖直段相交（这是排版问题，不是内容问题）
    s.text((xa + xb) / 2, ylow - 11, "驳回：lifecycle 回落 1，audit = 3",
           9.5, "#c0562b", "middle", "600")

    # 作废分支
    vx = xs[2] + BW / 2
    s.rect(xs[2], BY + 152, BW, BH, "#fdf5f2", "#d9a58d", 8, 1.4)
    s.text(xs[2] + BW / 2, BY + 176, "已作废", 14, "#a2512c", "middle", "600")
    s.text(xs[2] + BW / 2, BY + 194, "lifecycle = 5", 9.6, "#b07a5e", "middle")
    s.line(vx, BY + BH + 2, vx, BY + 148, "#c0562b", 1.6)
    s.text(vx + 8, BY + 128, "作废", 9.6, "#c0562b", "start", "600")

    # ---------------- 三字段并行示意
    s.text(24, 330, "同一张订单同时持有三个状态，互不推导——单状态机装不下「已审核 + 部分交付 + 后被关闭」", 12.5, INK, "start", "600")

    rows = [
        ("lifecycle_status", "生命周期", ["1 草稿", "2 已提交", "3 已审核", "4 已关闭", "5 已作废"], 2, BLUE),
        ("audit_status", "审核结果", ["1 未审核", "2 通过", "3 驳回"], 1, GREEN),
        ("fulfill_status", "履约进度", ["1 待交付", "2 部分交付", "3 全部交付", "4 已关闭"], 1, ORANGE),
    ]
    for k, (fld, cn, vals, hi, color) in enumerate(rows):
        y = 366 + k * 52
        s.text(24, y + 14, fld, 10.5, INK, "start", "600")
        s.text(24, y + 30, cn, 10, INK2, "start")
        for j, v in enumerate(vals):
            x = 176 + j * 114
            on = (j == hi)
            s.rect(x, y, 108, 38, color if on else "#ffffff",
                   color if on else "#dbe4ee", 6, 1.3 if on else 1)
            s.text(x + 54, y + 24, v, 10.4, "#ffffff" if on else "#7c8fa3", "middle",
                   "600" if on else "normal")

    s.save(os.path.join(OUT, "03-三状态正交.svg"))
    return "03-三状态正交.svg"


# ======================================================================
# 图 4　审批流程（或签 / 会签）
# ======================================================================
def fig4():
    W, H = 1200, 472
    s = Svg(W, H)
    head(s, "图 4　审批流程：申请单或签与订单会签共用同一套节点配置")

    def box(x, y, w, h, t, sub, color=LINE, fill="#ffffff", tc=INK):
        s.rect(x, y, w, h, fill, color, 8, 1.3)
        s.text(x + w / 2, y + h / 2 - 2, t, 11.8, tc, "middle", "600")
        s.text(x + w / 2, y + h / 2 + 15, sub, 9.4, INK2, "middle")

    def node(x, y, w, h, title, chips, note, color):
        s.rect(x, y, w, h, "#ffffff", color, 8, 1.4)
        s.rect(x, y, w, 26, color, "none", 8, 1)
        s.rect(x, y + 13, w, 13, color, "none", 0, 0)
        s.text(x + w / 2, y + 18, title, 10.8, "#ffffff", "middle", "600")
        n, cw = len(chips), 84
        tot = n * cw + (n - 1) * 10
        for j, c in enumerate(chips):
            cx = x + w / 2 - tot / 2 + j * (cw + 10)
            s.rect(cx, y + 36, cw, 26, "#f5f8fb", "#dbe4ee", 5, 1)
            s.text(cx + cw / 2, y + 53, c, 9.4, "#5a7590", "middle")
        s.text(x + w / 2, y + 80, note, 9.4, color, "middle", "600")

    # ---- 泳道 1：采购申请单（单节点、按角色、或签）
    s.text(24, 66, "申请单审批（M2，bill_type = 1）", 11, BLUE, "start", "600")
    box(140, 93, 176, 66, "采购申请单", "lifecycle = 2　已提交")
    node(392, 74, 300, 104, "节点 1　采购主管", ["zhuguan01", "zhuguan02"],
         "或签：任一通过即推进", BLUE)
    box(792, 93, 176, 66, "审批通过", "audit = 2", GREEN, GREEN_L, "#1f6b4b")
    s.line(320, 126, 386, 126, "#41556b", 1.8)
    s.line(696, 126, 786, 126, "#41556b", 1.8)

    # ---- 泳道 2：采购订单（两节点：主管或签 + 财务会签）
    s.text(24, 222, "订单审批（M3，bill_type = 2）", 11, PURPLE, "start", "600")
    box(140, 249, 176, 66, "采购订单", "lifecycle = 2　已提交")
    node(392, 230, 230, 104, "节点 1　采购主管", ["zhuguan01", "zhuguan02"],
         "或签：任一通过即推进", PURPLE)
    node(652, 230, 230, 104, "节点 2　财务会签", ["按角色", "指定用户"],
         "会签：全部通过才推进", PURPLE)
    box(912, 249, 176, 66, "审批通过", "audit = 2", GREEN, GREEN_L, "#1f6b4b")
    s.line(320, 282, 386, 282, "#41556b", 1.8)
    s.line(622, 282, 648, 282, "#41556b", 1.8)
    s.line(886, 282, 906, 282, "#41556b", 1.8)

    # ---- 底部三行说明
    y0 = 372
    s.rect(8, y0, W - 16, 80, "#fbfcfd", "#e3e9f1", 6, 1)
    lines = [
        ("驳回：", "lifecycle 回落 1（草稿）、audit = 3；不新增第 6 个生命周期值，否则「草稿」与「被驳回的草稿」会分成两种状态、两套筛选。"),
        ("撤回与重提：", "撤回时未处理的任务置 4（失效）；重新提交生成新批次任务——用任务 id 区间判定本次提交产生的任务，不靠时间戳（秒级分不清同秒操作）。"),
        ("审批历史：", "不另建表，按 node_seq 与自增 id 排序即得；任务状态 1 待处理、2 已通过、3 已驳回、4 已失效。"),
    ]
    for k, (k1, k2) in enumerate(lines):
        yy = y0 + 24 + k * 22
        s.text(24, yy, k1, 11, "#41556b", "start", "600")
        s.text(24 + measure(k1, 11) + 3, yy, k2, 11, INK2)

    s.save(os.path.join(OUT, "04-审批流程.svg"))
    return "04-审批流程.svg"


# ======================================================================
# 图 5　确认入库：库存唯一写入点
# ======================================================================
def fig5():
    W, H = 1200, 540
    s = Svg(W, H)
    head(s, "图 5　「确认入库」是库存的唯一写入点（单一事务）")

    # 左：事务内的五个步骤
    s.rect(30, 62, 660, 394, "#fbfcfd", "#8fa3b8", 10, 1.4, dash="7,5")
    s.text(48, 88, "确认入库 —— 单一数据库事务", 12, "#41556b", "start", "600")

    steps = [
        ("① 前置校验", "单据为草稿 / 每行库位已补登 / 入库量未超配额", BLUE),
        ("② 写库存余额 stock_balance", "按「物料 + 仓库 + 批次 + 归属」定位；乐观锁 version 重试，首次插入 DuplicateKey 兜底", GREEN),
        ("③ 写库存流水 stock_flow", "before_qty 取本事务读到的余额版本，after_qty = before + 入库量", GREEN),
        ("④ 回写订单行 delivered_qty", "只加合格量（不合格与未让步的不合格量不计入）", PURPLE),
        ("⑤ 重算整单履约状态", "由明细行重算，不由人工选择：全部交付 / 部分交付", PURPLE),
    ]
    for k, (t, d, color) in enumerate(steps):
        y = 104 + k * 66
        s.rect(48, y, 624, 54, "#ffffff", "#dbe4ee", 7, 1.1)
        s.rect(48, y, 4, 54, color, "none", 2, 0)
        s.text(66, y + 22, t, 11.5, INK, "start", "600")
        s.text(66, y + 40, d, 9.8, INK2, "start")
        if k < 4:
            s.line(360, y + 55, 360, y + 65, "#b9c7d6", 1.2)

    s.line(42, 438, 42, 98, "#c0562b", 1.5, dash="5,4")
    s.line(42, 98, 50, 98, "#c0562b", 1.5, dash="5,4", arrow=False)
    s.text(58, 444, "任一步失败：整体回滚，不留半条数据", 9.8, "#c0562b", "start", "600")

    # 右：作废与冲减
    s.rect(720, 62, 450, 394, "#fdfaf8", "#e3cdc1", 10, 1.4)
    s.text(740, 88, "作废入库单：反向冲减 + 回退（status → 3）", 12, "#a2512c", "start", "600")
    items = [
        ("写反向冲减流水", "change_type = 2，数量取负，同样记 before → after"),
        ("回退已交付量", "delivered_qty 减去该批次数量，重算整单履约"),
        ("释放入库配额", "该单不再占用「订单量 − 累计已生成入库量」"),
        ("余额为负则拒绝", "说明这批货已被后续业务占用，不允许把账做平"),
    ]
    for k, (t, d) in enumerate(items):
        y = 122 + k * 76
        s.circle(748, y, 11, "#f6e3d9", "#d9a58d", 1.2)
        s.text(748, y + 4, str(k + 1), 10, "#a2512c", "middle", "600")
        s.text(768, y + 1, t, 11, "#8d4a29", "start", "600")
        s.mtext(768, y + 19, d, 9.6, INK2, "start", 1.45, maxw=380)

    # 底部：为什么
    y0 = 472
    s.rect(8, y0, W - 16, 52, "#fbfcfd", "#e3e9f1", 6, 1)
    s.text(24, y0 + 21, "为什么必须是唯一写入点：", 11.2, "#41556b", "start", "600")
    s.text(24 + measure("为什么必须是唯一写入点：", 11.2) + 3, y0 + 21,
           "若验收或订单也各自写一次库存，同一批货会被记两遍，账实从此对不上；写入点收敛到一处，对账才有一条确定的基准。",
           11.2, INK2)
    s.text(24, y0 + 40, "对账口径：", 10.6, "#41556b", "start", "600")
    s.text(24 + measure("对账口径：", 10.6) + 3, y0 + 40,
           "逐「物料 + 仓库 + 批次 + 归属」键比对「流水累计」与「当前余额」，差异非零即为异常（归 admin，m5:flow:reconcile）。",
           10.6, "#7c8fa3")

    s.save(os.path.join(OUT, "05-确认入库事务.svg"))
    return "05-确认入库事务.svg"

# ======================================================================
# 图 6　到货验收：双配额与守恒
# ======================================================================
def fig6():
    W, H = 1200, 430
    s = Svg(W, H)
    head(s, "图 6　到货验收的三道校验：到货配额、合格守恒、入库配额")

    NW, NY, NH = 190, 92, 78
    xs = [45, 285, 525, 765, 1005]
    nodes = [
        ("到货登记", "验收单草稿", BLUE, "#ffffff"),
        ("配额①校验", "到货量不超订单", ORANGE, ORANGE_L),
        ("合格 / 不合格判定", "逐明细行登记", BLUE, "#ffffff"),
        ("验收提交", "整单结果由明细重算", BLUE, "#ffffff"),
        ("生成入库单", "只带合格量", GREEN, GREEN_L),
    ]
    for (t, sub, color, fill), x in zip(nodes, xs):
        s.rect(x, NY, NW, NH, fill, color, 8, 1.4)
        s.text(x + NW / 2, NY + 32, t, 12, INK if fill == "#ffffff" else color, "middle", "600")
        s.text(x + NW / 2, NY + 54, sub, 9.6, INK2 if fill == "#ffffff" else color, "middle")

    for i in range(4):
        x1, x2 = xs[i] + NW, xs[i + 1]
        s.line(x1 + 5, NY + NH / 2, x2 - 5, NY + NH / 2, "#41556b", 1.7)

    # 下方四个口径卡
    cards = [
        (45, 208, 552, 96, "配额 ①　到货不得超量", "本次到货量 ≤ 订单量 − 累计已到货量",
         "防止同一订单被重复登记到货；草稿验收单同样占用配额。", ORANGE),
        (621, 208, 534, 96, "守恒　合格 + 不合格 = 到货量", "登记与提交两处都校验",
         "缺一档默认全合格；整单结果由明细重算，让步是整单动作。", BLUE),
        (45, 318, 552, 96, "配额 ②　入库不得超量", "入库量 ≤ 订单量 − 累计已生成入库量",
         "累计含草稿与已确认、不含已作废——草稿也占配额，作废才释放。", GREEN),
        (621, 318, 534, 96, "例外　不合格不入库", "入库量 = 合格量（让步时含被放行的不合格量）",
         "全不合格且未让步的单据生成入库单返回 NoData。", PURPLE),
    ]
    for x, y, w, h, t, f, note, color in cards:
        s.rect(x, y, w, h, "#ffffff", "#dbe4ee", 8, 1.2)
        s.rect(x, y, 4, h, color, "none", 2, 0)
        s.text(x + 18, y + 26, t, 11.6, color, "start", "600")
        s.rect(x + 18, y + 38, w - 36, 26, "#f5f8fb", "none", 4, 0)
        s.text(x + 30, y + 56, f, 10.6, INK, "start")
        s.text(x + 18, y + 84, note, 9.6, INK2, "start")

    s.save(os.path.join(OUT, "06-验收校验.svg"))
    return "06-验收校验.svg"


# ======================================================================
# 图 7　M8 制度与教程问答链路
# ======================================================================
def fig7():
    W, H = 1200, 496
    s = Svg(W, H)
    head(s, "图 7　M8 制度与教程问答的实现链路（零依赖、可离线断言）")

    def nd(x, y, w, h, t, sub, color=BLUE, fill="#ffffff"):
        s.rect(x, y, w, h, fill, color, 8, 1.3)
        s.text(x + w / 2, y + 26, t, 11.4, color if fill != "#ffffff" else INK, "middle", "600")
        for i, ln in enumerate(sub):
            s.text(x + w / 2, y + 44 + i * 13, ln, 9.4, INK2, "middle")

    nd(40, 100, 150, 76, "用户问句", ["制度 / 教程 / 数据类"])
    nd(230, 100, 170, 76, "二元组分词", ["去停用词", "中文按二字切分"])
    # 双路召回
    s.rect(440, 72, 300, 132, "#fbfcfd", "#8fa3b8", 10, 1.3, dash="6,4")
    s.text(590, 92, "双路召回（各取 Top-5，互不预滤）", 10.2, "#41556b", "middle", "600")
    s.rect(456, 104, 128, 88, "#ffffff", "#dbe4ee", 7, 1.1)
    s.text(520, 128, "BM25", 11, INK, "middle", "600")
    s.text(520, 148, "k1 = 1.5", 9.4, INK2, "middle")
    s.text(520, 163, "b = 0.75", 9.4, INK2, "middle")
    s.text(520, 178, "词元命中加权", 9.2, "#9aa9b8", "middle")
    s.rect(596, 104, 128, 88, "#ffffff", "#dbe4ee", 7, 1.1)
    s.text(660, 128, "向量检索", 11, INK, "middle", "600")
    s.text(660, 148, "512 维哈希", 9.4, INK2, "middle")
    s.text(660, 163, "L2 归一 · 余弦", 9.4, INK2, "middle")
    s.text(660, 178, "全量候选", 9.2, "#9aa9b8", "middle")

    nd(790, 100, 150, 76, "RRF 融合", ["K = 60", "只融名次不融分数"])
    nd(970, 100, 190, 76, "阈值判定", ["最高分 ≥ 0.35", "或 词元命中 ≥ 2"])

    s.line(190, 138, 226, 138, "#41556b", 1.7)
    s.line(400, 138, 436, 138, "#41556b", 1.7)
    s.line(740, 138, 786, 138, "#41556b", 1.7)
    s.line(940, 138, 966, 138, "#41556b", 1.7)

    # 拒答分支
    nd(970, 232, 190, 62, "拒答", ["不臆造答案", "写 kb_reject_log"], ORANGE, ORANGE_L)
    s.line(1090, 178, 1090, 228, "#c0562b", 1.5)
    s.text(1098, 208, "低于阈值", 9.2, "#c0562b", "start", "600")

    # 答案装配
    nd(700, 232, 240, 62, "按问句形态分派", ["引用校验：切块必须存在且有效"])
    s.poly([(1010, 178), (1010, 200), (820, 200), (820, 228)], "#41556b", 1.6)
    s.text(830, 214, "通过", 9.2, "#41556b", "start", "600")

    nd(440, 336, 220, 66, "条款型问句", ["第 X 条 / 3.2.1 开头", "原文直引，不改写"], BLUE)
    nd(700, 336, 240, 66, "教程型问句", ["怎么做 / 流程", "规则拆步骤并去章节号"], BLUE)
    nd(970, 336, 190, 66, "数据类问句", ["转 M9 受控查询", "不经本链路"], PURPLE)

    s.line(820, 294, 820, 332, "#41556b", 1.6)                       # → 教程型
    s.poly([(700, 262), (550, 262), (550, 332)], "#41556b", 1.5)      # → 条款型
    s.poly([(930, 294), (930, 312), (1065, 312), (1065, 332)], "#41556b", 1.5)  # → 数据类

    y0 = 428
    s.rect(8, y0, W - 16, 60, "#fbfcfd", "#e3e9f1", 6, 1)
    s.text(24, y0 + 22, "零依赖：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("零依赖：", 10.8) + 4, y0 + 22,
           "分词、BM25、向量与余弦全部自己实现，不引 Elasticsearch、不调外部 embedding 服务——离线可跑、结果可复现，才谈得上「可断言」。",
           10.8, INK2)
    s.text(24, y0 + 42, "切块的前提：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("切块的前提：", 10.8) + 4, y0 + 42,
           "切块尊重条款边界（第 X 条 / 3.2.1 / 一、/（一）不跨切），条款型问句才可能原文直引；切断了，引用就只是关键词拼贴。",
           10.8, INK2)

    s.save(os.path.join(OUT, "07-M8制度问答链路.svg"))
    return "07-M8制度问答链路.svg"


# ======================================================================
# 图 8　M9 受控查询：四道闸
# ======================================================================
def fig8():
    W, H = 1200, 566
    s = Svg(W, H)
    head(s, "图 8　M9 受控自然语言查询：模型不接触数据库，四道闸把关")

    def nd(x, y, w, h, t, sub, color=BLUE, fill="#ffffff", fs=11.4):
        s.rect(x, y, w, h, fill, color, 8, 1.3)
        s.text(x + w / 2, y + 26, t, fs, color if fill != "#ffffff" else INK, "middle", "600")
        for i, ln in enumerate(sub):
            s.text(x + w / 2, y + 44 + i * 13, ln, 9.4, INK2, "middle")

    nd(60, 92, 180, 76, "用户问句", ["「上月 A 类物料花了多少」"])
    nd(300, 92, 180, 76, "意图识别", ["五类意图白名单", "词表权重 + 示例相似度"])
    nd(540, 92, 180, 76, "实体归一", ["别名表 → 真实 id", "多候选返回 Ambiguous"])
    nd(780, 92, 180, 76, "参数提取", ["时间 / 实体 / 数值", "字段与操作符候补"])

    s.line(240, 130, 296, 130, "#41556b", 1.7)
    s.line(480, 130, 536, 130, "#41556b", 1.7)
    s.line(720, 130, 776, 130, "#41556b", 1.7)

    s.poly([(870, 168), (870, 200), (340, 200), (340, 232)], "#41556b", 1.7)

    # 四道闸
    s.rect(60, 236, 560, 214, "#fbfcfd", "#8fa3b8", 10, 1.4, dash="6,4")
    s.text(78, 260, "四道闸：任一不通过即拒绝，不降级猜测", 11.2, "#41556b", "start", "600")
    gates = [
        ("闸 1　意图白名单", "ai_intent 里 enabled = 1 的五类意图之外，一律 Unsupported"),
        ("闸 2　实体已归一", "物料 / 供应商 / 仓库必须映射到真实 id，候选多个则回问澄清"),
        ("闸 3　参数名在规格表", "ai_intent_param 未声明的参数直接丢弃，记入 rejected_params"),
        ("闸 4　操作符与枚举合法", "operator_set 与 enum_values 双白名单校验，越界即拒"),
    ]
    for k, (t, d) in enumerate(gates):
        y = 276 + k * 42
        s.rect(78, y, 524, 34, "#ffffff", "#dbe4ee", 6, 1)
        s.text(92, y + 15, t, 10.6, "#2e5f8a", "start", "600")
        s.text(92, y + 29, d, 9.2, INK2, "start")

    # 被拒分支
    nd(660, 400, 250, 50, "被拒参数写审计", ["rejected_params_json"], ORANGE, ORANGE_L, 10.6)
    s.poly([(400, 450), (400, 472), (785, 472), (785, 452)], "#c0562b", 1.5)
    s.text(420, 486, "闸不通过：不猜、不兜底，把被拒的原始参数原样留档", 9.6, "#c0562b", "start", "600")

    nd(660, 262, 250, 76, "构造受控查询", ["字段 / 操作符 / 时间窗全部来自白名单", "强制注入数据范围（按角色）"])
    s.line(620, 300, 656, 300, "#41556b", 1.7)
    nd(950, 262, 200, 76, "执行查询", ["只读 SQL，受数据范围约束", "不返回越权行"])
    s.line(910, 300, 946, 300, "#41556b", 1.7)

    nd(950, 358, 200, 76, "模板转述", ["只把结果填进模板", "不改任何一个数字"])
    s.line(1050, 338, 1050, 354, "#41556b", 1.7)

    # 底部
    y0 = 500
    s.rect(8, y0, W - 16, 52, "#fbfcfd", "#e3e9f1", 6, 1)
    s.text(24, y0 + 21, "为什么不接大模型：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("为什么不接大模型：", 10.8) + 4, y0 + 21,
           "一版用规则式（词表权重 + 实体先验 + 示例相似度），意图判定与参数提取全部可复现、可离线断言；大模型是替换件，不是前提。",
           10.8, INK2)
    s.text(24, y0 + 41, "审计表就是对照实验的底座：", 10.4, "#41556b", "start", "600")
    s.text(24 + measure("审计表就是对照实验的底座：", 10.4) + 4, y0 + 41,
           "intent / params / rejected_params / result_code 已结构化落库，接模型后可同表比对意图准确率与拒答率。",
           10.4, "#7c8fa3")

    s.save(os.path.join(OUT, "08-M9受控查询链路.svg"))
    return "08-M9受控查询链路.svg"


# ======================================================================
# 图 10　两条 AI 功能的工作流程（双通道）
# ======================================================================
def fig10():
    W, H = 1200, 940
    s = Svg(W, H)
    head(s, "图 10　两条 AI 功能的工作流程：同一入口、两条业务线、每线两档")

    def nd(x, y, w, h, title, lines=(), color=BLUE, fill="#ffffff", tfs=11.6, lfs=9.6):
        s.rect(x, y, w, h, fill, color if fill != "#ffffff" else "#8fa3b8", 8, 1.3)
        s.text(x + w / 2, y + 24, title, tfs, color if fill != "#ffffff" else INK, "middle", "600")
        for i, ln in enumerate(lines):
            s.text(x + w / 2, y + 42 + i * 13, ln, lfs, INK2, "middle")

    def dual(x, y, w, h, model_txt, rule_txt):
        """双通道框：上半模型档、下半兜底档，一眼看出在哪分叉。"""
        top = h / 2
        s.rect(x, y, w, h, "#ffffff", "#8fa3b8", 8, 1.3)
        s.rect(x + 1, y + 1, w - 2, top - 1, BLUE_L, "none", 7, 0)
        s.rect(x + 1, y + top, w - 2, h - top - 1, ORANGE_L, "none", 7, 0)
        s.text(x + 14, y + top / 2 + 4, "模型档", 10.4, BLUE, "start", "600")
        s.text(x + 14, y + top + (h - top) / 2 + 4, "兜底档", 10.4, ORANGE, "start", "600")
        s.text(x + 66, y + top / 2 + 4, model_txt, 9.8, INK2)
        s.text(x + 66, y + top + (h - top) / 2 + 4, rule_txt, 9.8, INK2)

    nd(500, 62, 200, 56, "用户问句", ["AI 助手输入框"])
    nd(500, 148, 200, 72, "M7 入口：装载会话上下文",
       ["意图 / 实体 / 参数", "+ 最近两轮问答原文"], PURPLE)
    nd(500, 250, 200, 72, "路由判定",
       ["模型先判（1.5s 预算）", "未配置 / 失败 / 不自信 → 词表"], BLUE)

    s.line(600, 118, 600, 144, "#41556b", 1.6)
    s.line(600, 220, 600, 246, "#41556b", 1.6)
    s.line(600, 322, 600, 344, "#41556b", 1.6, arrow=False)
    s.poly([(600, 344), (300, 344), (300, 382)], "#41556b", 1.6)
    s.poly([(600, 344), (900, 344), (900, 382)], "#41556b", 1.6)
    s.text(330, 338, "制度 / 教程", 9.8, "#41556b", "start", "600")
    s.text(960, 338, "数据查询", 9.8, "#41556b", "start", "600")

    # ---------------- M8 列 ----------------
    s.rect(40, 386, 520, 36, BLUE_L, "#8fa3b8", 8, 1.2)
    s.text(60, 410, "M8　制度与教程问答", 12.4, BLUE, "start", "600")
    s.text(300, 410, "route = 1 制度　/　2 教程", 9.8, INK2, "start")

    nd(40, 440, 520, 62, "双路召回（各取 Top-5，互不预滤）",
       ["BM25  k1=1.5 · b=0.75　｜　512 维哈希向量 · 余弦"])
    nd(40, 522, 520, 62, "RRF 融合（K=60）→ 拒答判定",
       ["最高分 < 0.35 且 词元命中 < 2 → 拒答，写 kb_reject_log"], ORANGE)
    dual(40, 604, 520, 76,
         "LlmAnswerComposer：基于召回片段成文，标引用编号",
         "模板直引条款原文；教程型按原文顺序拆步骤")
    nd(40, 700, 520, 56, "数字一致性守卫 AnswerFidelityGuard",
       ["答案里每个数字必须在片段原文出现过，否则回退模板"], GREEN)
    nd(40, 776, 520, 52, "答案 + 引用片段（doc / chunk）",
       ["由 M7 组装后返回前端"], GREEN, GREEN_L)

    for y1, y2 in ((502, 522), (584, 604), (680, 700), (756, 776)):
        s.line(300, y1, 300, y2, "#41556b", 1.6)

    # ---------------- M9 列 ----------------
    s.rect(640, 386, 520, 36, "#f2eef9", "#a99cc9", 8, 1.2)
    s.text(660, 410, "M9　受控自然语言查询", 12.4, PURPLE, "start", "600")
    s.text(870, 410, "模型不接触数据库", 9.8, INK2, "start")

    dual(640, 440, 520, 76,
         "一次调用给出：意图 + 实体指称 + 参数 + 时间归一",
         "词表权重 + 正则参数 + 三级实体匹配")
    nd(640, 536, 520, 56, "实体指称 → 撞库 → 主键",
       ["模型只给类型 / 业务编号 / 原话，主键一律由后端撞库得出"], PURPLE)
    nd(640, 612, 520, 64, "四道闸（两档共用，位置不变）",
       ["意图在白名单　/　实体已归一　/　参数名合法　/　操作符与枚举合法"], ORANGE)
    nd(640, 696, 520, 56, "执行：只读 + 数据范围强制注入",
       ["deptScope / warehouseScope 由后端按登录人注入，模型无从干预"])
    nd(640, 772, 520, 56, "转述 + 审计落库（ai_query_audit）",
       ["由 M7 组装后返回前端"], BLUE)

    for y1, y2 in ((516, 536), (592, 612), (676, 696), (752, 772)):
        s.line(900, y1, 900, y2, "#41556b", 1.6)

    y0 = 852
    s.rect(8, y0, W - 16, 62, "#fbfcfd", "#e3e9f1", 6, 1)
    s.text(24, y0 + 22, "分叉在哪：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("分叉在哪：", 10.8) + 4, y0 + 22,
           "模型可用、输出可解析、编码在白名单、置信度达阈值 → 模型档；未配置 / 超时 / 限流 / 输出不合契约 / 编码越界 / 低置信 → 整条走兜底档，用户无感。",
           10.6, INK2)
    s.text(24, y0 + 44, "什么不变：", 10.8, "#41556b", "start", "600")
    s.text(24 + measure("什么不变：", 10.8) + 4, y0 + 44,
           "四道闸、数据范围、只读拒绝位置两档共用——模型说错话的代价是「按错意图查了一次受限数据」，不是绕过权限。",
           10.6, INK2)

    s.save(os.path.join(OUT, "10-两条AI功能流程图.svg"))
    return "10-两条AI功能流程图.svg"


# ======================================================================
# 图 11　整个系统的运行图
# ======================================================================
def fig11():
    W, H = 1200, 820
    s = Svg(W, H)
    head(s, "图 11　整个系统的运行图：请求怎么走、业务怎么跑、AI 挂在哪")

    def layer(x, y, w, h, title, lines=(), color=BLUE, fill="#ffffff", tfs=11.8):
        s.rect(x, y, w, h, fill, color if fill != "#ffffff" else "#8fa3b8", 8, 1.3)
        s.text(x + 20, y + 24, title, tfs, color if fill != "#ffffff" else INK, "start", "600")
        for i, ln in enumerate(lines):
            s.text(x + 20, y + 42 + i * 13, ln, 9.6, INK2, "start")

    layer(40, 66, 600, 58, "浏览器　Vue3 + TS + Vite + Element Plus",
          ["27 个页面 · JWT 登录态 · 菜单与按钮按权限点渲染"])
    layer(40, 148, 600, 58, "接入层　AuthInterceptor → @RequirePermission",
          ["认证在前、鉴权在后；权限点形如　模块:对象:动作"])
    layer(40, 230, 600, 58, "控制层　Controller　183 个接口",
          ["M1~M12 十二个模块，接口清单由 gen_api_list.py 校验"])
    layer(40, 312, 600, 82, "服务层　Service　业务规则都在这一层",
          ["五环节流转 · 三状态正交（lifecycle / audit / fulfill）",
           "库存唯一写入点 = 入库单确认（同事务写余额与流水）",
           "M4 双配额 · 验收守恒 · 参考价版本链"])
    layer(40, 412, 600, 58, "数据层　Mapper　MyBatis-Plus",
          ["逻辑删除 · 乐观锁 version · 分页与条件构造"])
    layer(40, 494, 600, 66, "MySQL　procurement_db",
          ["49 张表 · 718 个字段 · A~H 八个业务域"], GREEN, GREEN_L)

    for y1, y2 in ((124, 148), (206, 230), (288, 312), (394, 412), (470, 494)):
        s.line(340, y1, 340, y2, "#41556b", 1.6)

    # ---------------- AI 子系统 ----------------
    s.text(680, 52, "AI 子系统（旁路接入，模型可关）", 11.2, "#41556b", "start", "600")
    layer(680, 66, 480, 56, "M7　编排入口　/api/assistant/chat",
          ["会话上下文 · 路由判定 · 结果组装"], PURPLE)
    s.rect(680, 140, 228, 64, "#ffffff", "#a99cc9", 8, 1.3)
    s.text(794, 164, "M8　制度与教程问答", 10.8, PURPLE, "middle", "600")
    s.text(794, 182, "双路检索 · 拒答 · 成文", 9.4, INK2, "middle")
    s.rect(932, 140, 228, 64, "#ffffff", "#a99cc9", 8, 1.3)
    s.text(1046, 164, "M9　受控自然语言查询", 10.8, PURPLE, "middle", "600")
    s.text(1046, 182, "理解层 · 四道闸 · 只读", 9.4, INK2, "middle")

    s.line(794, 122, 794, 136, "#41556b", 1.6)
    s.line(1046, 122, 1046, 136, "#41556b", 1.6)

    s.line(794, 204, 794, 222, "#8fa3b8", 1.5, dash="5,4")
    s.line(1046, 204, 1046, 222, "#8fa3b8", 1.5, dash="5,4")
    s.rect(680, 222, 480, 64, "#fbfcfd", "#a99cc9", 8, 1.2, dash="6,4")
    s.text(920, 246, "外部大模型（可选）", 10.8, PURPLE, "middle", "600")
    s.text(920, 264, "DeepSeek · 超时 / 失败 / 输出不合契约即熔断，整条退回规则档", 9.4, INK2, "middle")

    layer(680, 306, 480, 56, "AiChannelGuard　三通道熔断 + 截止预算",
          ["route 1.5s / plan 2.5s / answer 2.5s，只能收紧不能放宽"], ORANGE)

    s.rect(680, 382, 480, 116, "#fbfcfd", "#a99cc9", 8, 1.2, dash="6,4")
    s.text(700, 406, "AI 不进主流程", 10.8, PURPLE, "start", "600")
    notes = [
        "读库经 Service / Mapper，与页面同一条路径，不直连数据库",
        "关掉模型：问答退模板、查询退规则，下单与入库不受影响",
        "任一通道熔断只影响本次回答，不阻断业务操作",
        "四道闸、数据范围、只读拒绝与模型无关，两档共用",
    ]
    for i, ln in enumerate(notes):
        s.text(700, 428 + i * 16, ln, 9.4, INK2, "start")

    # ---------------- 业务主流程 ----------------
    s.rect(8, 574, W - 16, 104, "#fbfcfd", "#e3e9f1", 8, 1)
    s.text(28, 598, "业务主流程（五个环节一条单据链）", 10.8, "#41556b", "start", "600")
    steps = ["采购申请", "采购订单", "到货验收", "采购入库", "库存查询"]
    for i, name in enumerate(steps):
        x = 70 + i * 216
        s.rect(x, 608, 196, 48, "#ffffff", BLUE, 7, 1.2)
        s.text(x + 98, 637, name, 11.6, INK, "middle", "600")
        if i < 4:
            s.line(x + 198, 632, x + 214, 632, "#41556b", 1.6)

    # ---------------- 横切能力 ----------------
    s.text(28, 712, "横切能力（三条，全链路生效）", 10.8, "#41556b", "start", "600")
    cards = [
        ("权限与数据范围", ["deptScope / warehouseScope 由后端注入", "拿不到范围 → 哨兵值失败关闭"]),
        ("审计留痕", ["ai_query_audit 记录问句 / 意图 / 参数 / 结果码", "拒绝的参数单独落 rejected_params"]),
        ("只读边界", ["AI 侧没有任何写接口", "写操作在路由前就被拒绝，与模型无关"]),
    ]
    for i, (title, lines) in enumerate(cards):
        x = 40 + i * 380
        s.rect(x, 722, 360, 62, "#ffffff", "#8fa3b8", 8, 1.2)
        s.text(x + 16, 744, title, 10.8, INK, "start", "600")
        for j, ln in enumerate(lines):
            s.text(x + 16, 762 + j * 13, ln, 9.2, INK2, "start")

    s.save(os.path.join(OUT, "11-系统运行图.svg"))
    return "11-系统运行图.svg"


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    made = []
    for fn in (fig1, fig2, fig3, fig4, fig5, fig6, fig7, fig8, fig10, fig11):
        made.append(fn())
    for m in made:
        print("已生成 docs/diagrams/" + m)
