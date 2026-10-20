#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 4 冒烟测试：M2 采购申请管理与审批引擎。

覆盖功能点：
    M2-1-1 列表与检索（数据范围）  M2-1-2 新建申请单（单号规则）
    M2-1-3 编辑（草稿/已驳回）     M2-1-4 删除（仅草稿）
    M2-1-5 复制申请单              M2-1-6 打印导出（CSV）
    M2-2-1 新增明细行（物料启用）  M2-2-2 编辑删除明细行（重算金额）
    M2-2-3 建议供应商（启用+供货范围）M2-2-4 按行退回（单据拆分）
    M2-2-5 归属对象指定            M2-3-1 提交审批（完整性+任务生成）
    M2-3-2 审批通过（或签推进）     M2-3-3 审批驳回（意见必填）
    M2-3-4 撤回申请                M2-3-5 整单关闭（未转量留痕）
    M2-3-6 审批历史时间线          M2-3-7 多人审批（或签/会签规则）
    M2-3-8 我的待办（数据范围）     M2-4-1 按行转订单
    M2-4-2 批量转订单（同供应商合并）M2-4-3 已转数量回写
    M2-4-4 未转量关闭校验
另含权限边界：采购员不可审批、主管不可建单、仓管员范围失败关闭、管理员无 M2 权限。

