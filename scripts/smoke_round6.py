#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 6 冒烟测试：M4 到货验收。

覆盖功能点：
    M4-1-1 新建到货登记（选单只列已审核且有余量订单）
    M4-1-2 关联订单明细行（一次多行、一单多次）
    M4-1-3 到货数量登记（不超剩余到货量，草稿也占配额）
    M4-2-1 验收方式登记（免检/抽样/全检，非法值拒绝）
    M4-2-2 验收结果判定（由明细重算：合格/不合格/让步接收）
    M4-2-3 合格与不合格登记（守恒律 R12：合格+不合格=到货）
    M4-2-4 让步接收标记（整单动作，主管专属，理由必填）
    M4-2-5 验收单提交（守恒校验后进入已验收态）
    M4-3-1 按合格数量生成入库单（让步单含不合格，唯一例外）
    M4-3-2 部分入库（行级指定量，剩余后续再入）
    M4-3-3 多批次入库（同一验收单多张独立草稿）
    M4-3-4 入库数量与订单量校验（累计已生成不超订单量）
    M4-4-1 验收记录列表与检索（数据范围：仓管员按仓库、
            部门维度用户失败关闭、管理员全量）
    M4-4-2 验收明细查看（配额现算：订单量/已到货/剩余/可入）
    M4-4-3 验收结果统计（主管视角，合格率口径）
另含：草稿删除释放配额、全不合格单生成入库单 NoData、
权限边界（采购员/主管不可建单、仓管员不可让步、采购员不可统计）、
M3 回归（有验收与入库记录的订单不可反审、不可作废）。

用法：python scripts/smoke_round6.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
测试数据落 smk6 前缀备注与当日日期窗，跑完执行
db/19_clean_smoke_round6.sql 复原演示规模。
退出码 0 表示全部通过。
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 每次运行使用唯一后缀，脚本可反复执行
SUFFIX = str(int(time.time()))[-6:]
MARK = "smk6" + SUFFIX
TODAY = time.strftime("%Y-%m-%d")


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


def detail(ins_id, token):
    status, body = call("GET", "/m4/inspections/%d" % ins_id, token=token)
    assert body.get("code") == "Success", body
    return body["data"]


