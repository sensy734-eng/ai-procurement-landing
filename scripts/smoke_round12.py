#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 12 全流程演示脚本：一条链从采购申请走到运营看板与智能助手。

为什么要单独一支脚本：
    前 11 支冒烟各管一个模块，每支都只在自己的边界内造数、验数。但毕业设计要演示的是
    **一条完整的业务链**：采购员提申请 → 主管审批 → 转订单 → 双人会签 → 仓管员到货验收
    → 生成入库单 → 确认入库 → 库存与订单回写 → 看板与助手反映出来。
    这条链上任何一环的口径断掉（比如验收合格数没带进入库单、确认入库没回写已交付），
    模块级冒烟都是绿的，只有整链走一遍才看得出来。

链路（12 步，全部用同一批 smk12 造的数据）：
    1  采购员建申请（两行）→ 提交 → 主管审批通过（或签）
    2  申请转订单（同一供应商两行合并一张单）→ 订单提交 → 两名主管会签通过
    3  仓管员到货登记（行1 全合格 100、行2 到 50 其中 10 不合格）→ 提交验收（整单不合格）
    4  按合格量生成入库单（100 + 40）→ 补登库位 → 确认入库
    5  库存增加、订单行已交付回写、履约状态变部分交付、订单金额与明细一致
    6  看板核对：状态分布/月度趋势/供应商排名/采购汇总/库存汇总/库存不足
    7  助手核对：问新单状态能答出该单号且只读；看板查询前后业务数据不变

断言策略：所有看板与助手断言都取「操作前后差值」，不写死绝对值，
这样即便库里还有别的演示单据、回归轮次又造了测试单，断言依然成立。

用法：python scripts/smoke_round12.py
前置：后端 8080 已启动，数据库已导入 db 目录脚本（含 24 运营演示数据）。
      脚本自造 smk12 前缀数据，跑完执行 db/27_clean_smoke_round12.sql 清理。
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

TODAY = date.today().isoformat()
SUFFIX = time.strftime("%H%M%S")
MARK = "smk12 全流程 " + SUFFIX
ORDER_DATE = TODAY
REQUIRE_DATE = date.today().replace(day=28).isoformat() if date.today().day <= 28 else TODAY
WAREHOUSE_ID = 1          # 原料仓
LOCATION_ID = 1           # 原料仓 1 号库位（种子数据）
DEPT_ID = 1101            # 采购部，caigou01 所属部门
YEAR_START = date.today().replace(month=1, day=1).isoformat()
WINDOW = "startDate=%s&endDate=%s" % (YEAR_START, TODAY)   # 看板统一用「本年至今」窗口

# 链路用的两个物料与数量
QTY_A = 100               # 行1：全合格
QTY_B = 50                # 行2：到 50，合格 40、不合格 10
ARRIVE_B_QUALIFIED = 40
UNIT_PRICE_A = 12.5
UNIT_PRICE_B = 30


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
        content = error.read()
        try:
            return error.code, json.loads(content.decode("utf-8"))
        except json.JSONDecodeError:
            return error.code, {"raw": content.decode("utf-8", "ignore")}


def check(name, condition, detail=""):
    global passed
    if condition:
        passed += 1
        print("PASS  " + name)
    else:
        failed.append(name)
        print("FAIL  " + name + ("  -> " + str(detail) if detail else ""))


def num(value):
    return float(value or 0)


def token_of(username):
    status, body = call("POST", "/auth/login", body={"username": username, "password": "123456"})
    assert status == 200 and body.get("code") == "Success", (username, body)
    return body["data"]["token"]


def data_of(body):
    return body.get("data")


def covering_supplier(material, suppliers, token):
    """按供货范围给物料找合规供应商：显式范围命中优先，都没有才退到无范围供应商。"""
    fallback = None
    for supplier in suppliers:
        status, body = call("GET", "/m1/suppliers/%d/scopes" % supplier["id"], token=token)
        if body.get("code") != "Success":
            continue
        scopes = body.get("data") or []
        if not scopes:
            if fallback is None:
                fallback = supplier
            continue
        for scope in scopes:
            if scope.get("scopeType") == 1 and scope.get("scopeId") == material["id"]:
                return supplier
            if scope.get("scopeType") == 2 and scope.get("scopeId") == material.get("categoryId"):
                return supplier
    return fallback


