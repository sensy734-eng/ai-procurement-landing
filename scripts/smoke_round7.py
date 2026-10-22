#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 7 冒烟测试：M5 采购入库与库存。

覆盖功能点（17 点）：
    M5-1-1 新建入库单（关联单/手工单，编辑）
    M5-1-2 入库单列表与检索（筛选 + 数据范围口径）
    M5-1-3 入库配额校验（订单量 − 累计已生成，草稿也占配额）
    M5-1-4 作废入库单（冲减库存 + 回退已交付）
    M5-1-5 入库单溯源（入库单 → 订单 → 申请单）
    M5-2-1 批次号规则生成（B + yyyyMMdd + 3 位流水）
    M5-2-2 库位登记与归属校验（库位必须属于本单仓库）
    M5-2-3 批次追溯（批次 → 入库明细 → 订单 → 供应商）
    M5-3-1 库存余额查询（按物料 + 仓库聚合）
    M5-3-2 可用量查询（数量 − 冻结 − 预留，现算不落库）
    M5-3-3 按归属对象分组查询（公共/项目/工单）
    M5-3-4 库存余额导出（CSV，UTF-8 BOM）
    M5-4-1 入库流水写入（确认/作废同事务，before/after 闭环）
    M5-4-2 库存流水查询（多条件筛选）
    M5-4-3 来源单据回溯（流水 → 入库单）
    M5-4-4 余额与流水对账（双端差集，差异为 0）
    M5-5-1 库存不足预警（可用量 < 安全库存下限）
    M5-5-2 短缺物料统计（按仓库/分类汇总）
    一致性：M5-5-3 重复确认防护、确认写余额+流水+回写 delivered
    （库存唯一写入点 R2）、作废是确认的逆操作。
另含：M4 → M5 跨模块链（验收单生成入库草稿 → 补登库位 → 确认）、
权限边界（采购员不可建单/确认、仓管员不可作废、采购员不可对账与导出、
admin 不授入库单查询）、无 token 401。

用法：python scripts/smoke_round7.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本，
且已执行 db/19_clean_smoke_round6.sql（演示基线：订单 1 行 1 已到货 600、
已生成入库 600，库存物料 1/仓库 1/B20261015 公共 600）。
测试数据落 smk7 前缀备注与当日日期窗，跑完执行
db/20_clean_smoke_round7.sql 复原演示规模。
退出码 0 表示全部通过。
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 每次运行使用唯一后缀，脚本可反复执行
SUFFIX = str(int(time.time()))[-6:]
MARK = "smk7" + SUFFIX
TODAY = time.strftime("%Y-%m-%d")
TODAY_COMPACT = time.strftime("%Y%m%d")
# 演示基线口径（19_clean 执行后）
DEMO_BATCH = "B20261015"          # 演示库存批次
ORDER1 = "CG-DD-20261011-0001"    # 已审核订单
ORDER1_ITEM1_QTY = 1000.0         # 行 1 订单量
ORDER1_ITEM1_DELIVERED = 600.0    # 行 1 已交付（演示入库单确认结果）
ORDER1_ITEM2_QTY = 500.0
ORDER1_ITEM1_INBOUND_LEFT = 400.0  # 行 1 剩余可入（1000 − 600 已生成）
# 本脚本批次号（与规则批次号区分：规则号由服务端生成，自造号走 B7 前缀）
BA = "B7A" + SUFFIX
BB = "B7B" + SUFFIX
BC = "B7C" + SUFFIX
BG = "B7G" + SUFFIX


def call(method, path, token=None, body=None, raw=False):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            content = response.read()
            if raw:
                return response.status, content, dict(response.headers)
            return response.status, json.loads(content.decode("utf-8"))
    except urllib.error.HTTPError as error:
        content = error.read()
        if raw:
            return error.code, content, dict(error.headers)
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


def token_of(username):
    status, body = call("POST", "/auth/login", body={"username": username, "password": "123456"})
    assert status == 200 and body.get("code") == "Success", (username, body)
    return body["data"]["token"]


def f(value):
    """JSON 数字统一转 float 比较（冒烟脚本历史坑：数字字段是 float 不是字符串）。"""
    return float(value)


