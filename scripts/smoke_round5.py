#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 5 冒烟测试：M3 采购订单管理。

覆盖功能点：
    M3-1-1 列表与检索（数据范围/超期筛选） M3-1-2 新建订单（单号规则）
    M3-1-3 由申请单生成订单              M3-1-4 编辑（草稿/已驳回）
    M3-1-5 作废（未到货前置）             M3-1-6 打印导出（CSV）
    M3-2-1 新增明细行（物料启用/供货范围）M3-2-2 编辑删除明细行（重算金额）
    M3-2-4 按行关闭（履约重算）           M3-2-5 归属对象指定
    M3-3-1 提交审批（完整性+任务生成）    M3-3-2 审批通过（会签两人）
    M3-3-3 审批驳回（意见必填）           M3-3-4 撤回审批（无人处理）
    M3-3-5 反审（仅管理员/仅未到货）      M3-3-6 关闭订单（原因必填）
    M3-4-1 变更登记（前后值留痕）         M3-4-2 变更历史查看
    M3-5-1 交付进度登记（累计守恒）       M3-5-4 超期未交付筛选（后端算天数）
    M3-5-5 交付汇总视图（数据范围）       M3-1-1 订单溯源
    M3-3-2 订单待办（P9）
另含：会签规则（两人任务/任一通过不推进/单人驳回即整单驳回）、权限边界
（主管不可建单、管理员无订单查询权、仓管员范围失败关闭）、M2 回归
（建单部门缺省取当前用户部门、删最后行再加行不撞唯一键）。

用法：python scripts/smoke_round5.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
测试数据统一 smk5 前缀，跑完执行 db/18_clean_smoke_round5.sql 复原。
退出码 0 表示全部通过。
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 每次运行使用唯一后缀，脚本可反复执行
SUFFIX = str(int(time.time()))[-6:]
MARK = "smk5" + SUFFIX
TODAY = time.strftime("%Y%m%d")


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


def login(username, password="123456"):
    return call("POST", "/auth/login", body={"username": username, "password": password})


def token_of(username):
    status, body = login(username)
    assert status == 200 and body.get("code") == "Success", (username, body)
    return body["data"]["token"]


def covering_supplier(material, suppliers, token):
    """按供货范围查找可供该物料的供应商：按物料或按分类命中。"""
    for supplier in suppliers:
        status, body = call("GET", "/m1/suppliers/%d/scopes" % supplier["id"], token=token)
        if body.get("code") != "Success":
            continue
        scopes = body.get("data") or []
        if not scopes:
            return supplier
        for scope in scopes:
            if scope.get("scopeType") == 1 and scope.get("scopeId") == material["id"]:
                return supplier
            if scope.get("scopeType") == 2 and scope.get("scopeId") == material.get("categoryId"):
                return supplier
    return None


def create_order(token, body):
    return call("POST", "/m3/orders", token=token, body=body)


def add_order_item(token, order_id, body):
    return call("POST", "/m3/orders/%d/items" % order_id, token=token, body=body)


def approve_both(order_id, manager1, manager2):
    """会签：两名主管都要通过。返回两步结果。"""
    r1 = call("POST", "/m3/orders/%d/approve" % order_id, token=manager1)
    r2 = call("POST", "/m3/orders/%d/approve" % order_id, token=manager2)
    return r1, r2


def order_detail(order_id, token):
    status, body = call("GET", "/m3/orders/%d" % order_id, token=token)
    assert body.get("code") == "Success", body
    return body["data"]


