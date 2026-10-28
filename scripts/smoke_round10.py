#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 10 冒烟测试：M7 智能助手 / M8 制度与教程问答 / M9 受控数据查询。

覆盖功能点：
    M7-1-1 自然语言输入与提交       M7-1-2 对话历史记录
    M7-1-3 多轮上下文维护（指代）   M7-1-4 数据范围感知的入口提示
    M7-2-1 三类问题分类             M7-2-2 置信度不足时澄清
    M7-2-3 越权请求统一拒绝         M7-2-4 超范围问题统一拒绝
    M7-3-1 解析结果展示             M7-3-2 参数修正
    M7-3-4 重跑与结果对比           M7-4-1 表格化结果
    M7-4-2 来源引用块               M7-4-3 结果码提示
    M8-1-1 稀疏检索                 M8-1-2 向量检索
    M8-1-3 双路结果组织             M8-1-5 条款型问句处理
    M8-2-1 操作步骤检索             M8-2-2 步骤化答案呈现
    M8-2-3 步骤顺序后端规整         M8-2-4 关联功能入口与版本说明
    M8-3-1 强制附加来源引用         M8-3-2 引用内容可展开
    M8-3-3 引用有效性校验           M8-3-4 引用覆盖率统计
    M8-5-1 检索依据不足判定         M8-5-2 拒答话术
    M8-5-3 拒答记录与统计
    M9-1-1 查询意图分类             M9-1-2 意图置信度输出
    M9-2-1 时间范围提取             M9-2-2 实体参数提取
    M9-2-3 数值条件提取             M9-2-4 字段与操作符白名单校验
    M9-3-1 物料名归一               M9-3-2 多候选返回
    M9-3-3 用户确认后执行
    M9-4-1 按角色注入数据范围       M9-4-3 可用量口径计算
    M9-4-4 超期天数计算             M9-4-5 价格类口径计算
    M9-5-1 七种结果码返回           M9-5-2 区分无数据与无此单据
    M9-5-3 结果转述                 M9-5-4 只读约束校验
    M9-5-5 查询审计写入
    M10-5-3 引用有效性校验（轮次 9 遗留，本轮随 M8 引用链路补齐）

口径（与 db 种子一致）：
    演示基线：库存 内六角螺栓 600（1 号仓）；订单 CG-DD-20261011-0001 已审核、
    部分交付、交货期 2026-10-18；知识库文档 2 篇（制度 6 块 / 手册 4 块）。
    可用量 = 数量 − 冻结 − 预留；超期天数按自然日现算；成交均价与参考价严格分离。

权限口径：
    查询与问答 m7:chat:query 授四个内置角色；审计统计 m9:audit:list 只授系统管理员；
    价格类数据另需 m6:stat:view 或 m6:price:view（仓管员不持，越权拒绝）。

用法：python scripts/smoke_round10.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本，
      且已执行 db/22_clean_smoke_round9.sql 与 db/23_clean_smoke_round10.sql
      （会话、消息、审计三表清零，演示基线复原）。