def bucket_map(body):
    return {b["code"]: b for b in (data_of(body) or {}).get("buckets") or []}


def main():
    admin = token_of("admin")
    buyer = token_of("caigou01")
    manager1 = token_of("zhuguan01")
    manager2 = token_of("zhuguan02")
    keeper = token_of("cangku01")

    print("== 0 登录五个角色 ==")
    check("五角色登录（采购员 / 双主管 / 仓管员 / 管理员）",
          all([admin, buyer, manager1, manager2, keeper]))

    # ---------- 基线：操作前的看板与业务快照 ----------
    # 订单总数用主管口径取：admin 不持 m3:order:list（权限矩阵里订单列表归采购员与主管），
    # 看板类接口 admin 有 m12 权限点，两类数据分开取
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=1", token=manager1)
    orders_before = data_of(body)["total"]
    status, body = call("GET", "/m12/charts/status", token=admin)
    buckets_before = bucket_map(body)
    excluded_before = sum(b["count"] for b in (data_of(body) or {}).get("excluded") or [])
    status, body = call("GET", "/m12/charts/trend?%s" % WINDOW, token=admin)
    trend_before = num((data_of(body) or {}).get("totalAmount"))
    status, body = call("GET", "/m12/charts/supplier?topN=50&%s" % WINDOW, token=admin)
    rank_before = {r["supplierId"]: num(r["amount"]) for r in (data_of(body) or {}).get("rows") or []}
    status, body = call("GET", "/m5/stock/alert", token=admin)
    alert_before = {a["materialId"]: num(a["gapQty"]) for a in data_of(body) or []}
    status, body = call("GET", "/m12/summary/purchase?groupBy=supplier&%s" % WINDOW, token=admin)
    sup_summary_before = {r["groupKey"]: num(r["amount"]) for r in data_of(body) or []}
    status, body = call("GET", "/m12/summary/purchase?groupBy=status&%s" % WINDOW, token=admin)
    status_summary_before = {r["groupKey"]: r["orderCount"] for r in data_of(body) or []}
    check("基线读数成功（订单总数 / 五桶 / 趋势总额 / 供应商排名 / 库存预警）",
          isinstance(orders_before, int) and len(buckets_before) == 5 and trend_before > 0,
          (orders_before, len(buckets_before), trend_before))

    # ---------- 1 采购员建申请并提交 ----------
    print("== 1 采购申请：建单 → 明细 → 提交 → 主管审批 ==")
    status, body = call("GET", "/m1/materials/page?pageNo=1&pageSize=100&status=1", token=buyer)
    materials = [m for m in data_of(body)["records"] if m.get("status") == 1]
    status, body = call("GET", "/m1/suppliers/page?pageNo=1&pageSize=50", token=buyer)
    suppliers = data_of(body)["records"]

    # 挑一家「供货范围覆盖 ≥2 个启用物料」的供应商：两行同供应商才会合并成一张订单，
    # 全流程演示只需要一条链，不该因为供应商范围拆成两张单（M1 供货范围是硬校验）
    supplier, mat_a, mat_b = None, None, None
    for sup in suppliers:
        status, body = call("GET", "/m1/suppliers/%d/scopes" % sup["id"], token=buyer)
        scopes = body.get("data") or []
        covered = {sc["scopeId"] for sc in scopes if sc.get("scopeType") == 1}
        mats = [m for m in materials if m["id"] in covered]
        if len(mats) >= 2:
            supplier, mat_a, mat_b = sup, mats[0], mats[1]
            break
    if not supplier:
        # 兜底：没有覆盖两物料的供应商时，退到「单物料 + 一家覆盖它的供应商」，
        # 链条仍能走完，只是明细只有一行（断言按实际行数自适应）
        any_material = materials[0]
        supplier = covering_supplier(any_material, suppliers, buyer)
        mat_a, mat_b = any_material, None
    if not supplier or not mat_a:
        print("SKIP：库内没有可用供应商，无法造全流程数据")
        return 1
    check("取到一家覆盖两个启用物料的供应商（供货范围硬校验）",
          mat_b is not None, (supplier.get("supplierNo"), [mat_a.get("materialNo")]))

    status, body = call("POST", "/m2/requests", token=buyer,
                        body={"deptId": DEPT_ID, "remark": MARK + " 申请单"})
    request_id = data_of(body)
    check("1.1 采购员新建申请单（草稿）", status == 200 and body.get("code") == "Success", body)

    plan = [(mat_a, QTY_A, UNIT_PRICE_A)] + ([(mat_b, QTY_B, UNIT_PRICE_B)] if mat_b else [])
    for material, qty, price in plan:
        status, body = call("POST", "/m2/requests/%d/items" % request_id, token=buyer, body={
            "materialId": material["id"], "quantity": qty, "estPrice": price,
            "suggestSupplierId": supplier["id"], "requireDate": REQUIRE_DATE,
            "ownerType": 1, "remark": MARK + " 明细"})
        check("1.2 新增明细行（%s × %s）" % (material["materialNo"], qty),
              status == 200 and body.get("code") == "Success", body)

    status, body = call("POST", "/m2/requests/%d/submit" % request_id, token=buyer)
    check("1.3 提交申请单（草稿 → 已提交，生成审批任务）",
          body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % request_id, token=buyer)
    check("1.4 提交后生命周期为已提交、审批中",
          data_of(body)["lifecycleStatus"] == 2 and data_of(body)["auditStatus"] == 1, data_of(body))

    status, body = call("POST", "/m2/requests/%d/approve" % request_id, token=manager1)
    check("1.5 主管审批通过（或签）", body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % request_id, token=buyer)
    check("1.6 通过后为已审核 + 审批通过",
          data_of(body)["lifecycleStatus"] == 3 and data_of(body)["auditStatus"] == 2, data_of(body))

    # ---------- 2 转订单并会签 ----------
    print("== 2 申请转订单 → 提交 → 双人会签 ==")
    status, body = call("POST", "/m2/requests/transfer", token=buyer,
                        body={"sources": [{"requestId": request_id, "itemIds": None}]})
    orders = (data_of(body) or {}).get("orders") or []
    check("2.1 转订单成功且同供应商的多行合并为一张单（明细 %d 行）" % len(plan),
          body.get("code") == "Success" and len(orders) == 1
          and orders[0]["itemCount"] == len(plan), data_of(body))
    order_id = orders[0]["orderId"]
    order_no = orders[0]["orderNo"]
    expect_amount = sum(q * p for _, q, p in plan)
    check("2.2 订单金额 = Σ 数量 × 申请单价（%.2f）" % expect_amount,
          abs(num(orders[0]["totalAmount"]) - expect_amount) < 0.01, orders[0])

    status, body = call("POST", "/m3/orders/%d/submit" % order_id, token=buyer)
    check("2.3 订单提交（生成会签任务）", body.get("code") == "Success", body)
    status, body = call("POST", "/m3/orders/%d/approve" % order_id, token=manager1)
    check("2.4 会签第一人通过", body.get("code") == "Success", body)
    status, body = call("GET", "/m3/orders/%d" % order_id, token=buyer)
    check("2.5 会签未齐时订单仍处于审批中",
          data_of(body)["auditStatus"] == 1, data_of(body)["auditStatus"])
    status, body = call("POST", "/m3/orders/%d/approve" % order_id, token=manager2)
    check("2.6 会签第二人通过", body.get("code") == "Success", body)
    status, body = call("GET", "/m3/orders/%d" % order_id, token=buyer)
    detail = data_of(body)
    check("2.7 会签齐后为已审核 + 待交付（履约 1）",
          detail["lifecycleStatus"] == 3 and detail["auditStatus"] == 2
          and detail["fulfillStatus"] == 1, detail)
    item_ids = [i["id"] for i in detail["items"]]
    status, body = call("GET", "/m2/requests/%d" % request_id, token=buyer)
    req_items = data_of(body)["items"]
    check("2.8 申请行已回写已转数量且行状态已转完（M2-4-3）",
          all(num(i["transferredQty"]) == num(i["quantity"]) for i in req_items)
          and all(i["itemStatusName"] == "已转完" for i in req_items),
          [(i["transferredQty"], i["quantity"], i["itemStatusName"]) for i in req_items])

    # ---------- 3 到货登记与验收判定 ----------
    print("== 3 仓管员到货登记 → 验收提交 ==")
    arrival_items = [{"orderItemId": item_ids[0], "arrivalQty": QTY_A,
                      "qualifiedQty": QTY_A, "unqualifiedQty": 0, "batchNo": "B12A" + SUFFIX}]
    if len(item_ids) > 1:
        arrival_items.append({"orderItemId": item_ids[1], "arrivalQty": QTY_B,
                              "qualifiedQty": ARRIVE_B_QUALIFIED,
                              "unqualifiedQty": QTY_B - ARRIVE_B_QUALIFIED,
                              "batchNo": "B12B" + SUFFIX})
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": order_id, "arrivalDate": TODAY, "warehouseId": WAREHOUSE_ID,
        "inspectionMode": 3, "remark": MARK + " 到货", "items": arrival_items})
    check("3.1 一次登记两行（行1 到 100 全合格、行2 到 50 含 10 不合格）",
          body.get("code") == "Success", body)
    inspection_id = data_of(body)

    status, body = call("POST", "/m4/inspections/%d/submit" % inspection_id, token=keeper)
    check("3.2 验收提交", body.get("code") == "Success", body)
    status, body = call("GET", "/m4/inspections/%d" % inspection_id, token=keeper)
    insp = data_of(body)
    check("3.3 守恒：到货 = 合格 + 不合格（逐行）",
          all(num(i["arrivalQty"]) == num(i["qualifiedQty"]) + num(i["unqualifiedQty"])
              for i in insp["items"]),
          [(i["arrivalQty"], i["qualifiedQty"], i["unqualifiedQty"]) for i in insp["items"]])
    expect_result = 2 if len(item_ids) > 1 else 1
    check("3.4 整单结果由明细重算（有不合格未让步 → 不合格；全合格 → 合格）",
          insp["inspectionResult"] == expect_result, insp.get("inspectionResultName"))
    expect_inboundable = QTY_A + (ARRIVE_B_QUALIFIED if len(item_ids) > 1 else 0)
    check("3.5 可入库量只算合格量（%d）" % expect_inboundable,
          abs(num(insp["inboundableQty"]) - expect_inboundable) < 0.01,
          insp.get("inboundableQty"))

    # ---------- 4 生成入库单并确认 ----------
    print("== 4 按合格量生成入库单 → 补登库位 → 确认入库 ==")
    status, body = call("POST", "/m4/inspections/%d/inbound" % inspection_id, token=keeper,
                        body={"inboundDate": TODAY, "remark": MARK + " 入库"})
    check("4.1 按合格量生成入库单草稿", body.get("code") == "Success", body)
    inbound_id = data_of(body)
    status, body = call("GET", "/m5/inbounds/%d" % inbound_id, token=keeper)
    inbound = data_of(body)
    expect_quantities = sorted([QTY_A] + ([ARRIVE_B_QUALIFIED] if len(item_ids) > 1 else []))
    check("4.2 入库明细只含合格量（%s，不合格不入库）" % expect_quantities,
          sorted(num(i["quantity"]) for i in inbound["items"]) == expect_quantities,
          [(i["materialId"], i["quantity"]) for i in inbound["items"]])
    check("4.3 入库单初始为草稿且库位待补登",
          inbound["status"] == 1 and all(i.get("locationId") is None for i in inbound["items"]),
          (inbound["status"], [i.get("locationId") for i in inbound["items"]]))

    status, body = call("PUT", "/m5/inbounds/%d" % inbound_id, token=keeper, body={
        "warehouseId": WAREHOUSE_ID, "inboundDate": TODAY, "remark": MARK + " 入库 补登库位",
        "items": [{"orderItemId": i["orderItemId"], "materialId": i["materialId"],
                   "quantity": num(i["quantity"]), "unitId": i["unitId"],
                   "batchNo": i["batchNo"], "locationId": LOCATION_ID, "ownerType": 1}
                  for i in inbound["items"]]})
    check("4.4 补登库位（草稿可编辑）", body.get("code") == "Success", body)

    status, body = call("GET", "/m5/stock/available?materialId=%d&warehouseId=%d"
                        % (mat_a["id"], WAREHOUSE_ID), token=buyer)
    stock_before = num(data_of(body)["quantity"])
    status, body = call("POST", "/m5/inbounds/%d/confirm" % inbound_id, token=keeper)
    check("4.5 确认入库（库存唯一写入点）", body.get("code") == "Success", body)

    status, body = call("GET", "/m5/stock/available?materialId=%d&warehouseId=%d"
                        % (mat_a["id"], WAREHOUSE_ID), token=buyer)
    stock_after = num(data_of(body)["quantity"])
    check("4.6 库存按合格量增加（+%d）" % QTY_A, abs(stock_after - stock_before - QTY_A) < 0.01,
          (stock_before, stock_after))

    # ---------- 5 订单回写与金额一致性 ----------
    print("== 5 订单已交付回写与履约状态重算 ==")
    status, body = call("GET", "/m3/orders/%d" % order_id, token=buyer)
    detail = data_of(body)
    items = {i["id"]: i for i in detail["items"]}
    check("5.1 行1 已交付 = 100（回写）",
          num(items[item_ids[0]]["deliveredQty"]) == QTY_A, items[item_ids[0]]["deliveredQty"])
    if len(item_ids) > 1:
        check("5.2 行2 已交付 = 40（只算合格量，不合格 10 不回写）",
              num(items[item_ids[1]]["deliveredQty"]) == ARRIVE_B_QUALIFIED,
              items[item_ids[1]]["deliveredQty"])
        check("5.3 行1 履约变全部交付、行2 变部分交付",
              items[item_ids[0]]["itemFulfillStatus"] == 3
              and items[item_ids[1]]["itemFulfillStatus"] == 2,
              [(items[i]["itemFulfillStatus"]) for i in item_ids])
    else:
        check("5.2 单行场景：行1 已交付 = 100",
              num(items[item_ids[0]]["deliveredQty"]) == QTY_A, items[item_ids[0]]["deliveredQty"])
        check("5.3 单行场景：行1 履约变全部交付",
              items[item_ids[0]]["itemFulfillStatus"] == 3, items[item_ids[0]]["itemFulfillStatus"])
    check("5.4 整单履约状态重算为部分交付（2）", detail["fulfillStatus"] == 2,
          detail.get("fulfillStatusName"))
    check("5.5 订单金额仍等于明细金额之和（入库不改金额）",
          abs(num(detail["totalAmount"]) - sum(num(i["amount"]) for i in detail["items"])) < 0.01,
          detail["totalAmount"])

    # ---------- 6 看板核对（全部取前后差值） ----------
    print("== 6 运营看板核对（操作前后差值） ==")
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=1", token=manager1)
    orders_after = data_of(body)["total"]
    check("6.1 订单总数 +1", orders_after == orders_before + 1, (orders_before, orders_after))

    status, body = call("GET", "/m12/charts/status", token=admin)
    buckets_after = bucket_map(body)
    partial_delta = buckets_after["PARTIAL"]["count"] - buckets_before["PARTIAL"]["count"]
    check("6.2 状态分布：部分交付桶 +1（新单已部分入库）",
          partial_delta == 1, (buckets_before["PARTIAL"]["count"],
                               buckets_after["PARTIAL"]["count"]))
    excluded_after = sum(b["count"] for b in (data_of(body) or {}).get("excluded") or [])
    check("6.3 状态分布：不计入的草稿/作废数量未变（新单已审核，不进 excluded）",
          excluded_after == excluded_before, (excluded_before, excluded_after))

    status, body = call("GET", "/m12/charts/trend?%s" % WINDOW, token=admin)
    trend_after = num((data_of(body) or {}).get("totalAmount"))
    check("6.4 月度趋势：年度总额增加 = 新单金额（%.2f）" % expect_amount,
          abs(trend_after - trend_before - expect_amount) < 0.01, (trend_before, trend_after))

    status, body = call("GET", "/m12/charts/supplier?topN=50&%s" % WINDOW, token=admin)
    rows = (data_of(body) or {}).get("rows") or []
    rank_after = {r["supplierId"]: num(r["amount"]) for r in rows}
    check("6.5 供应商排名：该供应商金额增加 = 新单金额",
          abs(rank_after.get(supplier["id"], 0) - rank_before.get(supplier["id"], 0)
              - expect_amount) < 0.01,
          (rank_before.get(supplier["id"]), rank_after.get(supplier["id"])))

    status, body = call("GET", "/m12/summary/purchase?groupBy=supplier&%s" % WINDOW, token=admin)
    summary_rows = data_of(body) or []
    sup_summary_after = {r["groupKey"]: num(r["amount"]) for r in summary_rows}
    key = str(supplier["id"])
    check("6.6 采购汇总（按供应商）：该供应商金额增加 = 新单金额",
          abs(sup_summary_after.get(key, 0) - sup_summary_before.get(key, 0)
              - expect_amount) < 0.01,
          (sup_summary_before.get(key), sup_summary_after.get(key)))

    status, body = call("GET", "/m12/summary/purchase?groupBy=status&%s" % WINDOW, token=admin)
    status_rows = data_of(body) or []
    after_map = {r["groupKey"]: r for r in status_rows}
    grew = [k for k, r in after_map.items()
            if r["orderCount"] - status_summary_before.get(k, 0) == 1]
    check("6.7 采购汇总（按状态）：恰好一条组合 +1，且是「部分交付」那条",
          len(grew) == 1 and "部分交付" in after_map[grew[0]]["groupName"],
          [(k, after_map[k]["groupName"], after_map[k]["orderCount"],
            status_summary_before.get(k, 0)) for k in grew])

    status, body = call("GET", "/m12/summary/stock?groupBy=warehouse", token=admin)
    stock_rows = data_of(body) or []
    wh_row = next((r for r in stock_rows if r["groupKey"] == str(WAREHOUSE_ID)), None)
    check("6.8 库存汇总（按仓库）：本仓库数量 ≥ 本次入库量",
          wh_row is not None and num(wh_row["quantity"]) >= QTY_A + ARRIVE_B_QUALIFIED, wh_row)

    status, body = call("GET", "/m12/charts/stock-alert", token=admin)
    alert_after = {r["materialId"]: num(r["gapQty"]) for r in (data_of(body) or {}).get("rows") or []}
    check("6.9 库存不足：两个物料的缺口不增（本次是入库，方向只能变好或不变）",
          all(alert_after.get(mid, 0) <= alert_before.get(mid, 0) + 0.0001
              for mid in (mat_a["id"], mat_b["id"])),
          (alert_before, alert_after))

    # ---------- 7 智能助手核对 ----------
    print("== 7 智能助手能查到这条新单 ==")
    status, body = call("POST", "/assistant/chat", token=admin,
                        body={"question": "%s 这张订单现在什么状态" % order_no})
    code = body.get("code")
    answer = (data_of(body) or {}).get("answer") or ""
    check("7.1 助手能按单号查到新单（Success）", code == "Success", (code, answer[:60]))
    check("7.2 答案是模板转述：带单号与状态，不改写数字",
          order_no in answer and ("部分交付" in answer or "已审核" in answer), answer[:120])

    status, body = call("GET", "/assistant/audits/stats", token=admin)
    audit_total = num((data_of(body) or {}).get("total"))
    check("7.3 查询审计已落库（total > 0）", audit_total > 0, data_of(body))

    # ---------- 8 只读约束 ----------
    print("== 8 只读约束（看板与助手不写业务表） ==")
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=1", token=manager1)
    check("8.1 看板与助手查询前后订单总数不变",
          data_of(body)["total"] == orders_after, data_of(body)["total"])
    status, body = call("GET", "/m5/stock/available?materialId=%d&warehouseId=%d"
                        % (mat_a["id"], WAREHOUSE_ID), token=buyer)
    check("8.2 看板与助手查询前后库存数量不变",
          abs(num(data_of(body)["quantity"]) - stock_after) < 0.001, data_of(body)["quantity"])

    print("\n" + "=" * 60)
    if failed:
        print("全流程演示结果：%d 通过，%d 失败" % (passed, len(failed)))
        for name in failed:
            print("  FAIL " + name)
        return 1
    print("全流程演示结果：%d 通过，0 失败" % passed)
    print("演示链路：申请 %s → 订单 %s → 验收 %s → 入库 %s"
          % (request_id, order_no, inspection_id, inbound_id))
    print("清理：mysql -uroot procurement_db < db/27_clean_smoke_round12.sql")
    return 0


if __name__ == "__main__":
    sys.exit(main())