用法：python scripts/smoke_round4.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
测试数据统一 smk4 前缀（备注标识），跑完执行 db/17_clean_smoke_round4.sql 复原。
退出码 0 表示全部通过。
"""
import json
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
MARK = "smk4" + SUFFIX


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
    """按供货范围查找可供该物料的供应商：按物料或按分类命中。
    供货明细走 /{id}/scopes 子资源（详情只返回 scopeCount）。找不到返回 None。"""
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


def add_item(token, request_id, material_id, quantity, supplier_id, owner_type=1,
             owner_id=None, est_price=10, require_date="2026-12-01"):
    return call("POST", "/m2/requests/%d/items" % request_id, token=token, body={
        "materialId": material_id, "quantity": quantity, "estPrice": est_price,
        "suggestSupplierId": supplier_id, "requireDate": require_date,
        "ownerType": owner_type, "ownerId": owner_id,
        "remark": MARK + " 明细"
    })


def create_draft(token, dept_id, remark=MARK + " 申请单"):
    status, body = call("POST", "/m2/requests", token=token,
                        body={"deptId": dept_id, "remark": remark})
    return status, body


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

    # ---------- 基础数据：启用物料与供应商 ----------
    status, body = call("GET", "/m1/materials/options", token=buyer1)
    check("物料下拉可读", status == 200 and body.get("code") == "Success", body)
    materials = body["data"]
    check("启用物料非空", len(materials) >= 2, len(materials))
    mat_a, mat_b = materials[0], materials[1]

    status, body = call("GET", "/m1/suppliers/options", token=buyer1)
    check("供应商下拉可读", status == 200 and body.get("code") == "Success", body)
    suppliers = body["data"]
    # 按供货范围为每个物料找一个合规供应商，避免用错供货范围（M2-2-3）
    sup_a = covering_supplier(mat_a, suppliers, buyer1)
    sup_b = covering_supplier(mat_b, suppliers, buyer1)
    check("物料 A 有合规供应商", sup_a is not None, mat_a)
    check("物料 B 有合规供应商", sup_b is not None, mat_b)

    status, body = call("GET", "/m2/requests/owner-options", token=buyer1)
    check("M2-2-5 归属对象选项可读", status == 200 and body.get("code") == "Success", body)
    owners = body["data"]["options"]
    project_owner = next((o for o in owners if o["ownerType"] == 2), None)
    check("M2-2-5 项目归属对象存在", project_owner is not None, owners)

    # ================= M2-1-2 / M2-1-1 新建与列表 =================
    print("== M2-1 申请单维护 ==")
    status, body = create_draft(buyer1, 1101)
    check("M2-1-2 采购员可建申请单", status == 200 and body.get("code") == "Success", body)
    d1 = body.get("data")
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    no1 = body["data"]["requestNo"]
    check("M2-1-2 单号按规则生成 CG-SQ-日期-流水",
          no1.startswith("CG-SQ-" + time.strftime("%Y%m%d") + "-"), no1)
    check("M2-1-2 新单为草稿未审核",
          body["data"]["lifecycleStatus"] == 1 and body["data"]["auditStatus"] == 1, body["data"])

    status, body = call("GET", "/m2/requests/page?pageNo=1&pageSize=50"
                        + "&requestNo=" + urllib.parse.quote(no1), token=buyer1)
    check("M2-1-1 按单号检索命中", status == 200 and body["data"]["total"] >= 1, body)

    # 数据范围：caigou01（1101）看不到 caigou02（1102）的单
    status, body = create_draft(buyer2, 1102)
    d2_hidden = body.get("data")
    status, body = call("GET", "/m2/requests/%d" % d2_hidden, token=buyer1)
    check("M2-1-1 数据范围：跨部门不可见", body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m2/requests/%d" % d2_hidden, token=manager1)
    check("M2-1-1 数据范围：主管跨部门可见（1101+1102）",
          body.get("code") == "Success", body)

    # ================= M2-2-1 / M2-2-2 / M2-2-3 / M2-2-5 明细行 =================
    print("== M2-2 申请明细行 ==")
    status, body = add_item(buyer1, d1, mat_a["id"], 100, sup_a["id"], est_price=12.5)
    check("M2-2-1 新增明细行", status == 200 and body.get("code") == "Success", body)
    item_a = body.get("data")
    status, body = add_item(buyer1, d1, mat_b["id"], 50, sup_b["id"],
                            owner_type=2, owner_id=project_owner["id"], est_price=30)
    check("M2-2-1 第二行（项目归属）", status == 200 and body.get("code") == "Success", body)
    item_b = body.get("data")

    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    total = float(body["data"]["totalAmount"])
    check("M2-2-2 单据金额=行金额汇总（100×12.5+50×30=2750）", abs(total - 2750) < 0.01, total)
    check("M2-2-2 需求日期取明细行最早值",
          str(body["data"]["requireDate"]) == "2026-12-01", body["data"]["requireDate"])
    check("M2-2-5 归属对象名称已补全",
          body["data"]["items"][1]["ownerName"] == project_owner["ownerName"], body["data"]["items"])

    # 停用物料不可选
    status, body = call("POST", "/m1/materials", token=buyer1, body={
        "materialNo": MARK + "-MAT", "materialName": "冒烟停用物料",
        "categoryId": mat_a["categoryId"], "unitId": mat_a.get("unitId") or 1001,
        "materialType": 1, "safetyStock": 0,
        "extAttrs": []})
    check("造停用物料", status == 200 and body.get("code") == "Success", body)
    disabled_mat = body.get("data")
    status, body = call("PATCH", "/m1/materials/%d/status" % disabled_mat, token=buyer1)
    check("物料停用", status == 200 and body.get("code") == "Success", body)
    status, body = add_item(buyer1, d1, disabled_mat, 10, sup_a["id"])
    check("M2-2-1 停用物料不可选", body.get("code") == "Unsupported", body)

    # 供应商停用 / 供货范围校验
    status, body = call("POST", "/m1/suppliers", token=buyer1, body={
        "supplierNo": MARK + "-GYS", "supplierName": "冒烟停用供应商",
        "supplierType": 1, "taxNo": "91331000MA2" + SUFFIX + "X", "status": 1,
        "contacts": [], "scopes": [], "attachments": []})
    check("造停用供应商", status == 200 and body.get("code") == "Success", body)
    disabled_sup = body.get("data")
    status, body = call("PATCH", "/m1/suppliers/%d/status" % disabled_sup, token=buyer1)
    check("供应商停用", status == 200 and body.get("code") == "Success", body)
    status, body = add_item(buyer1, d1, mat_a["id"], 10, disabled_sup)
    check("M2-2-3 停用供应商不可选", body.get("code") == "Unsupported", body)

    # 供货范围不含该物料：找一个不供 mat_a 的供应商
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
    if bad_supplier:
        status, body = add_item(buyer1, d1, mat_a["id"], 10, bad_supplier["id"])
        check("M2-2-3 供货范围不含该物料被拒", body.get("code") == "Unsupported", body)
    else:
        print("SKIP  M2-2-3 供货范围不含该物料被拒（演示数据中找不到不匹配的供应商对）")

    # 数量非法
    status, body = add_item(buyer1, d1, mat_a["id"], 0, sup_a["id"])
    check("M2-2-1 数量必须大于 0", body.get("code") == "InvalidParameter", body)

    # 编辑明细行：数量 100 → 200，金额重算
    status, body = call("PUT", "/m2/requests/%d/items/%d" % (d1, item_a), token=buyer1, body={
        "materialId": mat_a["id"], "quantity": 200, "estPrice": 12.5,
        "suggestSupplierId": sup_a["id"], "requireDate": "2026-12-01",
        "ownerType": 1, "remark": MARK + " 明细"})
    check("M2-2-2 编辑明细行", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    check("M2-2-2 编辑后金额重算（200×12.5+50×30=4000）",
          abs(float(body["data"]["totalAmount"]) - 4000) < 0.01, body["data"]["totalAmount"])

    # 删除明细行
    status, body = call("DELETE", "/m2/requests/%d/items/%d" % (d1, item_b), token=buyer1)
    check("M2-2-2 删除明细行", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    check("M2-2-2 删除后金额重算（200×12.5=2500）",
          abs(float(body["data"]["totalAmount"]) - 2500) < 0.01, body["data"]["totalAmount"])

    # ================= M2-1-5 复制申请单 =================
    print("== M2-1-5 复制 ==")
    status, body = call("POST", "/m2/requests/%d/copy" % d1, token=buyer1)
    check("M2-1-5 复制申请单", status == 200 and body.get("code") == "Success", body)
    d1_copy = body.get("data")
    status, body = call("GET", "/m2/requests/%d" % d1_copy, token=buyer1)
    check("M2-1-5 副本为新草稿且单号不同",
          body["data"]["requestNo"] != no1
          and body["data"]["lifecycleStatus"] == 1 and body["data"]["auditStatus"] == 1, body["data"])
    check("M2-1-5 明细行已复制且已转数量清零",
          len(body["data"]["items"]) == 1
          and float(body["data"]["items"][0]["transferredQty"]) == 0, body["data"]["items"])

    # ================= M2-1-6 导出 =================
    status, content, headers = call("GET", "/m2/requests/%d/export" % d1, token=buyer1, raw=True)
    check("M2-1-6 导出 CSV 状态 200", status == 200, status)
    check("M2-1-6 CSV 带 BOM 且含表头",
          content[:3] == b"\xef\xbb\xbf" and "申请单号".encode("utf-8") in content, content[:80])

    # ================= M2-1-3 / M2-1-4 编辑与删除 =================
    status, body = call("PUT", "/m2/requests/%d" % d1, token=buyer1,
                        body={"deptId": 1101, "remark": MARK + " 改后"})
    check("M2-1-3 编辑草稿主表", status == 200 and body.get("code") == "Success", body)
    status, body = call("DELETE", "/m2/requests/%d" % d1_copy, token=buyer1)
    check("M2-1-4 删除草稿", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1_copy, token=buyer1)
    check("M2-1-4 删除后不可见", body.get("code") == "NotFound", body)

    # ================= M2-3-1 提交审批 =================
    print("== M2-3-1 提交审批 ==")
    # 空单不可提交：新建一张无明细的单
    status, body = create_draft(buyer1, 1101, MARK + " 空单")
    empty_draft = body.get("data")
    status, body = call("POST", "/m2/requests/%d/submit" % empty_draft, token=buyer1)
    check("M2-3-1 无明细不可提交", body.get("code") == "InvalidParameter", body)
    status, body = call("DELETE", "/m2/requests/%d" % empty_draft, token=buyer1)

    # 提交 D1（一行明细 200×12.5，供应商 A）
    status, body = call("POST", "/m2/requests/%d/submit" % d1, token=buyer1)
    check("M2-3-1 提交审批", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    check("M2-3-1 提交后为已提交未审核", body["data"]["lifecycleStatus"] == 2, body["data"])

    # 并发/重复提交只成功一次
    status, body = call("POST", "/m2/requests/%d/submit" % d1, token=buyer1)
    check("M2-3-1 重复提交被拒（并发提交只成功一次）",
          body.get("code") == "Unsupported", body)

    # 已提交后不可编辑（M2-1-3 仅草稿/已驳回）
    status, body = call("PUT", "/m2/requests/%d" % d1, token=buyer1,
                        body={"deptId": 1101, "remark": "x"})
    check("M2-1-3 已提交不可编辑", body.get("code") == "Unsupported", body)

    # 任务生成：两名采购主管各一条待办
    status, body = call("GET", "/m2/requests/todo?pageNo=1&pageSize=50", token=manager1)
    check("M2-3-8 主管待办含新单", status == 200 and body["data"]["total"] >= 1, body)
    todo_bill_nos = [t["billNo"] for t in body["data"]["records"]]
    check("M2-3-8 待办命中提交单", no1 in todo_bill_nos, todo_bill_nos)
    status, body = call("GET", "/m2/requests/todo", token=manager2)
    check("M2-3-7 或签节点两名审批人各生成一条任务",
          no1 in [t["billNo"] for t in body["data"]["records"]], body["data"])

    # ================= M2-3-2 / M2-3-7 审批通过（或签） =================
    print("== M2-3-2 审批通过（或签） ==")
    status, body = call("GET", "/m2/requests/%d" % d1, token=manager1)
    check("M2-3-6 审批历史可见提交记录",
          len(body["data"]["approvalHistory"]) >= 2, body["data"]["approvalHistory"])
    status, body = call("POST", "/m2/requests/%d/approve" % d1, token=manager1)
    check("M2-3-2 主管一审批通过", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    check("M2-3-2 通过后为已审核+审批通过",
          body["data"]["lifecycleStatus"] == 3 and body["data"]["auditStatus"] == 2, body["data"])
    history = body["data"]["approvalHistory"]
    statuses = {h["approverName"]: h["taskStatusName"] for h in history}
    check("M2-3-7 或签：任一通过即推进，另一人任务失效",
          statuses.get("王强") == "已通过" and statuses.get("赵敏") == "已失效", statuses)

    # 第二人再审批 → 单据已审核通过，报不可推进（任务已失效或不在审批中）
    status, body = call("POST", "/m2/requests/%d/approve" % d1, token=manager2)
    check("M2-3-7 或签第二人不能重复推进", body.get("code") == "Unsupported", body)

    # 采购员不可审批（权限边界）
    status, body = call("POST", "/m2/requests/%d/approve" % d1, token=buyer1)
    check("权限边界：采购员不可审批", status == 403, status)

    # 已审核单不可撤回、不可再提交
    status, body = call("POST", "/m2/requests/%d/withdraw" % d1, token=buyer1)
    check("M2-3-4 已审核不可撤回", body.get("code") == "Unsupported", body)

    # ================= M2-3-3 / M2-3-4 驳回与撤回 =================
    print("== M2-3-3 驳回与撤回 ==")
    status, body = create_draft(buyer1, 1101, MARK + " 驳回测试")
    d3 = body.get("data")
    add_item(buyer1, d3, mat_a["id"], 30, sup_a["id"], est_price=5)
    status, body = call("POST", "/m2/requests/%d/submit" % d3, token=buyer1)
    check("驳回测试单提交", body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d3, token=buyer1)
    no3 = body["data"]["requestNo"]

    # 驳回必须填意见
    status, body = call("POST", "/m2/requests/%d/reject" % d3, token=manager1,
                        body={"opinion": ""})
    check("M2-3-3 驳回意见必填", body.get("code") == "InvalidParameter", body)

    # 主管一驳回：或签节点仍等待另一审批人
    status, body = call("POST", "/m2/requests/%d/reject" % d3, token=manager1,
                        body={"opinion": "数量偏大，先压一压"})
    check("M2-3-3 主管一驳回", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d3, token=buyer1)
    check("M2-3-3 或签单人驳回不否决整单（仍审批中）",
          body["data"]["lifecycleStatus"] == 2, body["data"])

    # 已有处理记录不可撤回
    status, body = call("POST", "/m2/requests/%d/withdraw" % d3, token=buyer1)
    check("M2-3-4 已有审批记录不可撤回", body.get("code") == "Unsupported", body)

    # 主管二也驳回 → 全部处理完毕无人通过 → 整单驳回
    status, body = call("POST", "/m2/requests/%d/reject" % d3, token=manager2,
                        body={"opinion": "同意压量"})
    check("M2-3-3 主管二驳回", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d3, token=buyer1)
    check("M2-3-3 或签全员驳回后整单驳回（生命周期回落草稿）",
          body["data"]["lifecycleStatus"] == 1 and body["data"]["auditStatus"] == 3, body["data"])

    # 已驳回单可编辑后重新提交（M2-1-3）
    status, body = add_item(buyer1, d3, mat_a["id"], 10, sup_a["id"], est_price=5)
    check("M2-1-3 已驳回单可加行", body.get("code") == "Success", body)
    status, body = call("POST", "/m2/requests/%d/submit" % d3, token=buyer1)
    check("M2-1-3 已驳回单可重新提交", body.get("code") == "Success", body)

    # 未处理时可撤回
    status, body = call("POST", "/m2/requests/%d/withdraw" % d3, token=buyer1)
    check("M2-3-4 未被审批时可撤回", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d3, token=buyer1)
    check("M2-3-4 撤回后回到草稿", body["data"]["lifecycleStatus"] == 1, body["data"])

    # 非申请人不可撤回（主管无 withdraw 权限）
    status, body = call("POST", "/m2/requests/%d/submit" % d3, token=buyer1)
    status, body = call("POST", "/m2/requests/%d/withdraw" % d3, token=manager1)
    check("权限边界：主管不可撤回", status == 403, status)

    # ================= M2-2-4 按行退回 =================
    print("== M2-2-4 按行退回 ==")
    status, body = create_draft(buyer1, 1101, MARK + " 退回测试")
    d4 = body.get("data")
    it1 = add_item(buyer1, d4, mat_a["id"], 100, sup_a["id"], est_price=12.5)[1]["data"]
    it2 = add_item(buyer1, d4, mat_b["id"], 40, sup_b["id"], est_price=20)[1]["data"]
    status, body = call("POST", "/m2/requests/%d/submit" % d4, token=buyer1)
    check("退回测试单提交", body.get("code") == "Success", body)

    # 意见必填
    status, body = call("POST", "/m2/requests/%d/return-items" % d4, token=manager1,
                        body={"itemIds": [it1], "opinion": ""})
    check("M2-2-4 退回意见必填", body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m2/requests/%d/return-items" % d4, token=manager1,
                        body={"itemIds": [it1], "opinion": "该行规格需确认"})
    check("M2-2-4 按行退回一行", status == 200 and body.get("code") == "Success", body)
    split_id = body.get("data")
    status, body = call("GET", "/m2/requests/%d" % d4, token=buyer1)
    check("M2-2-4 原单剩余行继续审批",
          body["data"]["lifecycleStatus"] == 2
          and [i["itemStatusName"] for i in body["data"]["items"]] == ["已退回", "待转"], body["data"])
    check("M2-2-4 原单金额只含剩余行（40×20=800）",
          abs(float(body["data"]["totalAmount"]) - 800) < 0.01, body["data"]["totalAmount"])
    status, body = call("GET", "/m2/requests/%d" % split_id, token=buyer1)
    check("M2-2-4 拆分单为驳回态草稿且含被退行",
          body["data"]["lifecycleStatus"] == 1 and body["data"]["auditStatus"] == 3
          and len(body["data"]["items"]) == 1, body["data"])
    check("M2-2-4 拆分单金额已重算（100×12.5=1250）",
          abs(float(body["data"]["totalAmount"]) - 1250) < 0.01, body["data"]["totalAmount"])

    # 原单剩余行由另一主管通过
    status, body = call("POST", "/m2/requests/%d/approve" % d4, token=manager2)
    check("M2-2-4 余下部分可被另一审批人通过", body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d4, token=buyer1)
    check("M2-2-4 原单审批通过", body["data"]["auditStatus"] == 2, body["data"])

    # ================= M2-4 转订单 =================
    print("== M2-4 申请转订单 ==")
    # 未审核不可转
    status, body = call("POST", "/m2/requests/transfer", token=buyer1,
                        body={"sources": [{"requestId": d3, "itemIds": None}]})
    check("M2-4-1 未审核单不可转订单", body.get("code") == "Unsupported", body)

    # 未指定建议供应商不可转：先造一行无供应商的已审核单
    status, body = create_draft(buyer1, 1101, MARK + " 无供应商")
    d5 = body.get("data")
    status, body = call("POST", "/m2/requests/%d/items" % d5, token=buyer1, body={
        "materialId": mat_a["id"], "quantity": 10, "requireDate": "2026-12-01",
        "ownerType": 1, "remark": MARK + " 无供应商行"})
    check("无供应商行可先存草稿", body.get("code") == "Success", body)
    status, body = call("POST", "/m2/requests/%d/submit" % d5, token=buyer1)
    status, body = call("POST", "/m2/requests/%d/approve" % d5, token=manager1)
    check("无供应商单可审批通过", body.get("code") == "Success", body)
    status, body = call("POST", "/m2/requests/transfer", token=buyer1,
                        body={"sources": [{"requestId": d5, "itemIds": None}]})
    check("M2-4-1 未指定建议供应商不可转", body.get("code") == "InvalidParameter", body)

    # 按行转订单：D4 剩余行 + 拆分单（重提审批后）→ 不同供应商出两张单
    status, body = call("POST", "/m2/requests/%d/submit" % split_id, token=buyer1)
    check("拆分单重新提交", body.get("code") == "Success", body)
    status, body = call("POST", "/m2/requests/%d/approve" % split_id, token=manager1)
    check("拆分单审批通过", body.get("code") == "Success", body)

    status, body = call("POST", "/m2/requests/transfer", token=buyer1, body={
        "sources": [{"requestId": d4, "itemIds": None},
                    {"requestId": split_id, "itemIds": None}]})
    check("M2-4-2 批量转订单（两单不同供应商出两单）",
          status == 200 and body.get("code") == "Success"
          and len(body["data"]["orders"]) == 2, body)
    order_nos = [o["orderNo"] for o in body["data"]["orders"]]
    check("M2-4-2 订单号按规则生成",
          all(no.startswith("CG-DD-" + time.strftime("%Y%m%d") + "-") for no in order_nos), order_nos)
    merged = [o for o in body["data"]["orders"] if o["itemCount"] == 2]
    check("M2-4-2 同一供应商多行合并", len(merged) == 0, body["data"]["orders"])

    # M2-4-3 已转数量回写
    status, body = call("GET", "/m2/requests/%d" % d4, token=buyer1)
    it = body["data"]["items"][1]
    check("M2-4-3 已转数量回写且行状态已转完",
          float(it["transferredQty"]) == 40 and it["itemStatusName"] == "已转完", it)
    status, body = call("GET", "/m2/requests/%d" % split_id, token=buyer1)
    it = body["data"]["items"][0]
    check("M2-4-3 拆分单行也已转完",
          float(it["transferredQty"]) == 100 and it["itemStatusName"] == "已转完", it)

    # 同供应商两行合并：一单两行同物料同供应商，一次转出一张两行订单
    status, body = create_draft(buyer1, 1101, MARK + " 合并测试")
    d7 = body.get("data")
    add_item(buyer1, d7, mat_a["id"], 5, sup_a["id"], est_price=12.5)
    add_item(buyer1, d7, mat_a["id"], 15, sup_a["id"], est_price=12.5)
    call("POST", "/m2/requests/%d/submit" % d7, token=buyer1)
    call("POST", "/m2/requests/%d/approve" % d7, token=manager1)
    status, body = call("POST", "/m2/requests/transfer", token=buyer1,
                        body={"sources": [{"requestId": d7, "itemIds": None}]})
    check("M2-4-2 同供应商两行合并为一张订单",
          status == 200 and body.get("code") == "Success"
          and len(body["data"]["orders"]) == 1
          and body["data"]["orders"][0]["itemCount"] == 2, body)

    # ================= M2-3-5 / M2-4-4 整单关闭 =================
    print("== M2-3-5 整单关闭 ==")
    # D1 已审核且已全部转单（200×12.5 一行）——先转掉再关
    status, body = call("POST", "/m2/requests/transfer", token=buyer1,
                        body={"sources": [{"requestId": d1, "itemIds": None}]})
    check("D1 转订单", body.get("code") == "Success", body)
    status, body = call("POST", "/m2/requests/%d/close" % d1, token=manager1, body={"reason": ""})
    check("M2-3-5 关闭原因必填", body.get("code") == "InvalidParameter", body)
    status, body = call("POST", "/m2/requests/%d/close" % d1, token=manager1,
                        body={"reason": "需求变更，余量作废"})
    check("M2-3-5 已审核单可关闭", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d1, token=buyer1)
    check("M2-3-5 关闭后为已关闭", body["data"]["lifecycleStatus"] == 4, body["data"])

    # 未审核单不可关闭
    status, body = call("POST", "/m2/requests/%d/close" % d3, token=manager1,
                        body={"reason": "x"})
    check("M2-3-5 未审核单不可关闭", body.get("code") == "Unsupported", body)

    # M2-4-4 有未转量时关闭：D6 只转一行，剩一行，关闭后剩余行置已关闭
    status, body = create_draft(buyer1, 1101, MARK + " 部分转单测试")
    d6 = body.get("data")
    add_item(buyer1, d6, mat_a["id"], 10, sup_a["id"], est_price=12.5)
    add_item(buyer1, d6, mat_a["id"], 20, sup_a["id"], est_price=12.5)
    call("POST", "/m2/requests/%d/submit" % d6, token=buyer1)
    call("POST", "/m2/requests/%d/approve" % d6, token=manager1)
    status, body = call("GET", "/m2/requests/%d" % d6, token=buyer1)
    first_item = body["data"]["items"][0]["id"]
    status, body = call("POST", "/m2/requests/transfer", token=buyer1,
                        body={"sources": [{"requestId": d6, "itemIds": [first_item]}]})
    check("M2-4-1 按行勾选转订单（只转一行）", body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d6, token=buyer1)
    check("M2-4-3 部分转单行状态为部分已转",
          body["data"]["items"][0]["itemStatusName"] == "已转完"
          and body["data"]["items"][1]["itemStatusName"] == "待转", body["data"]["items"])
    status, body = call("POST", "/m2/requests/%d/close" % d6, token=manager1,
                        body={"reason": "余量不再采购"})
    check("M2-4-4 有未转量仍可关闭（前端已提示确认）", body.get("code") == "Success", body)
    status, body = call("GET", "/m2/requests/%d" % d6, token=buyer1)
    names = [i["itemStatusName"] for i in body["data"]["items"]]
    check("M2-4-4 关闭后剩余行置已关闭、已转完行保持",
          body["data"]["lifecycleStatus"] == 4 and names == ["已转完", "已关闭"], names)

    # ================= 权限与范围边界 =================
    print("== 权限与范围边界 ==")
    status, body = call("GET", "/m2/requests/page", token=admin)
    check("权限边界：管理员无申请单查询权", status == 403, status)
    status, body = call("POST", "/m2/requests", token=manager1,
                        body={"deptId": 1101, "remark": "x"})
    check("权限边界：主管不可新建申请单", status == 403, status)
    status, body = call("GET", "/m2/requests/page?pageNo=1&pageSize=50", token=keeper)
    check("范围边界：仓管员无部门范围，列表失败关闭为空",
          status == 200 and body["data"]["total"] == 0, body["data"]["total"])
    status, body = call("GET", "/m2/requests/todo", token=buyer1)
    check("权限边界：采购员无待办查询权", status == 403, status)

    # 主管跨部门数据：caigou02 的 1102 单对 caigou01 不可见，对主管可见
    status, body = call("GET", "/m2/requests/page?pageNo=1&pageSize=50", token=buyer1)
    visible_depts = {r["deptId"] for r in body["data"]["records"]}
    check("范围边界：采购员只见本部门单据", visible_depts <= {1101}, visible_depts)

    print()
    print("=" * 60)
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  失败项: " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