退出码 0 表示全部通过。
"""
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 演示基线
MATERIAL_BOLT = 1          # 内六角螺栓，有库存
MATERIAL_PLATE = 2         # 不锈钢板，无库存
SUPPLIER_HONGDA = 1        # 台州宏达紧固件有限公司
SUPPLIER_HENGXIN = 4       # 台州恒信包装制品厂
ORDER_AUDITED = "CG-DD-20261011-0001"
STOCK_QTY = 600.0
DELIVERY_DATE = date(2026, 10, 18)
OVERDUE_DAYS = (date.today() - DELIVERY_DATE).days
# 知识库演示文档
DOC_POLICY = 1             # 采购管理制度（演示）
DOC_MANUAL = 2             # ERP 采购模块操作手册（演示）


def call(method, path, token=None, body=None):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.loads(error.read().decode("utf-8"))
        except json.JSONDecodeError:
            return error.code, {"raw": error.read().decode("utf-8", "ignore")}


def check(name, condition, detail=""):
    global passed
    if condition:
        passed += 1
        print("PASS  " + name)
    else:
        failed.append(name)
        print("FAIL  " + name + ("  -> " + str(detail) if detail else ""))


def token_of(username):
    status, body = call("POST", "/auth/login", body={"username": username, "password": "123456"})
    assert status == 200 and body.get("code") == "Success", (username, body)
    return body["data"]["token"]


def ask(question, token=None, **extra):
    """提问并返回 (结果码, payload, HTTP 状态)"""
    payload = {"question": question}
    payload.update(extra)
    status, body = call("POST", "/assistant/chat", token=token, body=payload)
    return body.get("code"), (body.get("data") or {}), status


def rejects_of(data):
    card = data.get("card") or {}
    return card.get("rejected") or []


def filters_of(data):
    card = data.get("card") or {}
    return {item.get("code"): item.get("value") for item in (card.get("filters") or [])}


def main():
    admin = token_of("admin")
    buyer = token_of("caigou01")
    manager = token_of("zhuguan01")
    keeper = token_of("cangku01")

    # 只读基线：问答不改业务数据，前后对比订单数与库存量
    _, orders_before = call("GET", "/m3/orders?pageNo=1&pageSize=1", token=admin)
    orders_total_before = (orders_before.get("data") or {}).get("total")

    # ================= M7-1-4 入口提示 =================
    print("== M7-1-4 数据范围感知的入口提示 ==")
    status, body = call("GET", "/assistant/profile", token=admin)
    profile_admin = body.get("data") or {}
    check("M7-1-4 管理员的入口提示声明全范围",
          profile_admin.get("scope", {}).get("all") is True,
          profile_admin.get("scope"))
    check("M7-1-4 入口提示给出示例问句（三类各至少一条）",
          {item.get("route") for item in profile_admin.get("samples") or []} >= {1, 2, 3},
          [item.get("route") for item in profile_admin.get("samples") or []])
    status, body = call("GET", "/assistant/profile", token=buyer)
    profile_buyer = body.get("data") or {}
    check("M7-1-4 采购员的入口提示指明部门范围（采购部）",
          profile_buyer.get("scope", {}).get("all") is False
          and "采购部" in (profile_buyer.get("scope", {}).get("deptNames") or []),
          profile_buyer.get("scope"))
    check("M7-1-4 采购员入口提示标注权限开关（知识问答/数据查询均可用）",
          profile_buyer.get("knowledgeEnabled") is True and profile_buyer.get("dataEnabled") is True,
          profile_buyer)

    # ================= M7-2-1 三类问题分类 =================
    print("== M7-2-1 三类问题分类与路由 ==")
    code, data, _ = ask("采购申请提交后多久内要审批完", admin)
    check("M7-2-1 制度类问句路由到知识类（route=1）", data.get("route") == 1, data.get("route"))
    check("M7-2-1 制度类问句返回 Success", code == "Success", code)
    code, data, _ = ask("怎么新增采购申请", admin)
    check("M7-2-1 操作类问句路由到教程类（route=2）", data.get("route") == 2, data.get("route"))
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    check("M7-2-1 数据类问句路由到数据类（route=3）", data.get("route") == 3, data.get("route"))

    # ================= M7-2-4 / M9-5-4 超范围与只读 =================
    print("== M7-2-4 超范围统一拒绝 / M9-5-4 只读约束 ==")
    code, data, _ = ask("公司年会抽奖规则是什么", admin)
    check("M7-2-4 不属于三类的问题返回 Unsupported", code == "Unsupported", code)
    check("M7-2-4 拒绝时不臆造答案（提示可换问法而非给出内容）",
          "不能臆造" in (data.get("answer") or "") or "不属于" in (data.get("answer") or ""),
          (data.get("answer") or "")[:60])
    code, data, _ = ask("把内六角螺栓的数量改成 800", admin)
    check("M9-5-4 写操作请求返回 Unsupported（智能接口只读）", code == "Unsupported", code)
    check("M9-5-4 写操作拒绝话术说明只做查询与问答",
          "不执行" in (data.get("answer") or ""), (data.get("answer") or "")[:60])

    # ================= M7-1-1 / M7-1-2 会话与历史 =================
    print("== M7-1-1 提交 / M7-1-2 对话历史 ==")
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    session_id = data.get("sessionId")
    check("M7-1-1 提问返回会话 id 与问句消息 id",
          isinstance(session_id, int) and isinstance(data.get("questionMessageId"), int),
          (session_id, data.get("questionMessageId")))
    status, body = call("GET", "/assistant/sessions/%d/messages" % session_id, token=admin)
    messages = body.get("data") or []
    check("M7-1-2 会话消息按序保存（问句 + 回答两条）",
          len(messages) >= 2 and messages[0].get("role") == 1 and messages[1].get("role") == 2,
          [(m.get("role"), m.get("msgType")) for m in messages[:2]])
    check("M7-1-2 回答消息留存结构化结果（resultJson 非空）",
          bool((messages[1].get("resultJson") or "").strip()), messages[1].get("resultJson"))
    status, body = call("GET", "/assistant/sessions", token=admin)
    sessions = body.get("data") or []
    hit = [item for item in sessions if item.get("id") == session_id]
    check("M7-1-2 会话列表可见且标题取问句、消息数=2",
          len(hit) == 1 and hit[0].get("messageCount") == 2
          and hit[0].get("title") == "内六角螺栓还有多少库存",
          hit[0] if hit else sessions[:2])

    # ================= M9-1-1 五类意图 =================
    print("== M9-1-1 五类意图分类 ==")
    cases = [
        ("order_status", "CG-DD-20261011-0001 这张订单现在什么状态"),
        ("delivery_progress", "哪些订单还没交货"),
        ("purchase_price_history", "内六角螺栓最近成交价是多少"),
        ("stock_available", "内六角螺栓还有多少库存"),
        ("supplier_delivery", "台州宏达紧固件有限公司的交付情况怎么样"),
    ]
    for intent_code, question in cases:
        code, data, _ = ask(question, admin)
        check("M9-1-1 意图命中 %s（问句：%s）" % (intent_code, question),
              code == "Success" and data.get("intentCode") == intent_code,
              (code, data.get("intentCode")))
        check("M9-1-2 意图置信度在 (0,1] 区间（%s）" % intent_code,
              data.get("confidence") is not None and 0 < float(data["confidence"]) <= 1,
              data.get("confidence"))

    # ================= M9-5-1 / M9-5-2 结果码 =================
    print("== M9-5-1 七种结果码 / M9-5-2 无数据与无此单据 ==")
    code, data, _ = ask("CG-DD-20990101-9999 这张订单现在什么状态", admin)
    check("M9-5-2 单据号不存在返回 NotFound（不返回 NoData）", code == "NotFound", code)
    check("M9-5-2 NotFound 话术指出编号可能错误或超出数据范围",
          "没有找到订单号" in (data.get("answer") or ""), (data.get("answer") or "")[:60])
    code, data, _ = ask("不锈钢板还有多少库存", admin)
    check("M9-5-2 条件无匹配返回 NoData", code == "NoData", code)
    code, data, _ = ask("内六角螺栓最近成交价是多少", keeper)
    check("M7-2-3 仓管员查历史价格被拒（NoPermission，需 m6:stat:view 或 m6:price:view）",
          code == "NoPermission" and "m6:" in (data.get("answer") or ""), (code, data.get("answer")))
    check("M7-2-3 越权拒绝不返回任何行数据",
          (data.get("table") or {}).get("rows") in (None, []), data.get("table"))

    # ================= M9-2 参数提取 =================
    print("== M9-2-1 时间范围 / M9-2-3 数值条件 ==")
    code, data, _ = ask("近 30 天超期未交付的订单", admin)
    card = data.get("card") or {}
    time_range = card.get("timeRange") or {}
    check("M9-2-1 「近 30 天」归一为起止日期",
          code == "Success" and time_range.get("from") and time_range.get("to"),
          (code, time_range))
    code, data, _ = ask("金额大于 1 万的订单现在什么状态", admin)
    check("M9-2-3 「金额大于 1 万」提取为金额下限 10000",
          code == "Success" and float(filters_of(data).get("amount_gte") or 0) == 10000.0,
          (code, filters_of(data)))

    # ================= M9-2-4 白名单校验 =================
    print("== M9-2-4 参数白名单校验 ==")
    code, data, _ = ask("近 7 天内六角螺栓的库存还有多少", admin)
    rejected = rejects_of(data)
    check("M9-2-4 库存意图不支持时间条件，被拒并返回 InvalidParameter",
          code == "InvalidParameter" and len(rejected) == 2, (code, len(rejected)))
    check("M9-2-4 被拒参数逐条给出原因（不静默忽略）",
          all(item.get("reason") for item in rejected) and
          {item.get("code") for item in rejected} == {"date_from", "date_to"},
          [(item.get("code"), item.get("reason")) for item in rejected])
    check("M9-2-4 被拒时不给结果表（避免看似完整的错误结论）",
          (data.get("table") or {}).get("rows") in (None, []), data.get("table"))
    check("M9-2-4 提供「去掉被拒条件重查」的澄清选项",
          (data.get("clarify") or {}).get("type") == "param"
          and len((data.get("clarify") or {}).get("options") or []) == 1,
          data.get("clarify"))

    # ================= M9-2-2 / M9-3-1 实体归一 =================
    print("== M9-2-2 实体参数提取 / M9-3-1 实体归一 ==")
    code, data, _ = ask("台州宏达紧固件有限公司的交付情况怎么样", admin)
    check("M9-2-2 供应商全名提取为 supplier_id",
          code == "Success" and filters_of(data).get("supplier_id") == SUPPLIER_HONGDA,
          (code, filters_of(data)))
    code, data, _ = ask("六角螺栓还有多少库存", admin)
    check("M9-3-1 口语化物料名模糊归一到物料档案（六角螺栓 → 内六角螺栓）",
          code == "Success" and filters_of(data).get("material_id") == MATERIAL_BOLT
          and "内六角螺栓" in (data.get("answer") or ""),
          (code, filters_of(data), (data.get("answer") or "")[:40]))

    # ================= M9-3-2 / M9-3-3 实体消歧 =================
    print("== M9-3-2 多候选返回 / M9-3-3 确认后执行 ==")
    code, data, _ = ask("台州的供应商交付怎么样", admin, overrideIntentCode="supplier_delivery")
    clarify = data.get("clarify") or {}
    options = clarify.get("options") or []
    check("M9-3-2 「台州」匹配到多个供应商返回 Ambiguous",
          code == "Ambiguous" and clarify.get("type") == "entity", (code, clarify.get("type")))
    check("M9-3-2 候选清单列出全部匹配项（2 条，带编号）",
          len(options) == 2 and all("SUP-" in (item.get("label") or "") for item in options),
          [item.get("label") for item in options])
    check("M9-3-2 歧义话术说明匹配数量与实体类型",
          "2 个供应商档案" in (data.get("answer") or ""), (data.get("answer") or "")[:60])
    check("M9-3-3 候选带可执行参数（supplier_id），确认即可执行",
          all((item.get("params") or {}).get("supplier_id") for item in options),
          [item.get("params") for item in options])
    code, data, _ = ask("台州的供应商交付怎么样", admin,
                        overrideIntentCode="supplier_delivery", confirmedEntityId=SUPPLIER_HONGDA)
    entities = data.get("entities") or []
    check("M9-3-3 确认实体后按确认结果执行（只剩 1 个实体且命中宏达）",
          code == "Success" and len(entities) == 1
          and entities[0].get("id") == SUPPLIER_HONGDA,
          (code, [(e.get("id"), e.get("name")) for e in entities]))

    # ================= M7-1-3 多轮指代 =================
    print("== M7-1-3 多轮上下文维护 ==")
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    ctx_session = data.get("sessionId")
    code, data, _ = ask("它的价格是多少", admin, sessionId=ctx_session)
    check("M7-1-3 指代问句继承上一轮意图（价格查询）",
          code == "Success" and data.get("intentCode") == "purchase_price_history",
          (code, data.get("intentCode")))
    check("M7-1-3 指代问句继承上一轮实体（material_id=%d）" % MATERIAL_BOLT,
          filters_of(data).get("material_id") == MATERIAL_BOLT, filters_of(data))
    check("M7-1-3 上下文继承在提示区明示（不让用户以为问句里有对象）",
          any("沿用上一轮" in notice for notice in data.get("notices") or []),
          data.get("notices"))

    # ================= M7-3-1 解析结果展示 =================
    print("== M7-3-1 解析结果卡片 ==")
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    card = data.get("card") or {}
    check("M7-3-1 卡片展示意图编码与服务标识",
          card.get("intentCode") == "stock_available" and card.get("serviceCode"),
          (card.get("intentCode"), card.get("serviceCode")))
    check("M7-3-1 卡片展示实体与筛选条件",
          (card.get("entities") or [{}])[0].get("name") == "内六角螺栓"
          and filters_of(data).get("material_id") == MATERIAL_BOLT,
          (card.get("entities"), filters_of(data)))
    check("M7-3-1 卡片按权限开放参数编辑（管理员 retryEnabled）",
          card.get("editable") is True, card.get("editable"))

    # ================= M7-3-2 / M7-3-4 参数修正与重跑 =================
    print("== M7-3-2 参数修正 / M7-3-4 重跑对比 ==")
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    rerun_session = data.get("sessionId")
    code, data, _ = ask("内六角螺栓还有多少库存", admin, sessionId=rerun_session,
                        overrideIntentCode="stock_available",
                        overrideParams={"material_id": MATERIAL_PLATE})
    check("M7-3-2 修正参数后重新执行（结果码随参数变化为 NoData）",
          code == "NoData", code)
    compare = data.get("compare") or {}
    check("M7-3-4 重跑返回上一轮结果码与摘要",
          compare.get("previousResultCode") == "Success" and compare.get("previousSummary"),
          compare)
    check("M7-3-4 重跑列出变更的参数字段（material_id）",
          any("material_id" in item for item in compare.get("changedParams") or []),
          compare.get("changedParams"))
    code, data, _ = ask("内六角螺栓还有多少库存", admin, sessionId=rerun_session,
                        overrideIntentCode="stock_available",
                        overrideParams={"material_id": MATERIAL_BOLT})
    check("M7-3-2 修正回正确物料后重跑成功",
          code == "Success" and filters_of(data).get("material_id") == MATERIAL_BOLT,
          (code, filters_of(data)))
    check("M7-3-2 重跑后实体展示随修正参数校正（不残留旧实体）",
          (data.get("entities") or [{}])[0].get("name") == "内六角螺栓",
          data.get("entities"))
    check("M7-3-2 重跑提示已按修正后条件执行",
          any("修正后的条件" in notice for notice in data.get("notices") or []),
          data.get("notices"))

    # ================= M7-4-1 表格化结果 =================
    print("== M7-4-1 表格化结果 ==")
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    table = data.get("table") or {}
    columns = [item.get("key") for item in table.get("columns") or []]
    row = (table.get("rows") or [{}])[0]
    check("M7-4-1 返回结构化表格（列定义 + 行数据）",
          "materialName" in columns and "availableQty" in columns and len(table.get("rows") or []) == 1,
          (columns, len(table.get("rows") or [])))
    check("M7-4-1 表格与自然语言说明同时给出",
          bool(table.get("caption")) and "可用量" in (data.get("answer") or ""),
          table.get("caption"))

    # ================= M9-4-3 可用量口径 =================
    print("== M9-4-3 可用量 / M9-4-4 超期天数 / M9-4-5 价格口径 ==")
    check("M9-4-3 可用量 = 数量 − 冻结 − 预留（600 − 0 − 0 = 600）",
          float(row.get("quantity")) == STOCK_QTY
          and float(row.get("availableQty")) == STOCK_QTY
          - float(row.get("frozenQty") or 0) - float(row.get("reservedQty") or 0),
          row)
    code, data, _ = ask("哪些订单还没交货", admin)
    check("M9-4-4 超期天数按自然日现算（%d 天）" % OVERDUE_DAYS,
          ("最长超期 %d 天" % OVERDUE_DAYS) in (data.get("answer") or ""),
          (data.get("answer") or "")[:80])
    code, data, _ = ask("内六角螺栓最近成交价是多少", admin)
    answer = data.get("answer") or ""
    check("M9-4-5 历史成交价走 M6 口径（均价 + 样本数 + 来源单号）",
          "加权均价" in answer and "样本" in answer and "来源单号" in answer, answer[:100])
    check("M9-4-5 成交价与参考价严格分离表述（不互相替代）",
          "当前参考价" in answer and "不是同一概念" in answer, answer[:120])

    # ================= M8-1 双路检索 =================
    print("== M8-1-1 稀疏 / M8-1-2 向量 / M8-1-3 双路组织 ==")
    code, data, _ = ask("采购申请提交后多久内要审批完", admin)
    dual = data.get("dualPath") or []
    paths = {item.get("path") for item in dual}
    check("M8-1-3 双路召回结果并列返回（稀疏 + 向量）",
          {"sparse", "vector"} <= paths,
          [item.get("path") for item in dual])
    check("M8-1-1/1-2 两路均带名次、切块与相似度分数",
          all(item.get("rank") and item.get("chunkId") and item.get("score") is not None for item in dual),
          [(item.get("path"), item.get("rank"), item.get("score")) for item in dual[:3]])

    # ================= M8-1-5 条款型问句 =================
    print("== M8-1-5 条款型问句处理 ==")
    answer = data.get("answer") or ""
    check("M8-1-5 条款型问句直接定位条款并引用原文",
          "原文如下（未改写）" in answer and "第三条" in answer, answer[:120])
    refs = data.get("refs") or []
    check("M8-3-1 知识类回答强制附加来源引用（文档名 + 章节 + 切块）",
          bool(refs) and refs[0].get("docName") and refs[0].get("sectionNo")
          and isinstance(refs[0].get("chunkId"), int),
          refs[:1])
    check("M8-3-1 引用指向制度文档而非其他文档",
          refs and refs[0].get("docId") == DOC_POLICY, refs[:1])
    check("M8-3-2 引用带原文片段供展开定位",
          bool((refs[0].get("snippet") or "").strip()), (refs[0].get("snippet") or "")[:60])

    # ================= M8-2 教程问答 =================
    print("== M8-2 教程问答 ==")
    code, data, _ = ask("怎么新增采购申请", admin)
    answer = data.get("answer") or ""
    steps = [line for line in answer.split("\n") if line.strip()[:2] in
             ("1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.")]
    check("M8-2-2 教程类回答以有序步骤呈现（≥4 步）", len(steps) >= 4, steps)
    check("M8-2-3 步骤序号由后端规整为连续 1..n",
          [line.strip().split(".")[0] for line in steps] == [str(i) for i in range(1, len(steps) + 1)],
          [line.strip()[:6] for line in steps])
    check("M8-2-3 步骤内容不带原文序号与承接词（已清理）",
          not any("第一步" in line for line in steps), steps[:2])
    check("M8-2-4 标注对应功能入口与适用文档版本",
          "对应页面" in answer and "适用于文档版本" in answer, answer[-80:])
    check("M8-2-4 教程引用指向操作手册",
          (data.get("refs") or [{}])[0].get("docId") == DOC_MANUAL, data.get("refs")[:1])

    # ================= M8-5 拒答 =================
    print("== M8-5-1 依据不足判定 / M8-5-2 拒答话术 ==")
    code, data, _ = ask("差旅费报销标准是多少", admin)
    check("M8-5-1 知识库无依据时返回 NoData 而非编造",
          code == "NoData", code)
    check("M8-5-2 拒答话术说明未找到依据并建议人工确认",
          "无法给出有依据的回答" in (data.get("answer") or "")
          and "建议人工确认" in (data.get("answer") or ""),
          (data.get("answer") or "")[:100])
    check("M8-5-1 拒答时返回拒答原因（相似度与阈值可核）",
          bool(data.get("refusalReason")) and "阈值" in (data.get("refusalReason") or ""),
          data.get("refusalReason"))
    check("M8-5-1 拒答不给引用（无依据不挂来源）",
          not (data.get("refs") or []), data.get("refs"))

    # ================= M8-3-3 引用有效性校验 =================
    print("== M8-3-3 引用有效性校验 ==")
    code, data, _ = ask("采购申请提交后多久内要审批完", admin)
    ref = (data.get("refs") or [{}])[0]
    status, body = call("POST", "/assistant/citations/verify", token=admin,
                        body={"docId": ref.get("docId"), "chunkId": ref.get("chunkId")})
    verified = body.get("data") or {}
    check("M8-3-3 正确引用校验通过（valid=true 且回报文档版本）",
          verified.get("valid") is True and verified.get("docVersion"),
          verified)
    status, body = call("POST", "/assistant/citations/verify", token=admin,
                        body={"docId": DOC_MANUAL, "chunkId": ref.get("chunkId")})
    mismatch = body.get("data") or {}
    check("M8-3-3 切块不属于所引文档时校验失败并说明原因",
          mismatch.get("valid") is False and mismatch.get("reason") == "切块不属于所引文档",
          mismatch)
    status, body = call("POST", "/assistant/citations/verify", token=admin,
                        body={"docId": ref.get("docId"), "chunkId": 999999})
    missing = body.get("data") or {}
    check("M8-3-3 切块不存在时校验失败（引用失效可被发现）",
          missing.get("valid") is False and missing.get("reason") == "切块不存在或已删除",
          missing)

    # ================= M8-3-4 / M8-5-3 统计 =================
    print("== M8-3-4 引用覆盖率 / M8-5-3 拒答统计 ==")
    status, body = call("GET", "/assistant/citations/coverage", token=admin)
    coverage = body.get("data") or {}
    check("M8-3-4 引用覆盖率统计可查且分母口径写明",
          body.get("code") == "Success" and coverage.get("coverage") is not None
          and "分母" in (coverage.get("note") or ""),
          coverage)
    check("M8-3-4 有效回答数 ≥ 引用数（覆盖率不超过 1）",
          float(coverage.get("coverage") or 0) <= 1.0
          and coverage.get("citedAnswers", 0) <= coverage.get("effectiveAnswers", 0),
          coverage)
    check("M8-5-3 拒答单独计数（不混入覆盖率分母）",
          coverage.get("refusalCount", 0) >= 1, coverage)

    # ================= M9-4-1 数据范围 =================
    print("== M9-4-1 按角色注入数据范围 ==")
    code, data, _ = ask("哪些订单还没交货", buyer)
    check("M9-4-1/4-2 采购员查询返回 Success 且只含本部门单（1 张待交付）",
          code == "Success" and (data.get("table") or {}).get("total") == 1,
          (code, (data.get("table") or {}).get("total")))
    code, data, _ = ask("内六角螺栓还有多少库存", buyer)
    check("M9-4-1 采购员库存查询同样受范围约束且返回自身可见行",
          code == "Success" and len((data.get("table") or {}).get("rows") or []) == 1, code)
    status, body = call("GET", "/assistant/audits/stats", token=buyer)
    check("M9-5-5 审计统计只授系统管理员（采购员 NoPermission）",
          body.get("code") == "NoPermission", body.get("code"))
    status, body = call("GET", "/assistant/audits/stats", token=manager)
    check("M9-5-5 审计统计对采购主管同样不开放",
          body.get("code") == "NoPermission", body.get("code"))
    code, data, _ = ask("内六角螺栓还有多少库存", keeper)
    check("M9-4-1 仓管员库存查询可执行（持 m5:stock:list）", code == "Success", code)

    # ================= M9-5-5 审计写入 =================
    print("== M9-5-5 查询审计写入 ==")
    status, body = call("GET", "/assistant/audits/stats", token=admin)
    stats = body.get("data") or {}
    check("M9-5-5 审计统计可查（总数 > 0）", stats.get("total", 0) > 0, stats.get("total"))
    code_map = stats.get("byResultCode") or {}
    covered = {"Success", "NotFound", "Ambiguous", "NoPermission", "NoData",
               "Unsupported", "InvalidParameter"} - set(code_map.keys())
    check("M9-5-1 七种结果码全部落审计（缺 %s）" % ("无" if not covered else covered),
          not covered, code_map)
    check("M9-5-5 审计按意图分布统计（五类意图均有记录）",
          {case[0] for case in cases} <= set((stats.get("byIntent") or {}).keys()),
          stats.get("byIntent"))
    check("M9-5-5 审计统计含拒答率、无效参数、越权计数",
          stats.get("refusalRate") is not None and stats.get("invalidParamCount", 0) >= 1
          and stats.get("noPermissionCount", 0) >= 1, stats)
    check("M8-3-4 审计统计含引用覆盖率",
          stats.get("citationCoverage") is not None, stats.get("citationCoverage"))

    # ================= 只读约束复核 =================
    print("== M9-5-4 只读约束复核 ==")
    _, orders_after = call("GET", "/m3/orders?pageNo=1&pageSize=1", token=admin)
    orders_total_after = (orders_after.get("data") or {}).get("total")
    check("M9-5-4 全流程问答后订单总数不变（无业务写入）",
          orders_total_before == orders_total_after,
          (orders_total_before, orders_total_after))
    code, data, _ = ask("内六角螺栓还有多少库存", admin)
    row = ((data.get("table") or {}).get("rows") or [{}])[0]
    check("M9-5-4 全流程问答后库存数量不变（600）",
          float(row.get("quantity")) == STOCK_QTY, row.get("quantity"))

    # ================= 认证边界 =================
    print("== 认证边界 ==")
    status, body = call("POST", "/assistant/chat", body={"question": "内六角螺栓还有多少库存"})
    check("无 token 访问问答接口 401", status == 401, status)
    status, body = call("GET", "/assistant/sessions")
    check("无 token 访问会话列表 401", status == 401, status)
    status, body = call("POST", "/assistant/chat", token=admin, body={"question": "   "})
    check("空问句被拒（InvalidParameter，不产生空会话）",
          body.get("code") == "InvalidParameter", body.get("code"))

    print("\n============================================================")
    print("轮次 10 冒烟结果：%d 通过，%d 失败" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAIL " + name)
        sys.exit(1)


main()