def main():
    keeper = token_of("cangku01")    # 仓管员：建单/编辑/确认/溯源，仓库范围 1/2
    manager = token_of("zhuguan01")  # 采购主管：作废、库存导出、预警统计
    buyer = token_of("caigou01")     # 采购员：只读查询（入库单/库存/流水）
    admin = token_of("admin")        # 系统管理员：库存查询与对账

    # ================= M5-2-1 批次号规则 =================
    print("== M5-2-1 批次号规则生成 ==")
    status, body = call("GET", "/m5/inbounds/next-batch-no", token=keeper)
    rule_batch = body.get("data")
    check("M5-2-1 批次号规则 B+yyyyMMdd+3位流水",
          status == 200 and body.get("code") == "Success"
          and re.match("^B" + TODAY_COMPACT + r"\d{3}$", rule_batch or "") is not None,
          (status, rule_batch))
    check("M5-2-1 采购员无批次号生成权（挂在编辑权限上）NoPermission",
          call("GET", "/m5/inbounds/next-batch-no", token=buyer)[1].get("code") == "NoPermission")

    # ================= M5-1-1 新建入库单 =================
    print("== M5-1-1 新建入库单 ==")
    # 关联单：订单 1 行 1 入 300、行 2 入 200，登记批次与库位
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 关联单A",
        "items": [
            {"orderItemId": 1, "materialId": 1, "quantity": 300, "unitId": 1001,
             "batchNo": BA, "locationId": 1, "ownerType": 1},
            {"orderItemId": 2, "materialId": 2, "quantity": 200, "unitId": 1003,
             "batchNo": BB, "locationId": 2, "ownerType": 1}
        ]})
    check("M5-1-1 关联单新建（行1入300、行2入200）",
          status == 200 and body.get("code") == "Success", body)
    bill_a = body.get("data")
    status, body = call("GET", "/m5/inbounds/%d" % bill_a, token=keeper)
    da = body["data"]
    check("M5-1-1 新建为草稿态", da["status"] == 1 and da["statusName"] == "草稿", da)
    check("M5-1-1 入库单号按规则生成（CG-RK-日期-流水）",
          da["inboundNo"].startswith("CG-RK-" + TODAY_COMPACT + "-"), da["inboundNo"])
    check("M5-1-1 关联单带订单与供应商快照",
          da["orderNo"] == ORDER1 and bool(da["supplierName"]), da)
    check("M5-1-1 明细带物料名/规格/库位名/批次",
          da["items"][0]["materialName"] == "内六角螺栓"
          and da["items"][1]["materialName"] == "不锈钢板"
          and da["items"][0]["batchNo"] == BA and da["items"][1]["batchNo"] == BB
          and bool(da["items"][0]["locationName"]) and bool(da["items"][1]["locationName"]),
          da["items"])
    check("M5-1-1 明细合计与行数", f(da["totalQty"]) == 500.0 and len(da["items"]) == 2, da["totalQty"])

    # 配额：行 1 已生成 600 + 本单 300 = 900，再入 101 超（草稿也占配额）
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"orderItemId": 1, "materialId": 1, "quantity": 101, "unitId": 1001,
                   "batchNo": "B7X" + SUFFIX, "locationId": 1, "ownerType": 1}]})
    check("M5-1-3 入库不超剩余可入量（行1草稿占配额后仅剩100，入101被拒）",
          body.get("code") == "Unsupported" and "剩余可入量" in body.get("message", ""), body)

    # 手工单：不带订单行（公共入库/补录场景，不受配额约束）
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 手工单C",
        "items": [{"materialId": 3, "quantity": 60, "unitId": 1005,
                   "batchNo": BC, "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 手工单新建（无订单行，补录场景）",
          status == 200 and body.get("code") == "Success", body)
    bill_c = body.get("data")
    status, body = call("GET", "/m5/inbounds/%d" % bill_c, token=keeper)
    dc = body["data"]
    check("M5-1-1 手工单不带订单信息", dc["orderId"] is None and dc["orderNo"] is None, dc)

    # 混合单：一行带订单行一行不带 → 拒
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [
            {"orderItemId": 1, "materialId": 1, "quantity": 1, "unitId": 1001, "ownerType": 1},
            {"materialId": 3, "quantity": 1, "unitId": 1005, "ownerType": 1}
        ]})
    check("M5-1-1 关联单与手工单不能混在一张单里",
          body.get("code") == "InvalidParameter" and "混在一张单" in body.get("message", ""), body)

    # 未审核订单（订单 2 草稿）不可入库
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"orderItemId": 3, "materialId": 4, "quantity": 1, "unitId": 1007,
                   "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 只有已审核订单可以入库（草稿单行3被拒）",
          body.get("code") == "Unsupported" and "已审核" in body.get("message", ""), body)

    # 同一订单行一次登记只能出现一次
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [
            {"orderItemId": 1, "materialId": 1, "quantity": 1, "unitId": 1001, "ownerType": 1},
            {"orderItemId": 1, "materialId": 1, "quantity": 1, "unitId": 1001, "ownerType": 1}
        ]})
    check("M5-1-1 同一订单行一张单只能出现一次",
          body.get("code") == "InvalidParameter" and "只能出现一次" in body.get("message", ""), body)

    # M5-2-2 库位归属校验：库位 3 属于仓库 2，本单仓库 1 → 拒
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 3, "quantity": 1, "unitId": 1005,
                   "locationId": 3, "ownerType": 1}]})
    check("M5-2-2 库位不属于本单仓库被拒",
          body.get("code") == "Unsupported" and "库位不属于本单仓库" in body.get("message", ""), body)

    # 库位不存在
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 3, "quantity": 1, "unitId": 1005,
                   "locationId": 999, "ownerType": 1}]})
    check("M5-2-2 库位不存在被拒", body.get("code") == "Unsupported", body)

    # 明细行完整性：缺单位、数量非正
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 3, "quantity": 1, "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 明细缺计量单位被拒",
          body.get("code") == "InvalidParameter" and "计量单位" in body.get("message", ""), body)
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 3, "quantity": 0, "unitId": 1005,
                   "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 明细入库数量必须大于 0",
          body.get("code") == "InvalidParameter" and "大于 0" in body.get("message", ""), body)

    # ================= M5-1-2 列表与详情 =================
    print("== M5-1-2 入库单列表与检索 ==")
    status, body = call("GET", "/m5/inbounds/page?pageNo=1&pageSize=10", token=keeper)
    page = body["data"]
    nos = [r["inboundNo"] for r in page["records"]]
    check("M5-1-2 列表含新建单与演示单",
          da["inboundNo"] in nos and dc["inboundNo"] in nos and "CG-RK-20261015-0001" in nos, nos)
    check("M5-1-2 列表带仓库名/状态名/合计",
          all(r["warehouseName"] and r["statusName"] for r in page["records"]), page["records"][0])
    status, body = call("GET", "/m5/inbounds/page?keyword=%s" % da["inboundNo"], token=keeper)
    check("M5-1-2 单号关键字筛选",
          body["data"]["total"] == 1 and body["data"]["records"][0]["inboundNo"] == da["inboundNo"],
          body["data"]["total"])
    status, body = call("GET", "/m5/inbounds/page?status=1", token=buyer)
    check("M5-1-2 状态筛选（采购员可查列表，全部为草稿）",
          body["data"]["total"] >= 2 and all(r["status"] == 1 for r in body["data"]["records"]),
          body["data"]["total"])
    status, body = call("GET", "/m5/inbounds/page?warehouseId=2", token=buyer)
    check("M5-1-2 仓库筛选（演示单与新建单都在1号仓，2号仓为空）",
          body["data"]["total"] == 0, body["data"]["total"])
    status, body = call("GET", "/m5/inbounds/page?orderId=1", token=buyer)
    check("M5-1-2 来源订单筛选（演示单 + 关联单A）",
          body["data"]["total"] == 2, body["data"]["total"])

    # 编辑（M5-1-1）：手工单 C 数量 60 → 80，批次改号
    status, body = call("PUT", "/m5/inbounds/%d" % bill_c, token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 手工单C改",
        "items": [{"materialId": 3, "quantity": 80, "unitId": 1005,
                   "batchNo": BC, "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 草稿可编辑（明细整体替换：60 → 80）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m5/inbounds/%d" % bill_c, token=keeper)
    check("M5-1-1 编辑后明细为新行", f(body["data"]["items"][0]["quantity"]) == 80.0
          and body["data"]["remark"] == MARK + " 手工单C改", body["data"]["items"])

    # ================= M5-1-3 确认入库（库存唯一写入点） =================
    print("== M5-1-3 确认入库 ==")
    # 确认前库存未动
    status, body = call("GET", "/m5/inbounds/%d" % bill_a, token=keeper)
    check("M5-4-1 确认前无库存流水（草稿不碰库存表）",
          body["data"]["flows"] == [] and body["data"]["status"] == 1, body["data"]["flows"])

    # 未登记库位的草稿确认被拒（M5-1-3 前置完整性校验）
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 缺库位单",
        "items": [{"materialId": 3, "quantity": 5, "unitId": 1005, "ownerType": 1}]})
    bill_noloc = body.get("data")
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_noloc, token=keeper)
    check("M5-1-3 未登记库位不能确认",
          body.get("code") == "InvalidParameter" and "库位" in body.get("message", ""), body)

    # 确认关联单 A：写余额 + 流水 + 回写 delivered
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_a, token=keeper)
    check("M5-1-3 确认入库（库存唯一写入点）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m5/inbounds/%d" % bill_a, token=keeper)
    da = body["data"]
    check("M5-1-3 确认后状态 2 已确认并记确认人",
          da["status"] == 2 and da["statusName"] == "已确认"
          and da["confirmByName"] == "陈杰" and bool(da["confirmTime"]), da)
    flows_a = da["flows"]
    check("M5-4-1 确认写两条入库流水（before/after 闭环）",
          len(flows_a) == 2 and all(fl["changeType"] == 1 for fl in flows_a)
          and all(f(fl["beforeQty"]) == 0.0 for fl in flows_a)
          and f(flows_a[0]["afterQty"]) == 300.0 and f(flows_a[1]["afterQty"]) == 200.0,
          flows_a)

    # M5-5-3 重复确认防护
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_a, token=keeper)
    check("M5-5-3 重复确认被拒（状态机闸门）",
          body.get("code") == "Unsupported" and "重复确认" in body.get("message", ""), body)

    # R2 后半：回写订单行已交付并重算履约
    status, body = call("GET", "/m3/orders/1", token=buyer)
    items = {i["itemNo"]: i for i in body["data"]["items"]}
    check("M5-1-3 确认回写订单行已交付（行1 600→900、行2 0→200）",
          f(items[1]["deliveredQty"]) == 900.0 and f(items[2]["deliveredQty"]) == 200.0,
          [(i["itemNo"], i["deliveredQty"]) for i in body["data"]["items"]])
    check("M5-1-3 行履约状态重算（均未交满 → 部分交付）",
          items[1]["itemFulfillStatus"] == 2 and items[2]["itemFulfillStatus"] == 2,
          [(i["itemNo"], i["itemFulfillStatus"]) for i in body["data"]["items"]])

    # 库存余额与可用量（M5-3-1 / M5-3-2）
    status, body = call("GET", "/m5/stock/available?materialId=1&warehouseId=1", token=buyer)
    av = body["data"]
    check("M5-3-2 可用量 = 600（演示）+ 300（本次确认）",
          f(av["quantity"]) == 900.0 and f(av["availableQty"]) == 900.0, av)
    status, body = call("GET", "/m5/stock/available?materialId=2&warehouseId=1", token=buyer)
    check("M5-3-2 物料2 可用量 200", f(body["data"]["quantity"]) == 200.0, body["data"])
    status, body = call("GET", "/m5/stock/balance?pageNo=1&pageSize=50", token=buyer)
    rows = body["data"]["records"]
    m1 = [r for r in rows if r["materialId"] == 1]
    check("M5-3-1 余额按物料+仓库聚合（物料1 一行 900，跨批次合计）",
          len(m1) == 1 and f(m1[0]["quantity"]) == 900.0, m1)
    check("M5-3-1 聚合行带批次展开（演示600 + 新批次300）",
          sorted((b["batchNo"], f(b["quantity"])) for b in m1[0]["batches"])
          == [(DEMO_BATCH, 600.0), (BA, 300.0)], m1[0]["batches"])
    m2 = [r for r in rows if r["materialId"] == 2]
    check("M5-3-1 物料2 一行 200（批次展开一行）",
          len(m2) == 1 and f(m2[0]["quantity"]) == 200.0
          and len(m2[0]["batches"]) == 1 and m2[0]["batches"][0]["batchNo"] == BB, m2)
    check("M5-3-1 余额行带可用量现算（数量−冻结−预留）",
          all(f(r["availableQty"]) == f(r["quantity"]) - f(r["frozenQty"]) - f(r["reservedQty"])
              for r in rows), rows)

    # 手工单确认（无订单行：写库存但不回写任何订单交付）
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_c, token=keeper)
    check("M5-1-3 手工单确认（补录场景，不走配额）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m5/inbounds/%d" % bill_c, token=keeper)
    check("M5-4-1 手工单确认写一条入库流水（0 → 80）",
          body["data"]["status"] == 2 and len(body["data"]["flows"]) == 1
          and f(body["data"]["flows"][0]["beforeQty"]) == 0.0
          and f(body["data"]["flows"][0]["afterQty"]) == 80.0, body["data"]["flows"])
    status, body = call("GET", "/m5/stock/available?materialId=3&warehouseId=1", token=buyer)
    check("M5-3-2 手工单入库落库存（物料3 可用 80）",
          f(body["data"]["quantity"]) == 80.0, body["data"])
    status, body = call("GET", "/m3/orders/1", token=buyer)
    items = {i["itemNo"]: i for i in body["data"]["items"]}
    check("M5-1-3 手工单不回写订单交付（无订单行）",
          f(items[1]["deliveredQty"]) == 900.0 and f(items[2]["deliveredQty"]) == 200.0,
          [(i["itemNo"], i["deliveredQty"]) for i in body["data"]["items"]])

    # M5-3-3 归属分组
    status, body = call("GET", "/m5/stock/owner-group?pageNo=1&pageSize=50", token=buyer)
    orows = body["data"]["records"]
    check("M5-3-3 归属分组查询（三种物料均为公共库存 PUBLIC）",
          body.get("code") == "Success" and len(orows) == 3
          and all(r["ownerType"] == 1 and r["ownerNo"] == "PUBLIC" for r in orows)
          and sorted(f(r["quantity"]) for r in orows) == [80.0, 200.0, 900.0],
          [(r["materialId"], r["ownerNo"], r["quantity"]) for r in orows])

    # 已确认单不可编辑
    status, body = call("PUT", "/m5/inbounds/%d" % bill_a, token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 1, "quantity": 1, "unitId": 1001, "ownerType": 1}]})
    check("M5-1-1 已确认单不可编辑",
          body.get("code") == "Unsupported" and "草稿" in body.get("message", ""), body)

    # ================= M5-1-4 作废入库单 =================
    print("== M5-1-4 作废入库单 ==")
    status, body = call("POST", "/m5/inbounds/%d/void" % bill_a, token=manager, body={})
    check("M5-1-4 作废原因必填",
          body.get("code") == "InvalidParameter" and "作废原因必填" in body.get("message", ""), body)
    status, body = call("POST", "/m5/inbounds/%d/void" % bill_c, token=keeper,
                        body={"reason": "越权作废"})
    check("M5-1-4 仓管员无作废权 NoPermission（作废归采购主管）",
          body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m5/inbounds/%d/void" % bill_noloc, token=manager,
                        body={"reason": "草稿不可作废"})
    check("M5-1-4 草稿不可作废",
          body.get("code") == "Unsupported" and "已确认" in body.get("message", ""), body)

    status, body = call("POST", "/m5/inbounds/%d/void" % bill_a, token=manager,
                        body={"reason": MARK + " 录入有误，整单作废"})
    check("M5-1-4 作废已确认入库单（主管）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m5/inbounds/%d" % bill_a, token=keeper)
    da = body["data"]
    check("M5-1-4 作废后状态 3 已作废并记原因",
          da["status"] == 3 and da["statusName"] == "已作废"
          and da["voidReason"] == MARK + " 录入有误，整单作废", da)
    flows_a = da["flows"]
    check("M5-4-1 作废写冲减流水（不删原入库流水，共 4 条）",
          len(flows_a) == 4
          and [fl["changeType"] for fl in flows_a] == [1, 1, 2, 2]
          and f(flows_a[2]["changeQty"]) == -300.0 and f(flows_a[3]["changeQty"]) == -200.0
          and f(flows_a[2]["afterQty"]) == 0.0 and f(flows_a[3]["afterQty"]) == 0.0,
          flows_a)
    status, body = call("POST", "/m5/inbounds/%d/void" % bill_a, token=manager, body={"reason": "重复作废"})
    check("M5-1-4 重复作废被拒", body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_a, token=keeper)
    check("M5-1-3 已作废单不能确认", body.get("code") == "Unsupported" and "作废" in body.get("message", ""), body)

    # 作废逆操作：库存与已交付回退
    status, body = call("GET", "/m5/stock/available?materialId=1&warehouseId=1", token=buyer)
    check("M5-1-4 作废冲减库存（物料1 可用量回 600）",
          f(body["data"]["quantity"]) == 600.0, body["data"])
    status, body = call("GET", "/m3/orders/1", token=buyer)
    items = {i["itemNo"]: i for i in body["data"]["items"]}
    check("M5-1-4 作废回退订单已交付（行1 回600、行2 回0）",
          f(items[1]["deliveredQty"]) == 600.0 and f(items[2]["deliveredQty"]) == 0.0,
          [(i["itemNo"], i["deliveredQty"]) for i in body["data"]["items"]])
    check("M5-1-4 行2 回到未交付态", items[2]["itemFulfillStatus"] == 1,
          items[2]["itemFulfillStatus"])

    # 作废后配额释放：行 1 又可入 400（1000 − 600）
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"orderItemId": 1, "materialId": 1, "quantity": 400, "unitId": 1001,
                   "batchNo": "B7R" + SUFFIX, "locationId": 1, "ownerType": 1}]})
    check("M5-1-3 作废释放入库配额（行1 补满 400 可建单）",
          body.get("code") == "Success", body)
    bill_r = body.get("data")
    status, body = call("POST", "/m5/inbounds", token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"orderItemId": 1, "materialId": 1, "quantity": 1, "unitId": 1001,
                   "batchNo": "B7R2" + SUFFIX, "locationId": 1, "ownerType": 1}]})
    check("M5-1-3 配额补满后再入 1 被拒",
          body.get("code") == "Unsupported" and "剩余可入量 0" in body.get("message", ""), body)
    # 把 R 单改小，给后面的 M4 跨模块链留出配额（编辑按「不含本单」校验）
    status, body = call("PUT", "/m5/inbounds/%d" % bill_r, token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 配额单R改小",
        "items": [{"orderItemId": 1, "materialId": 1, "quantity": 50, "unitId": 1001,
                   "batchNo": "B7R" + SUFFIX, "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 编辑草稿改量释放配额（400 → 50，余 350）",
          status == 200 and body.get("code") == "Success", body)

    # ================= M5-1-5 溯源与 M5-2-3 批次追溯 =================
    print("== M5-1-5 溯源 / M5-2-3 批次追溯 ==")
    status, body = call("GET", "/m5/inbounds/%d/trace" % bill_a, token=keeper)
    tr = body["data"]
    check("M5-1-5 溯源链 入库单 → 订单 → 申请单 → 申请人",
          tr["inboundNo"] == da["inboundNo"] and tr["orderNo"] == ORDER1
          and tr["requestNo"] == "CG-SQ-20261010-0001" and tr["applicantName"] == "张伟"
          and tr["deptName"] == "采购部", tr)
    status, body = call("GET", "/m5/inbounds/%d/trace" % bill_c, token=keeper)
    check("M5-1-5 手工单溯源链上游为 null（不报错）",
          body.get("code") == "Success" and body["data"]["orderNo"] is None
          and body["data"]["requestNo"] is None, body["data"])
    status, body = call("GET", "/m5/stock/batch-trace?batchNo=%s" % BA, token=buyer)
    bt = body["data"]
    check("M5-2-3 批次追溯（批次 → 入库明细 → 订单 → 供应商）",
          bt["batchNo"] == BA and bt["inboundCount"] == 1 and len(bt["rows"]) == 1
          and bt["rows"][0]["inboundNo"] == da["inboundNo"]
          and bt["rows"][0]["orderNo"] == ORDER1
          and bool(bt["rows"][0]["supplierName"])
          and bt["rows"][0]["inboundStatusName"] == "已作废", bt)
    check("M5-2-3 作废单明细也进批次历史（批次是事实）",
          f(bt["rows"][0]["quantity"]) == 300.0, bt["rows"][0])
    status, body = call("GET", "/m5/stock/batch-trace?batchNo=B7NONE", token=buyer)
    check("M5-2-3 批次不存在返回空链",
          body.get("code") == "Success" and body["data"]["rows"] == []
          and body["data"]["inboundCount"] == 0, body["data"])

    # ================= M5-4-2/3/4 流水查询与对账 =================
    print("== M5-4 库存流水 ==")
    status, body = call("GET", "/m5/flows/page?sourceId=%d&pageNo=1&pageSize=10" % bill_a, token=buyer)
    fpage = body["data"]
    check("M5-4-2 按来源单据筛流水（A 单 4 条：2 增 2 减）",
          fpage["total"] == 4 and [r["changeType"] for r in fpage["records"]] == [2, 2, 1, 1],
          [r["changeType"] for r in fpage["records"]])
    check("M5-4-2 流水带来源单摘要（入库单号/订单号/状态）",
          fpage["records"][0]["source"]["inboundNo"] == da["inboundNo"]
          and fpage["records"][0]["source"]["orderNo"] == ORDER1
          and fpage["records"][0]["source"]["inboundStatusName"] == "已作废",
          fpage["records"][0]["source"])
    check("M5-4-2 流水带批次与物料名",
          fpage["records"][0]["batchNo"] == BB and fpage["records"][0]["materialName"] == "不锈钢板",
          fpage["records"][0])
    flow_id = fpage["records"][0]["id"]
    status, body = call("GET", "/m5/flows/%d/source" % flow_id, token=buyer)
    check("M5-4-3 来源单据回溯（流水 → 入库单详情）",
          body.get("code") == "Success" and body["data"]["inboundNo"] == da["inboundNo"], body["data"])
    status, body = call("GET", "/m5/flows/%d/source" % 999999, token=buyer)
    check("M5-4-3 流水不存在 NotFound",
          body.get("code") == "NotFound", body)
    status, body = call("GET", "/m5/flows/page?changeType=1&pageNo=1&pageSize=50", token=buyer)
    check("M5-4-2 按变动类型筛选（演示1 + A单2 + 手工单C 1）",
          body["data"]["total"] == 4, body["data"]["total"])

    # ================= M4 → M5 跨模块链 =================
    print("== M4 → M5 验收生成入库草稿 → 确认 ==")
    status, body = call("GET", "/m4/inspections/arrivable-orders", token=keeper)
    demo_order = next(o for o in body["data"] if o["orderNo"] == ORDER1)
    item1 = next(i for i in demo_order["items"] if i["orderItemNo"] == 1)
    check("M4 选单带剩余到货量（行1 已到600，剩400）",
          f(item1["remainingArrival"]) == 400.0, item1["remainingArrival"])
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo_order["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "inspectionMode": 3, "remark": MARK + " 到货100全合格",
        "items": [{"orderItemId": item1["orderItemId"], "arrivalQty": 100,
                   "qualifiedQty": 100, "unqualifiedQty": 0, "batchNo": BG}]})
    check("M4 到货登记（行1 到100，全合格）",
          status == 200 and body.get("code") == "Success", body)
    ins_id = body.get("data")
    status, body = call("POST", "/m4/inspections/%d/submit" % ins_id, token=keeper)
    check("M4 验收单提交（守恒校验后进入已验收）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m4/inspections/%d" % ins_id, token=keeper)
    ins = body["data"]
    check("M4 验收结果由明细重算（全合格 → 1）",
          ins["status"] == 2 and ins["inspectionResult"] == 1, ins)
    ins_item_id = ins["items"][0]["id"]
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_id, token=keeper, body={
        "inboundDate": TODAY,
        "items": [{"inspectionItemId": ins_item_id, "quantity": 100}]})
    check("M4-3-1 按合格数量生成入库单（草稿）",
          status == 200 and body.get("code") == "Success", body)
    bill_g = body.get("data")
    status, body = call("GET", "/m5/inbounds/%d" % bill_g, token=keeper)
    dg = body["data"]
    check("M4→M5 草稿带来源验收单与订单，批次从验收预登记带入",
          dg["status"] == 1 and dg["inspectionNo"] == ins["inspectionNo"]
          and dg["orderNo"] == ORDER1 and dg["items"][0]["batchNo"] == BG
          and f(dg["items"][0]["quantity"]) == 100.0, dg)
    check("M4→M5 草稿库位置空（M5-2-2 编辑时补登）",
          dg["items"][0]["locationId"] is None, dg["items"][0])
    status, body = call("PUT", "/m5/inbounds/%d" % bill_g, token=keeper, body={
        "warehouseId": 1, "inboundDate": TODAY, "remark": MARK + " 补登库位",
        "items": [{"orderItemId": 1, "materialId": 1, "quantity": 100, "unitId": 1001,
                   "batchNo": BG, "locationId": 1, "ownerType": 1}]})
    check("M5-1-1 编辑验收来源草稿（补登库位）",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_g, token=keeper)
    check("M5-1-3 确认验收来源入库单",
          status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m5/stock/available?materialId=1&warehouseId=1", token=buyer)
    check("M5-1-3 库存 600 + 100 = 700（跨模块链闭环）",
          f(body["data"]["quantity"]) == 700.0, body["data"])
    status, body = call("GET", "/m3/orders/1", token=buyer)
    items = {i["itemNo"]: i for i in body["data"]["items"]}
    check("M5-1-3 行1 已交付 600 + 100 = 700",
          f(items[1]["deliveredQty"]) == 700.0, items[1]["deliveredQty"])

    # ================= M5-4-4 对账 =================
    print("== M5-4-4 余额与流水对账 ==")
    status, body = call("GET", "/m5/flows/reconcile?asOf=%s" % TODAY, token=admin)
    rc = body["data"]
    check("M5-4-4 对账零差异（余额 = 流水累计，5 个键全匹配）",
          rc["totalKeys"] == 5 and rc["diffKeys"] == 0 and rc["matchedKeys"] == 5
          and f(rc["flowTotal"]) == 780.0 and f(rc["balanceTotal"]) == 780.0, rc)
    check("M5-4-4 对账含作废冲减后的零余额批次键",
          any(f(r["balanceQty"]) == 0.0 and f(r["flowQty"]) == 0.0 and r["batchNo"] == BA
              for r in rc["rows"]), [r["batchNo"] for r in rc["rows"]])

    # ================= M5-5-1/2 库存预警 =================
    print("== M5-5 库存预警 ==")
    status, body = call("GET", "/m5/stock/alert", token=buyer)
    alerts = body["data"]
    alert_map = {a["materialNo"]: a for a in alerts}
    check("M5-5-1 预警只含可用量低于下限的启用物料（5 种）",
          len(alerts) == 5 and set(alert_map) == {"M-1002", "M-1004", "M-1005", "M-1006", "M-1007"},
          [(a["materialNo"], a["availableQty"], a["safetyStock"]) for a in alerts])
    check("M5-5-1 物料1 可用700 不低于下限200，不进预警", "M-1001" not in alert_map, list(alert_map))
    check("M5-5-1 缺口 = 下限 − 可用量（M-1006 缺 300）",
          f(alert_map["M-1006"]["gapQty"]) == 300.0 and f(alert_map["M-1002"]["gapQty"]) == 100.0,
          alert_map.get("M-1006"))
    check("M5-5-1 预警按缺口降序", [f(a["gapQty"]) for a in alerts] ==
          sorted([f(a["gapQty"]) for a in alerts], reverse=True), alerts)
    status, body = call("GET", "/m5/stock/alert-stat?groupBy=warehouse", token=manager)
    stat = body["data"]
    check("M5-5-2 按仓库汇总（1号仓 5 种短缺、缺口 560）",
          len(stat) == 1 and stat[0]["warehouseName"] == "原料仓"
          and stat[0]["materialCount"] == 5 and f(stat[0]["gapQty"]) == 560.0, stat)
    status, body = call("GET", "/m5/stock/alert-stat?groupBy=category", token=manager)
    stat2 = body["data"]
    check("M5-5-2 按分类汇总（缺口合计 560）",
          body.get("code") == "Success" and len(stat2) >= 1
          and abs(sum(f(r["gapQty"]) for r in stat2) - 560.0) < 0.001, stat2)

    # ================= M5-3-4 库存导出 =================
    print("== M5-3-4 库存导出 ==")
    status, content, headers = call("GET", "/m5/stock/export", token=manager, raw=True)
    text = content.decode("utf-8-sig")
    check("M5-3-4 导出 CSV（UTF-8 BOM）带头与数据行",
          status == 200 and text.splitlines()[0].startswith("物料编号")
          and "内六角螺栓" in text and "M-1001" in text, text.splitlines()[:2])
    status, body = call("GET", "/m5/stock/export", token=buyer)
    check("M5-3-4 采购员无导出权 NoPermission（导出归主管）",
          body.get("code") == "NoPermission", body)

    # ================= 权限边界 =================
    print("== 权限边界 ==")
    status, body = call("POST", "/m5/inbounds", token=buyer, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 1, "quantity": 1, "unitId": 1001,
                   "locationId": 1, "ownerType": 1}]})
    check("采购员无新建权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m5/inbounds/%d/confirm" % bill_c, token=buyer)
    check("采购员无确认权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("PUT", "/m5/inbounds/%d" % bill_c, token=buyer, body={
        "warehouseId": 1, "inboundDate": TODAY,
        "items": [{"materialId": 3, "quantity": 1, "unitId": 1005,
                   "locationId": 1, "ownerType": 1}]})
    check("采购员无编辑权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m5/flows/reconcile", token=buyer)
    check("采购员无对账权 NoPermission（对账归管理员）", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m5/inbounds/page", token=admin)
    check("admin 不授入库单查询权 NoPermission（清单未授权，非缺陷）",
          body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m5/stock/balance", token=admin)
    check("admin 可查库存余额（清单授权）", body.get("code") == "Success", body)
    status, body = call("GET", "/m5/inbounds/page")
    check("无 token 401", status == 401, status)

    print()
    print("=" * 60)
    print("轮次 7 冒烟结果：%d 通过，%d 失败" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAIL: " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