def main():
    # ---------- 登录六个角色 ----------
    admin = token_of("admin")
    buyer1 = token_of("caigou01")   # 采购部 1101
    buyer2 = token_of("caigou02")   # 生产部 1102
    manager1 = token_of("zhuguan01")
    manager2 = token_of("zhuguan02")
    keeper = token_of("cangku01")
    print("== 登录六个角色 ==")
    check("六角色登录", all([admin, buyer1, buyer2, manager1, manager2, keeper]))

    # ---------- 基础数据 ----------
    status, body = call("GET", "/m1/materials/options", token=buyer1)
    materials = body["data"]
    mat_a, mat_b = materials[0], materials[1]
    status, body = call("GET", "/m1/suppliers/options", token=buyer1)
    suppliers = body["data"]
    sup_a = covering_supplier(mat_a, suppliers, buyer1)
    check("物料 A 有合规供应商", sup_a is not None, mat_a)
    status, body = call("GET", "/m3/orders/owner-options", token=buyer1)
    owners = body["data"]["options"]
    project_owner = next((o for o in owners if o["ownerType"] == 2), None)
    check("M3-2-5 项目归属对象存在", project_owner is not None, owners)

    # 造停用物料与停用供应商（复用 M1 接口，清理脚本按 smk 前缀清除）
    status, body = call("POST", "/m1/materials", token=buyer1, body={
        "materialNo": MARK + "-MAT", "materialName": "冒烟停用物料",
        "categoryId": mat_a["categoryId"], "unitId": mat_a.get("unitId") or 1001,
        "materialType": 1, "safetyStock": 0, "extAttrs": []})
    disabled_mat = body.get("data")
    call("PATCH", "/m1/materials/%d/status" % disabled_mat, token=buyer1)
    status, body = call("POST", "/m1/suppliers", token=buyer1, body={
        "supplierNo": MARK + "-GYS", "supplierName": "冒烟停用供应商",
        "supplierType": 1, "taxNo": "91331000MA2" + SUFFIX + "X", "status": 1,
        "contacts": [], "scopes": [], "attachments": []})
    disabled_sup = body.get("data")
    call("PATCH", "/m1/suppliers/%d/status" % disabled_sup, token=buyer1)
    # 造一个不配供货范围的启用供应商（未配范围行视为不限，主订单混合两种物料用）
    status, body = call("POST", "/m1/suppliers", token=buyer1, body={
        "supplierNo": MARK + "-ALL", "supplierName": "冒烟全量供应商",
        "supplierType": 1, "taxNo": "91331000MA2" + SUFFIX + "Y", "status": 1,
        "contacts": [], "scopes": [], "attachments": []})
    sup_all = body.get("data")
    check("造全量供应商（不配供货范围）", sup_all is not None, body)
    # 找一个不供 mat_a 的供应商（供货范围反向用例）
    bad_supplier = None
    for supplier in suppliers:
        if supplier["id"] == sup_a["id"]:
            continue
        status, body = call("GET", "/m1/suppliers/%d/scopes" % supplier["id"], token=buyer1)
        scopes = body.get("data") or []
        covered = any((s.get("scopeType") == 1 and s.get("scopeId") == mat_a["id"])
                      or (s.get("scopeType") == 2 and s.get("scopeId") == mat_a.get("categoryId"))
                      for s in scopes)
        if scopes and not covered:
            bad_supplier = supplier
            break
    check("造出供货范围不合规供应商", bad_supplier is not None, "跳过供货范围反向断言")

    # ================= M3-1-2 新建订单 =================
    print("== M3-1 订单维护 ==")
    status, body = create_order(buyer1, {
        "supplierId": sup_all, "deliveryDate": "2026-12-01",
        "deliveryPlace": "台州市演示工业园 1 号库", "remark": MARK + " 主订单"})
    check("M3-1-2 采购员可建订单", status == 200 and body.get("code") == "Success", body)
    main_order = body.get("data")
    detail = order_detail(main_order, buyer1)
    check("M3-1-2 单号按规则生成 CG-DD-日期-流水",
          re.match(r"^CG-DD-" + TODAY + r"-\d{4}$", detail["orderNo"]) is not None,
          detail["orderNo"])
    check("M3-1-2 新单三状态独立且为初值",
          detail["lifecycleStatus"] == 1 and detail["auditStatus"] == 1
          and detail["fulfillStatus"] == 1, detail)
    check("M3-1-2 部门取当前用户部门（数据范围前提）",
          detail["deptId"] == 1101, detail["deptId"])

    status, body = create_order(buyer1, {"supplierId": 999999, "remark": MARK})
    check("M3-1-2 供应商不存在 NotFound", body.get("code") == "NotFound", body)
    status, body = create_order(buyer1, {"supplierId": disabled_sup, "remark": MARK})
    check("M3-1-2 停用供应商 Unsupported", body.get("code") == "Unsupported", body)
    status, body = create_order(manager1, {"supplierId": sup_a["id"], "remark": MARK})
    check("M3-1-2 主管不可建单 NoPermission", body.get("code") == "NoPermission", body)

    # 数据范围：caigou01 看不到演示订单 2（生产部 1102）
    status, body = call("GET", "/m3/orders/2", token=buyer1)
    check("M3-1-1 数据范围：跨部门订单不可见", body.get("code") == "NoPermission", body)
    status, body = call("PUT", "/m3/orders/2", token=buyer1,
                        body={"supplierId": sup_a["id"], "remark": MARK})
    check("M3-1-1 数据范围：跨部门不可编辑", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m3/orders/2", token=manager1)
    check("M3-1-1 数据范围：主管跨部门可见（1101+1102）",
          body.get("code") == "Success", body)

    # ================= M3-1-1 列表检索 =================
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50&orderNo="
                        + urllib.parse.quote(detail["orderNo"]), token=buyer1)
    check("M3-1-1 按单号检索命中", body["data"]["total"] >= 1, body)
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50&supplierId=%d"
                        % sup_a["id"], token=buyer1)
    check("M3-1-1 按供应商筛选命中", body["data"]["total"] >= 1, body)

    # 演示订单 1：三状态正交样本 + 超期天数（交期 2026-10-18 已过、部分交付）
    demo1 = order_detail(1, buyer1)
    check("M3-1-1 演示单三状态独立（已审核/通过/部分交付）",
          demo1["lifecycleStatus"] == 3 and demo1["auditStatus"] == 2
          and demo1["fulfillStatus"] == 2, demo1)
    expect_overdue = (time.time() - time.mktime(time.strptime("2026-10-18", "%Y-%m-%d"))) // 86400
    check("M3-5-4 超期天数后端计算（交期 2026-10-18，部分交付）",
          demo1["overdueDays"] == int(expect_overdue),
          (demo1["overdueDays"], int(expect_overdue)))
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50&overdue=true", token=buyer1)
    rows = body["data"]["records"]
    check("M3-5-4 超期筛选命中演示单 1",
          any(r["orderNo"] == "CG-DD-20261011-0001" for r in rows), rows)
    check("M3-5-4 超期筛选结果均带超期天数",
          all(r["overdueDays"] > 0 for r in rows), rows)

    # 仓管员：有查询权但数据范围是仓库维度，部门过滤失败关闭返回空
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50", token=keeper)
    check("M3-1-1 仓管员范围失败关闭（空列表不报错）",
          status == 200 and body.get("code") == "Success" and body["data"]["total"] == 0, body)
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50", token=admin)
    check("M3-1-1 管理员无订单查询权（只授反审）NoPermission",
          body.get("code") == "NoPermission", body)

    # ================= M3-2 明细行 =================
    print("== M3-2 订单明细行 ==")
    status, body = add_order_item(buyer1, main_order, {
        "materialId": mat_a["id"], "quantity": 100, "unitPrice": 2.5})
    check("M3-2-1 新增明细行", status == 200 and body.get("code") == "Success", body)
    item_a = body.get("data")
    detail = order_detail(main_order, buyer1)
    row = detail["items"][0]
    check("M3-2-1 行金额=数量×单价（250）", abs(float(row["amount"]) - 250) < 0.01, row)
    check("M3-2-1 不含税金额=含税÷1.13（221.24）",
          abs(float(row["noTaxAmount"]) - 221.24) < 0.01, row)
    check("M3-2-1 主表合计同步（250）", abs(float(detail["totalAmount"]) - 250) < 0.01, detail)

    status, body = add_order_item(buyer1, main_order, {
        "materialId": mat_b["id"], "quantity": 50, "unitPrice": 30,
        "ownerType": 2, "ownerId": project_owner["id"], "remark": MARK + " 行"})
    check("M3-2-1 第二行（项目归属）", status == 200 and body.get("code") == "Success", body)
    item_b = body.get("data")
    detail = order_detail(main_order, buyer1)
    check("M3-2-2 单据金额=行金额汇总（250+1500=1750）",
          abs(float(detail["totalAmount"]) - 1750) < 0.01, detail["totalAmount"])
    check("M3-2-5 归属对象名称已补全",
          detail["items"][1]["ownerName"] == project_owner["ownerName"], detail["items"])

    status, body = add_order_item(buyer1, main_order, {
        "materialId": disabled_mat, "quantity": 10, "unitPrice": 1})
    check("M3-2-1 停用物料不可选", body.get("code") == "Unsupported", body)
    status, body = add_order_item(buyer1, main_order, {
        "materialId": mat_a["id"], "quantity": 0, "unitPrice": 1})
    check("M3-2-1 数量为 0 InvalidParameter", body.get("code") == "InvalidParameter", body)

    # 供货范围不合规：单独建单（供应商不供 mat_a）
    if bad_supplier:
        status, body = create_order(buyer1, {"supplierId": bad_supplier["id"], "remark": MARK})
        bad_order = body.get("data")
        status, body = add_order_item(buyer1, bad_order, {
            "materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
        check("M3-2-1 供货范围不含该物料 Unsupported",
              body.get("code") == "Unsupported", body)

    # 编辑行：数量 100→120、单价 2.5→2.2，重算金额
    status, body = call("PUT", "/m3/orders/%d/items/%d" % (main_order, item_a),
                        token=buyer1, body={"materialId": mat_a["id"],
                                            "quantity": 120, "unitPrice": 2.2})
    check("M3-2-2 编辑明细行", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-2-2 编辑后重算（120×2.2+1500=1764）",
          abs(float(detail["totalAmount"]) - 1764) < 0.01, detail["totalAmount"])

    # 删除行后行号不复用（uk_order_item_no 不含 is_deleted，轮次 5 修正的隐患）
    status, body = call("DELETE", "/m3/orders/%d/items/%d" % (main_order, item_b),
                        token=buyer1)
    check("M3-2-2 删除明细行", status == 200 and body.get("code") == "Success", body)
    status, body = add_order_item(buyer1, main_order, {
        "materialId": mat_b["id"], "quantity": 50, "unitPrice": 30,
        "ownerType": 2, "ownerId": project_owner["id"]})
    check("M3-2-2 删行后新增成功（不撞唯一键）",
          status == 200 and body.get("code") == "Success", body)
    item_c = body.get("data")
    detail = order_detail(main_order, buyer1)
    check("M3-2-2 行号连同已删行递增（新行号 3，不复用 2）",
          detail["items"][1]["itemNo"] == 3, [i["itemNo"] for i in detail["items"]])
    check("M3-2-2 删除后金额不重算错误（264+1500=1764）",
          abs(float(detail["totalAmount"]) - 1764) < 0.01, detail["totalAmount"])

    # ================= M3-1-4 编辑主表 =================
    status, body = call("PUT", "/m3/orders/%d" % main_order, token=buyer1, body={
        "supplierId": sup_a["id"], "deliveryDate": "2026-12-15",
        "deliveryPlace": "台州市演示工业园 2 号库", "settlementType": 2,
        "remark": MARK + " 改后"})
    check("M3-1-4 编辑草稿主表", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-1-4 主表编辑生效（交期/地点/结算方式/备注）",
          detail["deliveryDate"] == "2026-12-15"
          and detail["deliveryPlace"] == "台州市演示工业园 2 号库"
          and detail["settlementTypeName"] == "货到付款"
          and detail["remark"] == MARK + " 改后", detail)

    # ================= M3-3-1 提交审批 =================
    print("== M3-3 订单审批（会签） ==")
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 空单"})
    empty_order = body.get("data")
    status, body = call("POST", "/m3/orders/%d/submit" % empty_order, token=buyer1)
    check("M3-3-1 无明细不可提交", body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m3/orders/%d/submit" % main_order, token=buyer1)
    check("M3-3-1 提交审批", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-3-1 提交后为已提交未审核",
          detail["lifecycleStatus"] == 2 and detail["auditStatus"] == 1, detail)
    pending_tasks = [h for h in detail["approvalHistory"] if h["taskStatus"] == 1]
    check("M3-3-1 会签节点生成两人任务（或签规则对比样）",
          len(pending_tasks) == 2 and all(t["signTypeName"] == "会签" for t in pending_tasks),
          pending_tasks)

    # 并发提交只成功一次（第二次状态已变）
    status, body = call("POST", "/m3/orders/%d/submit" % main_order, token=buyer1)
    check("M3-3-1 重复提交被拒", body.get("code") != "Success", body)

    # 已提交不可编辑（M3-1-4 状态边界）
    status, body = call("PUT", "/m3/orders/%d" % main_order, token=buyer1,
                        body={"supplierId": sup_a["id"]})
    check("M3-1-4 已提交订单不可编辑", body.get("code") == "Unsupported", body)

    # ================= M3-3-2 审批通过（会签） =================
    status, body = call("POST", "/m3/orders/%d/approve" % main_order, token=buyer1)
    check("M3-3-2 采购员不可审批 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/approve" % main_order, token=manager1)
    check("M3-3-2 主管1 通过（会签未齐不推进）",
          status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-3-2 会签一人通过仍在审批中",
          detail["lifecycleStatus"] == 2, detail)
    status, body = call("POST", "/m3/orders/%d/approve" % main_order, token=manager1)
    check("M3-3-2 重复处理同一单据被拒", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m3/orders/%d/approve" % main_order, token=manager2)
    check("M3-3-2 主管2 通过（会签齐→审核）",
          status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-3-2 会签通过后三状态（已审核/通过/待交付）",
          detail["lifecycleStatus"] == 3 and detail["auditStatus"] == 2
          and detail["fulfillStatus"] == 1, detail)
    check("M3-3-2 审批历史含两条已通过",
          len([h for h in detail["approvalHistory"] if h["taskStatus"] == 2]) == 2,
          detail["approvalHistory"])

    # ================= M3-3-3 审批驳回 =================
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 驳回单"})
    reject_order = body.get("data")
    add_order_item(buyer1, reject_order, {
        "materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % reject_order, token=buyer1)
    status, body = call("POST", "/m3/orders/%d/reject" % reject_order, token=manager1,
                        body={"opinion": ""})
    check("M3-3-3 驳回意见必填", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m3/orders/%d/reject" % reject_order, token=manager1,
                        body={"opinion": MARK + " 会签任一驳回即整单驳回"})
    check("M3-3-3 会签单人驳回即整单驳回",
          status == 200 and body.get("code") == "Success", body)
    detail = order_detail(reject_order, buyer1)
    check("M3-3-3 驳回后回落草稿、审批结果驳回",
          detail["lifecycleStatus"] == 1 and detail["auditStatus"] == 3, detail)
    # 驳回后可编辑重提
    status, body = call("PUT", "/m3/orders/%d" % reject_order, token=buyer1,
                        body={"supplierId": sup_a["id"], "remark": MARK + " 改后重提"})
    check("M3-1-4 已驳回订单可编辑", status == 200 and body.get("code") == "Success", body)
    status, body = call("POST", "/m3/orders/%d/submit" % reject_order, token=buyer1)
    check("M3-3-1 驳回后重新提交", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(reject_order, buyer1)
    check("M3-3-1 重提后仍在审批中、历史任务保留",
          detail["lifecycleStatus"] == 2
          and len([h for h in detail["approvalHistory"] if h["taskStatus"] == 3]) == 1,
          detail["approvalHistory"])
    approve_both(reject_order, manager1, manager2)

    # ================= M3-3-4 撤回审批 =================
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 撤回单"})
    withdraw_order = body.get("data")
    add_order_item(buyer1, withdraw_order, {
        "materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % withdraw_order, token=buyer1)
    status, body = call("POST", "/m3/orders/%d/withdraw" % withdraw_order, token=buyer2)
    check("M3-3-4 非采购员不可撤回 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/withdraw" % withdraw_order, token=buyer1)
    check("M3-3-4 无人处理可撤回", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(withdraw_order, buyer1)
    check("M3-3-4 撤回后回草稿", detail["lifecycleStatus"] == 1, detail)

    # 已有人处理不可撤回
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 撤回处理单"})
    withdraw2 = body.get("data")
    add_order_item(buyer1, withdraw2, {"materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % withdraw2, token=buyer1)
    call("POST", "/m3/orders/%d/approve" % withdraw2, token=manager1)
    status, body = call("POST", "/m3/orders/%d/withdraw" % withdraw2, token=buyer1)
    check("M3-3-4 已有审批处理记录不可撤回", body.get("code") == "Unsupported", body)

    # ================= M3-5-1 交付进度登记 =================
    print("== M3-5 履约跟踪 ==")
    status, body = call("POST", "/m3/orders/%d/deliveries" % main_order, token=buyer1,
                        body={"orderItemId": item_a, "deliveryQty": 60, "batchNo": "B" + SUFFIX})
    check("M3-5-1 交付进度登记", status == 200 and body.get("code") == "Success", body)
    delivery_id = body.get("data")
    status, body = call("POST", "/m3/orders/%d/deliveries" % main_order, token=buyer1,
                        body={"orderItemId": item_a, "deliveryQty": 99})
    check("M3-5-1 累计登记不超订单数量（60+99>120）",
          body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m3/orders/%d/deliveries" % empty_order, token=buyer1,
                        body={"orderItemId": item_a, "deliveryQty": 1})
    check("M3-5-1 草稿单不可登记交付", body.get("code") == "Unsupported", body)
    status, body = call("GET", "/m3/orders/%d/deliveries" % main_order, token=buyer1)
    records = body["data"]
    check("M3-5-1 交付流水可查（1 条，批次与数量正确）",
          len(records) == 1 and float(records[0]["deliveryQty"]) == 60
          and records[0]["batchNo"] == "B" + SUFFIX, records)

    # ================= M3-4 订单变更 =================
    print("== M3-4 订单变更留痕 ==")
    status, body = call("POST", "/m3/orders/%d/change" % main_order, token=buyer1,
                        body={"itemId": item_a, "quantity": 150, "unitPrice": 2.0})
    check("M3-4-1 变更原因必填", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m3/orders/%d/change" % main_order, token=buyer1,
                        body={"itemId": item_a, "quantity": 150, "unitPrice": 2.0,
                              "reason": MARK + " 行级变更"})
    check("M3-4-1 行级变更登记", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    row = detail["items"][0]
    check("M3-4-1 变更后数量与单价生效（150×2.0=300）",
          float(row["quantity"]) == 150 and float(row["unitPrice"]) == 2.0
          and abs(float(row["amount"]) - 300) < 0.01, row)
    check("M3-4-1 变更后主表重算（300+1500=1800）",
          abs(float(detail["totalAmount"]) - 1800) < 0.01, detail["totalAmount"])
    logs = detail["changeLogs"]
    check("M3-4-1 变更历史两条（数量与单价）",
          len(logs) == 2, logs)
    qty_log = next((l for l in logs if l["changeField"] == "quantity"), None)
    check("M3-4-1 变更前后值留痕（120→150）",
          qty_log and qty_log["beforeValue"].startswith("120")
          and qty_log["afterValue"].startswith("150"), qty_log)
    check("M3-4-1 变更带行号与操作人",
          qty_log and qty_log["itemNo"] == 1 and qty_log["operatorName"] == "张伟", qty_log)
    status, body = call("POST", "/m3/orders/%d/change" % main_order, token=buyer1,
                        body={"itemId": item_a, "quantity": 150, "unitPrice": 2.0,
                              "reason": MARK + " 无变化"})
    check("M3-4-1 无实际变更被拒", body.get("code") == "InvalidParameter", body)
    # 整单级变更
    status, body = call("POST", "/m3/orders/%d/change" % main_order, token=buyer1,
                        body={"deliveryDate": "2026-12-20", "settlementType": 3,
                              "reason": MARK + " 整单变更"})
    check("M3-4-1 整单级变更登记", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m3/orders/%d/change-logs" % main_order, token=buyer1)
    logs2 = body["data"]
    header_log = next((l for l in logs2 if l["changeField"] == "delivery_date"), None)
    check("M3-4-2 变更历史独立接口可查，整单变更无行号",
          header_log and header_log["orderItemId"] is None and header_log["itemNo"] is None,
          header_log)
    check("M3-4-2 变更历史按时间正序（早变更在上）",
          [l["changeAt"] for l in logs2] == sorted(l["changeAt"] for l in logs2), logs2)

    # ================= M3-2-4 按行关闭与履约重算 =================
    status, body = call("POST", "/m3/orders/%d/items/close" % main_order, token=manager1,
                        body={"itemIds": [item_c]})
    check("M3-2-4 按行关闭（主管）", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-2-4 关闭行履约状态=已关闭",
          detail["items"][1]["itemFulfillStatusName"] == "已关闭", detail["items"])
    check("M3-5-2 仍有待交付行→主单仍待交付",
          detail["fulfillStatusName"] == "待交付", detail)
    status, body = call("POST", "/m3/orders/%d/items/close" % main_order, token=buyer1,
                        body={"itemIds": [item_a]})
    check("M3-2-4 采购员不可按行关闭 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/items/close" % main_order, token=manager1,
                        body={"itemIds": [item_a]})
    check("M3-2-4 关闭剩余行", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(main_order, buyer1)
    check("M3-5-3 全部行关闭→主单履约已关闭",
          detail["fulfillStatusName"] == "已关闭", detail)
    status, body = call("POST", "/m3/orders/%d/items/close" % main_order, token=manager1,
                        body={"itemIds": [item_a]})
    check("M3-2-4 重复关闭被拒", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m3/orders/%d/deliveries" % main_order, token=buyer1,
                        body={"orderItemId": item_a, "deliveryQty": 1})
    check("M3-5-1 已关闭行不可登记交付", body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m3/orders/%d/change" % main_order, token=buyer1,
                        body={"itemId": item_a, "quantity": 200, "reason": MARK})
    check("M3-4-1 已关闭行不可变更", body.get("code") == "Unsupported", body)

    # ================= M3-1-3 由申请单生成订单（含 M2 回归） =================
    print("== M3-1-3 由申请单生成订单 ==")
    # M2 回归：建单不传部门（前端路径），服务层取当前用户部门
    status, body = call("POST", "/m2/requests", token=buyer1, body={"remark": MARK + " 回归单"})
    check("M2 回归：不传部门可建单（取当前用户部门）",
          status == 200 and body.get("code") == "Success", body)
    reg_request = body.get("data")
    # M2 回归：删最后行再加行不撞唯一键（maxItemNo 修正）
    add_item1 = call("POST", "/m2/requests/%d/items" % reg_request, token=buyer1, body={
        "materialId": mat_a["id"], "quantity": 10, "estPrice": 5,
        "suggestSupplierId": sup_a["id"], "requireDate": "2026-12-01"})
    add_item2 = call("POST", "/m2/requests/%d/items" % reg_request, token=buyer1, body={
        "materialId": mat_b["id"], "quantity": 20, "estPrice": 6,
        "suggestSupplierId": sup_a["id"], "requireDate": "2026-12-01"})
    req_item2 = add_item2[1].get("data")
    call("DELETE", "/m2/requests/%d/items/%d" % (reg_request, req_item2), token=buyer1)
    status, body = call("POST", "/m2/requests/%d/items" % reg_request, token=buyer1, body={
        "materialId": mat_b["id"], "quantity": 20, "estPrice": 6,
        "suggestSupplierId": sup_a["id"], "requireDate": "2026-12-01"})
    check("M2 回归：删最后行再加行不撞唯一键",
          status == 200 and body.get("code") == "Success", body)

    call("POST", "/m2/requests/%d/submit" % reg_request, token=buyer1)
    status, body = call("POST", "/m2/requests/%d/approve" % reg_request, token=manager1)
    check("M2 回归：或签一人通过即审核", status == 200 and body.get("code") == "Success", body)
    status, body = call("POST", "/m3/orders/from-request", token=buyer1,
                        body={"requestId": reg_request})
    check("M3-1-3 由申请单生成订单", status == 200 and body.get("code") == "Success", body)
    gen = body["data"]["orders"][0]
    check("M3-1-3 生成单带回来源单号",
          gen["sourceRequestNo"].startswith("CG-SQ-" + TODAY), gen)
    gen_detail = order_detail(gen["orderId"], buyer1)
    check("M3-1-3 生成单为草稿且来源申请已关联",
          gen_detail["lifecycleStatus"] == 1
          and gen_detail["sourceRequestNo"] == gen["sourceRequestNo"], gen_detail)
    check("M3-1-3 明细行带回来源申请行（行级追溯）",
          all(i["sourceRequestItemId"] for i in gen_detail["items"]), gen_detail["items"])
    reg_detail = call("GET", "/m2/requests/%d" % reg_request, token=buyer1)[1]["data"]
    check("M3-1-3 申请行已转数量已回写",
          all(float(i["transferredQty"]) > 0 for i in reg_detail["items"]), reg_detail["items"])
    # 溯源：转单生成的订单可追溯到申请单
    status, body = call("GET", "/m3/orders/%d/trace" % gen["orderId"], token=buyer1)
    check("M3-1-1 订单溯源返回来源申请单",
          status == 200 and body["data"]["requestNo"] == gen["sourceRequestNo"], body)
    # 溯源边界
    status, body = call("GET", "/m3/orders/%d/trace" % main_order, token=buyer1)
    check("M3-1-1 手工单溯源 NoData", body.get("code") == "NoData", body)
    status, body = call("GET", "/m3/orders/99999/trace", token=buyer1)
    check("M3-1-1 溯源不存在订单 NotFound", body.get("code") == "NotFound", body)
    status, body = call("GET", "/m3/orders/1/trace", token=buyer2)
    check("M3-1-1 溯源跨部门 NoPermission", body.get("code") == "NoPermission", body)

    # ================= M3-3-6 关闭订单 =================
    print("== M3-3-6 关闭与 M3-1-5 作废 ==")
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 关闭单"})
    close_order = body.get("data")
    add_order_item(buyer1, close_order, {"materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % close_order, token=buyer1)
    approve_both(close_order, manager1, manager2)
    status, body = call("POST", "/m3/orders/%d/close" % close_order, token=manager1, body={})
    check("M3-3-6 关闭原因必填", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m3/orders/%d/close" % close_order, token=manager1,
                        body={"reason": MARK + " 剩余不再交付"})
    check("M3-3-6 整单关闭", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(close_order, buyer1)
    check("M3-3-6 关闭后三状态（已关闭/通过/已关闭）",
          detail["lifecycleStatus"] == 4 and detail["auditStatus"] == 2
          and detail["fulfillStatus"] == 4, detail)
    check("M3-3-6 关闭原因落库", detail["closeReason"] == MARK + " 剩余不再交付", detail)
    status, body = call("POST", "/m3/orders/%d/close" % close_order, token=manager1,
                        body={"reason": "again"})
    check("M3-3-6 重复关闭被拒", body.get("code") == "Unsupported", body)

    # 作废：审批中的订单可作废
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 作废单"})
    void_order = body.get("data")
    add_order_item(buyer1, void_order, {"materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % void_order, token=buyer1)
    status, body = call("POST", "/m3/orders/%d/void" % void_order, token=buyer1)
    check("M3-1-5 采购员不可作废 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/void" % void_order, token=manager1)
    check("M3-1-5 主管可作废审批中订单", status == 200 and body.get("code") == "Success", body)
    detail = order_detail(void_order, buyer1)
    check("M3-1-5 作废后生命周期=已作废", detail["lifecycleStatus"] == 5, detail)

    # 已到货订单不可作废（演示单 1 有交付登记+验收+入库）
    status, body = call("POST", "/m3/orders/1/void", token=manager1)
    check("M3-1-5 已有交付登记的订单不可作废", body.get("code") == "Unsupported", body)
    status, body = call("POST", "/m3/orders/1/void", token=admin)
    check("M3-1-5 管理员无作废权 NoPermission", body.get("code") == "NoPermission", body)

    # ================= M3-3-5 反审 =================
    print("== M3-3-5 反审 ==")
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 反审单"})
    reaudit_order = body.get("data")
    add_order_item(buyer1, reaudit_order, {
        "materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % reaudit_order, token=buyer1)
    approve_both(reaudit_order, manager1, manager2)
    status, body = call("POST", "/m3/orders/%d/reaudit" % reaudit_order, token=manager1)
    check("M3-3-5 主管不可反审 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/reaudit" % reaudit_order, token=buyer1)
    check("M3-3-5 采购员不可反审 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m3/orders/%d/reaudit?reason=%s" % (
        reaudit_order, urllib.parse.quote(MARK + " 纠正审批错误")), token=admin)
    check("M3-3-5 管理员可反审（未到货）",
          status == 200 and body.get("code") == "Success", body)
    detail = order_detail(reaudit_order, buyer1)
    check("M3-3-5 反审后回已提交、审批结果未审核",
          detail["lifecycleStatus"] == 2 and detail["auditStatus"] == 1, detail)
    check("M3-3-5 反审保留原审批历史（不删任务）",
          len([h for h in detail["approvalHistory"] if h["taskStatus"] == 2]) == 2,
          detail["approvalHistory"])
    # 反审后重新走审批
    status, body = call("POST", "/m3/orders/%d/submit" % reaudit_order, token=buyer1)
    check("M3-3-5 反审后可重新提交", status == 200 and body.get("code") == "Success", body)
    approve_both(reaudit_order, manager1, manager2)
    detail = order_detail(reaudit_order, buyer1)
    check("M3-3-5 重走审批后再次审核通过",
          detail["lifecycleStatus"] == 3 and detail["auditStatus"] == 2, detail)
    # 已有交付登记不可反审（主订单）
    status, body = call("POST", "/m3/orders/%d/reaudit" % main_order, token=admin)
    check("M3-3-5 已有交付登记的订单不可反审", body.get("code") == "Unsupported", body)
    # 演示单 1（有验收+入库）不可反审
    status, body = call("POST", "/m3/orders/1/reaudit", token=admin)
    check("M3-3-5 已有到货验收与入库的订单不可反审", body.get("code") == "Unsupported", body)

    # ================= M3-5-4 超期筛选 =================
    print("== M3-5-4 超期与 M3-5-5 汇总 ==")
    status, body = create_order(buyer1, {
        "supplierId": sup_a["id"], "deliveryDate": "2027-01-15", "remark": MARK + " 未来单"})
    future_order = body.get("data")
    add_order_item(buyer1, future_order, {
        "materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % future_order, token=buyer1)
    approve_both(future_order, manager1, manager2)
    status, body = call("GET", "/m3/orders/page?pageNo=1&pageSize=50&overdue=true", token=buyer1)
    rows = body["data"]["records"]
    check("M3-5-4 未来交期单不进超期集",
          all(r["orderNo"] != order_detail(future_order, buyer1)["orderNo"] for r in rows),
          [r["orderNo"] for r in rows])

    # ================= M3-5-5 交付汇总 =================
    # 用日期窗口过滤，只统计演示单（下单日期 2026-10-11~16），
    # 避免本轮新建订单混入汇总口径断言
    status, body = call("GET", "/m3/orders/delivery-summary?startDate=2026-10-01&endDate=2026-10-17",
                        token=manager1)
    summary = body["data"]
    # 主管角色兼管 1101+1102 两个部门，但演示单 2 是草稿（lifecycle=1），
    # 汇总只统计已审核/已关闭单，故窗口内只有 SUP-001 一家
    check("M3-5-5 主管汇总仅含已审核单（草稿单 2 不进汇总）", len(summary) == 1, summary)
    demo1_sup = order_detail(1, buyer1)["supplierId"]
    row = next((r for r in summary if r["supplierId"] == demo1_sup), None)
    check("M3-5-5 汇总口径（订单数/已交付/未交付/金额）",
          row and row["orderCount"] == 1 and float(row["deliveredQty"]) == 600
          and float(row["remainingQty"]) == 900 and float(row["totalAmount"]) == 13700, row)
    check("M3-5-5 汇总金额自洽（合计=已交付+未交付）",
          row and abs(float(row["totalAmount"]) - float(row["deliveredAmount"])
                      - float(row["remainingAmount"])) < 0.01, row)
    status, body = call("GET", "/m3/orders/delivery-summary?startDate=2026-10-01&endDate=2026-10-17",
                        token=buyer1)
    summary1 = body["data"]
    check("M3-5-5 数据范围：采购员只见本部门供应商汇总",
          all(r["supplierId"] != order_detail(2, manager1)["supplierId"] for r in summary1),
          summary1)

    # ================= M3-1-6 导出 =================
    print("== M3-1-6 导出 ==")
    status, content, headers = call("GET", "/m3/orders/1/export", token=buyer1, raw=True)
    text = content.decode("utf-8-sig")
    check("M3-1-6 导出 CSV 且含表头与单号",
          status == 200 and "订单号" in text and "CG-DD-20261011-0001" in text, text[:80])
    status, content, headers = call("GET", "/m3/orders/1/export", token=admin, raw=True)
    check("M3-1-6 管理员无导出权 NoPermission", status == 403, status)

    # ================= M3-3-2 订单待办 =================
    print("== M3-3-2 订单待办 ==")
    status, body = create_order(buyer1, {"supplierId": sup_a["id"], "remark": MARK + " 待办单"})
    todo_order = body.get("data")
    add_order_item(buyer1, todo_order, {"materialId": mat_a["id"], "quantity": 10, "unitPrice": 1})
    call("POST", "/m3/orders/%d/submit" % todo_order, token=buyer1)
    status, body = call("GET", "/m3/orders/todo?pageNo=1&pageSize=50", token=manager1)
    todo_rows = body["data"]["records"]
    check("M3-3-2 主管待办命中待审订单",
          any(r["billNo"] == order_detail(todo_order, buyer1)["orderNo"] for r in todo_rows),
          todo_rows)
    check("M3-3-2 待办带会签标识", all(r["signTypeName"] == "会签" for r in todo_rows), todo_rows)
    status, body = call("GET", "/m3/orders/todo?pageNo=1&pageSize=50", token=buyer1)
    check("M3-3-2 采购员无订单待办权 NoPermission", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m3/orders/todo?pageNo=1&pageSize=50", token=keeper)
    check("M3-3-2 仓管员无订单待办权 NoPermission", body.get("code") == "NoPermission", body)
    # 待办里通过后消失
    approve_both(todo_order, manager1, manager2)
    status, body = call("GET", "/m3/orders/todo?pageNo=1&pageSize=50", token=manager1)
    check("M3-3-2 审批后待办消失",
          all(r["billNo"] != order_detail(todo_order, buyer1)["orderNo"]
              for r in body["data"]["records"]), body["data"]["records"])

    # ---------- 汇总 ----------
    print("")
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAIL: " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