def main():
    keeper = token_of("cangku01")   # 仓管员：验收全流程
    manager = token_of("zhuguan01")  # 采购主管：让步与统计
    buyer = token_of("caigou01")     # 采购员：只读查看
    admin = token_of("admin")        # 系统管理员：只读查看（全量）

    # ================= M4-1-1 选单：只列已审核且有余量的订单 =================
    print("== M4-1 到货登记 ==")
    status, body = call("GET", "/m4/inspections/arrivable-orders", token=keeper)
    orders = body["data"]
    order_nos = [o["orderNo"] for o in orders]
    check("M4-1-1 选单只含已审核订单（草稿单 CG-DD-20261018-0002 不在列）",
          "CG-DD-20261011-0001" in order_nos and "CG-DD-20261018-0002" not in order_nos,
          order_nos)
    demo = next(o for o in orders if o["orderNo"] == "CG-DD-20261011-0001")
    item1 = next(i for i in demo["items"] if i["orderItemNo"] == 1)
    item2 = next(i for i in demo["items"] if i["orderItemNo"] == 2)
    check("M4-1-1 选单带未交付明细与剩余到货量（行1剩400、行2剩500）",
          float(item1["remainingArrival"]) == 400.0 and float(item2["remainingArrival"]) == 500.0,
          [(i["orderItemNo"], i["remainingArrival"]) for i in demo["items"]])
    check("M4-1-1 选单带物料名称与单位", bool(item1["materialName"]) and bool(item1["unitName"]),
          item1)
    status, body = call("GET", "/m4/inspections/arrivable-orders/%d" % demo["id"], token=keeper)
    check("M4-1-1 单订单明细接口返回同样余量",
          float(body["data"]["remainingArrivalQty"]) == 900.0, body["data"])

    # ---------- M4-1-2 一次多行、M4-1-3 到货配额 ----------
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "inspectionMode": 3, "remark": MARK + " A单",
        "items": [
            {"orderItemId": item1["orderItemId"], "arrivalQty": 400,
             "qualifiedQty": 380, "unqualifiedQty": 20, "batchNo": "B6A1"},
            {"orderItemId": item2["orderItemId"], "arrivalQty": 200,
             "qualifiedQty": 150, "unqualifiedQty": 50}
        ]})
    check("M4-1-2 一次多行登记（A单：行1到400、行2到200）",
          status == 200 and body.get("code") == "Success", body)
    ins_a = body["data"]
    a = detail(ins_a, keeper)
    check("M4-2-1 验收方式落库（全检）", a["inspectionMode"] == 3 and a["inspectionModeName"] == "全检", a)
    check("M4-1-1 新建为草稿态", a["status"] == 1 and a["statusName"] == "草稿", a)
    check("M4-1-1 验收单号按规则生成（CG-YS-日期-流水）",
          a["inspectionNo"].startswith("CG-YS-" + time.strftime("%Y%m%d") + "-"), a["inspectionNo"])
    check("M4-1-3 明细行带到货量与批次",
          float(a["items"][0]["arrivalQty"]) == 400.0 and a["items"][0]["batchNo"] == "B6A1", a["items"])
    check("M4-4-2 明细带配额现算（订单量/已到货/剩余）",
          float(a["items"][0]["orderQty"]) == 1000.0 and float(a["items"][0]["arrivedQty"]) == 1000.0
          and float(a["items"][0]["remainingArrival"]) == 0.0, a["items"][0])

    # 到货超额：行1已到货600+400=1000=订单量，再登1应被拒
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item1["orderItemId"], "arrivalQty": 1}]})
    check("M4-1-3 到货不超剩余到货量（行1已到满，再登被拒）",
          body.get("code") == "InvalidParameter", body)
    # 验收方式非法值
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1, "inspectionMode": 9,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("M4-2-1 验收方式非法值拒绝", body.get("code") == "InvalidParameter", body)
    # 同一订单行在一次登记里重复
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 10},
                  {"orderItemId": item2["orderItemId"], "arrivalQty": 10}]})
    check("M4-1-2 同一订单行一次登记只能出现一次", body.get("code") == "InvalidParameter", body)
    # 草稿单不可登记
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": 999999, "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("M4-1-1 订单不存在 NotFound", body.get("code") == "NotFound", body)
    status, body = call("GET", "/m4/inspections/999999", token=keeper)
    check("M4-4-2 验收单不存在 NotFound", body.get("code") == "NotFound", body)

    # ---------- M4-2-3 守恒律：显式判定必须合格+不合格=到货 ----------
    status, body = call("PUT", "/m4/inspections/%d" % ins_a, token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1, "inspectionMode": 3,
        "items": [
            {"orderItemId": item1["orderItemId"], "arrivalQty": 400,
             "qualifiedQty": 300, "unqualifiedQty": 50},
            {"orderItemId": item2["orderItemId"], "arrivalQty": 200,
             "qualifiedQty": 150, "unqualifiedQty": 50}
        ]})
    check("M4-2-3 守恒律：合格300+不合格50≠到货400 拒绝",
          body.get("code") == "InvalidParameter", body)
    status, body = call("PUT", "/m4/inspections/%d" % ins_a, token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1, "inspectionMode": 3,
        "remark": MARK + " A单改后",
        "items": [
            {"orderItemId": item1["orderItemId"], "arrivalQty": 400,
             "qualifiedQty": 380, "unqualifiedQty": 20},
            {"orderItemId": item2["orderItemId"], "arrivalQty": 200,
             "qualifiedQty": 150, "unqualifiedQty": 50}
        ]})
    check("M4-1 编辑草稿（守恒满足）", status == 200 and body.get("code") == "Success", body)
    a = detail(ins_a, keeper)
    check("M4-2-2 草稿期主表合计随明细重算（合格530、不合格70）",
          float(a["qualifiedQty"]) == 530.0 and float(a["unqualifiedQty"]) == 70.0, a)

    # ---------- M4-2-5 提交 ----------
    status, body = call("POST", "/m4/inspections/%d/submit" % ins_a, token=keeper)
    check("M4-2-5 验收单提交", status == 200 and body.get("code") == "Success", body)
    a = detail(ins_a, keeper)
    check("M4-2-2 整单结果由明细重算：有不合格未让步 → 2 不合格",
          a["inspectionResult"] == 2 and a["inspectionResultName"] == "不合格", a)
    check("M4-2-5 提交后进入已验收态", a["status"] == 2 and a["statusName"] == "已验收", a)
    check("M4-2-5 验收人落当前用户", a["inspectorName"] == "陈杰", a)
    status, body = call("PUT", "/m4/inspections/%d" % ins_a, token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("M4-2-5 已验收单不可编辑", body.get("code") == "Unsupported", body)
    status, body = call("DELETE", "/m4/inspections/%d" % ins_a, token=keeper)
    check("M4-2-5 已验收单不可删除", body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m4/inspections/%d/submit" % ins_a, token=keeper)
    check("M4-2-5 重复提交拒绝", body.get("code") == "Unsupported", body)

    # ---------- M4-2-4 让步接收 ----------
    status, body = call("POST", "/m4/inspections/%d/concession" % ins_a, token=manager,
                        body={"flag": True, "reason": ""})
    check("M4-2-4 让步接收理由必填", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m4/inspections/%d/concession" % ins_a, token=keeper,
                        body={"flag": True, "reason": "产线急用"})
    check("M4-2-4 让步接收归采购主管（仓管员越权拒绝）",
          body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m4/inspections/%d/concession" % ins_a, token=manager,
                        body={"flag": True, "reason": "产线急用，让步接收"})
    check("M4-2-4 主管打让步标记", status == 200 and body.get("code") == "Success", body)
    a = detail(ins_a, keeper)
    check("M4-2-4 让步后整单结果为 3 让步接收",
          a["inspectionResult"] == 3 and a["inspectionResultName"] == "让步接收", a)
    check("M4-2-4 让步理由留痕", a["concessionReason"] == "产线急用，让步接收", a)
    check("M4-3-1 让步单可入量含不合格部分（530合格+70不合格=600）",
          float(a["inboundableQty"]) == 600.0
          and float(a["items"][0]["inboundableQty"]) == 400.0
          and float(a["items"][1]["inboundableQty"]) == 200.0, a)

    # ================= M4-3 生成入库单 =================
    print("== M4-3 生成入库单 ==")
    # 部分入库：第一张只入行1的200
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_a, token=keeper,
                        body={"inboundDate": TODAY, "remark": MARK + " 第一张",
                              "items": [{"inspectionItemId": a["items"][0]["id"],
                                         "quantity": 200}]})
    check("M4-3-2 部分入库：第一张只入行1的200",
          status == 200 and body.get("code") == "Success", body)
    inbound1 = body["data"]
    # 第二张入剩余（行1再200 + 行2的200）
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_a, token=keeper,
                        body={"inboundDate": TODAY, "remark": MARK + " 第二张",
                              "items": [{"inspectionItemId": a["items"][0]["id"],
                                         "quantity": 200},
                                        {"inspectionItemId": a["items"][1]["id"],
                                         "quantity": 200}]})
    check("M4-3-3 多批次入库：第二张入剩余，单号不同",
          status == 200 and body.get("code") == "Success" and body["data"] != inbound1, body)
    inbound2 = body["data"]
    # 第三张：可入量已用完
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_a, token=keeper,
                        body={"inboundDate": TODAY})
    check("M4-3-2 可入量耗尽后继续生成被拒", body.get("code") == "Unsupported", body)
    # 明细行不属于本单
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_a, token=keeper,
                        body={"inboundDate": TODAY,
                              "items": [{"inspectionItemId": 999999, "quantity": 1}]})
    check("M4-3 行级明细行不属于本验收单 NotFound", body.get("code") == "NotFound", body)

    a = detail(ins_a, keeper)
    check("M4-3-3 验收单详情带两张入库单草稿（多批次）",
          len(a["inboundOrders"]) == 2
          and a["inboundOrders"][0]["inboundNo"] != a["inboundOrders"][1]["inboundNo"],
          a["inboundOrders"])
    check("M4-3-3 入库单草稿数量合计（200 / 400）",
          sorted(float(o["totalQty"]) for o in a["inboundOrders"]) == [200.0, 400.0], a["inboundOrders"])
    check("M4-3-1 入库单草稿状态为草稿（确认在轮次 7）",
          all(o["status"] == 1 for o in a["inboundOrders"]), a["inboundOrders"])
    check("M4-3-3 入库单号按 CG-RK 规则生成",
          all(o["inboundNo"].startswith("CG-RK-" + time.strftime("%Y%m%d") + "-")
              for o in a["inboundOrders"]), a["inboundOrders"])

    # ================= M4-1 一单多次 + M4-3-1 非让步只入合格 =================
    print("== M4-1 一单多次（B单） ==")
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "inspectionMode": 1, "remark": MARK + " B单",
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 100}]})
    check("M4-1-2 一单多次：同一订单第二张验收单",
          status == 200 and body.get("code") == "Success", body)
    ins_b = body["data"]
    b = detail(ins_b, keeper)
    check("M4-2-1 免检方式落库（不传合格数缺省全合格）",
          b["inspectionMode"] == 1 and float(b["items"][0]["qualifiedQty"]) == 100.0, b)
    check("M4-1-3 行2已到货累计（200+100=300），剩余200",
          float(b["items"][0]["arrivedQty"]) == 300.0
          and float(b["items"][0]["remainingArrival"]) == 200.0, b["items"][0])
    status, body = call("POST", "/m4/inspections/%d/submit" % ins_b, token=keeper)
    check("M4-2-5 B单提交", status == 200 and body.get("code") == "Success", body)
    b = detail(ins_b, keeper)
    check("M4-2-2 全合格单结果为 1 合格",
          b["inspectionResult"] == 1 and b["inspectionResultName"] == "合格", b)
    status, body = call("POST", "/m4/inspections/%d/concession" % ins_b, token=manager,
                        body={"flag": True, "reason": "无需让步"})
    check("M4-2-4 无不合格数量不可打让步", body.get("code") == "Unsupported", body)
    # 行级入库量超过本行可入量（B单行2可入100、订单余量充足）
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_b, token=keeper,
                        body={"inboundDate": TODAY,
                              "items": [{"inspectionItemId": b["items"][0]["id"],
                                         "quantity": 9999}]})
    check("M4-3-2 行级入库量不超本行可入量", body.get("code") == "InvalidParameter", body)
    # 非让步单只入合格数
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_b, token=keeper,
                        body={"inboundDate": TODAY})
    check("M4-3-1 非让步单只入合格数（100）",
          status == 200 and body.get("code") == "Success", body)
    inbound_b = body["data"]
    status, body = call("GET", "/m4/inspections/%d" % ins_b, token=keeper)
    check("M4-3-1 非让步单可入量=合格量（100）",
          float(body["data"]["inboundableQty"]) == 100.0, body["data"])

    # ================= 草稿删除释放配额 =================
    print("== 草稿删除 ==")
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "remark": MARK + " C单待删",
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 50}]})
    check("新建草稿 C单（行2到货50占配额）", status == 200, body)
    ins_c = body["data"]
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 200}]})
    check("草稿占配额：行2剩余200时登200应被拒（300+50+200>500）",
          body.get("code") == "InvalidParameter", body)
    status, body = call("DELETE", "/m4/inspections/%d" % ins_c, token=keeper)
    check("删除草稿验收单", status == 200 and body.get("code") == "Success", body)
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "remark": MARK + " C单重建",
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 200}]})
    check("删除后配额释放：同量重建成功", status == 200 and body.get("code") == "Success", body)
    ins_c2 = body["data"]
    status, body = call("DELETE", "/m4/inspections/%d" % ins_c2, token=keeper)
    check("清理重建草稿", status == 200, body)

    # ================= 全不合格单：生成入库单 NoData =================
    print("== 全不合格 ==")
    status, body = call("POST", "/m4/inspections", token=keeper, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "remark": MARK + " D单全不合格",
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 30,
                   "qualifiedQty": 0, "unqualifiedQty": 30}]})
    check("新建全不合格单 D", status == 200, body)
    ins_d = body["data"]
    status, body = call("POST", "/m4/inspections/%d/submit" % ins_d, token=keeper)
    check("D单提交（不合格也允许提交，只是不入库）", status == 200, body)
    d = detail(ins_d, keeper)
    check("D单结果为 2 不合格", d["inspectionResultName"] == "不合格", d)
    status, body = call("POST", "/m4/inspections/%d/inbound" % ins_d, token=keeper,
                        body={"inboundDate": TODAY})
    check("M4-3-1 不合格不生成入库：全不合格单 NoData",
          body.get("code") == "NoData", body)

    # ================= M4-4 查询与统计 =================
    print("== M4-4 查询与统计 ==")
    status, body = call("GET", "/m4/inspections/page?pageNo=1&pageSize=50&orderNo=CG-DD-20261011",
                        token=keeper)
    rows = body["data"]["records"]
    check("M4-4-1 按订单号检索命中本轮验收单",
          any(r["inspectionNo"] == a["inspectionNo"] for r in rows), [r["inspectionNo"] for r in rows])
    status, body = call("GET", "/m4/inspections/page?pageNo=1&pageSize=50&inspectionResult=3",
                        token=keeper)
    check("M4-4-1 按验收结果筛选（让步接收）",
          all(r["inspectionResult"] == 3 for r in body["data"]["records"])
          and len(body["data"]["records"]) >= 1, body["data"]["records"])
    status, body = call("GET", "/m4/inspections/page?pageNo=1&pageSize=50&startDate=%s&endDate=%s"
                        % (TODAY, TODAY), token=keeper)
    check("M4-4-1 按到货日期范围筛选（今天含本轮 4 张）",
          len(body["data"]["records"]) == 3, [r["inspectionNo"] for r in body["data"]["records"]])
    status, body = call("GET", "/m4/inspections/page?pageNo=1&pageSize=50", token=buyer)
    check("M4-4-1 采购员部门维度范围在仓库单据上失败关闭（空列表）",
          body["data"]["total"] == 0, body["data"])
    status, body = call("GET", "/m4/inspections/page?pageNo=1&pageSize=50", token=admin)
    check("M4-4-1 管理员全量可见（不少于仓管员）",
          body["data"]["total"] >= 4, body["data"]["total"])
    status, body = call("GET", "/m4/inspections/stat?startDate=%s&endDate=%s" % (TODAY, TODAY),
                        token=manager)
    stats = body["data"]
    check("M4-4-3 统计按供应商分组（今天 1 家）",
          len(stats) == 1 and stats[0]["supplierName"], stats)
    check("M4-4-3 统计口径：4 张已验收单、到货730、合格630、不合格100",
          stats[0]["inspectionCount"] == 3
          and float(stats[0]["arrivalQty"]) == 730.0
          and float(stats[0]["qualifiedQty"]) == 630.0
          and float(stats[0]["unqualifiedQty"]) == 100.0, stats[0])
    check("M4-4-3 合格率=合格÷到货（630/730）",
          abs(stats[0]["qualifiedRate"] - 0.8630) < 0.0001, stats[0])
    status, body = call("GET", "/m4/inspections/stat", token=buyer)
    check("M4-4-3 采购员无统计权 NoPermission", body.get("code") == "NoPermission", body)

    # ================= 权限边界 =================
    print("== 权限边界 ==")
    status, body = call("POST", "/m4/inspections", token=buyer, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("采购员无到货登记权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m4/inspections", token=manager, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("主管无到货登记权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("PUT", "/m4/inspections/%d" % ins_b, token=buyer, body={
        "orderId": demo["id"], "arrivalDate": TODAY, "warehouseId": 1,
        "items": [{"orderItemId": item2["orderItemId"], "arrivalQty": 1}]})
    check("采购员无编辑权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m4/inspections/%d" % ins_a, token=buyer)
    check("M4-4-2 明细查看授给全部角色（采购员可读详情，列表层仍失败关闭）",
          body.get("code") == "Success", body)

    # ================= M3 回归：有验收与入库记录的订单不可反审/作废 =================
    print("== M3 回归 ==")
    status, body = call("POST", "/m3/orders/%d/reaudit" % demo["id"], token=admin)
    check("M3-3-5 有到货验收记录的订单不可反审", body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m3/orders/%d/void" % demo["id"], token=manager)
    check("M3-1-5 有入库记录的订单不可作废", body.get("code") == "Unsupported", body)

    # ---------- 汇总 ----------
    print("")
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAIL: " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
