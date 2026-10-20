#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 2 冒烟测试：M11 平台与权限管理。

覆盖功能点：
    M11-2-1 用户列表与检索      M11-2-2 新增与编辑用户
    M11-2-3 用户启用停用        M11-2-4 重置密码
    M11-2-5 角色列表与维护      M11-2-6 用户角色分配
    M11-3-1 权限点列表          M11-3-2 角色授权
    M11-3-3 数据范围授权        M11-3-5 授权变更留痕
    M11-4-1 价格偏离阈值        M11-4-2 库存预警下限
    M11-4-3 单据编号规则        M11-4-4 操作日志检索
    M11-4-5 智能查询审计列表    M11-4-6 审计详情与导出
    M11-5-1 审批流程配置        M11-5-2 审批人指定

用法：python scripts/smoke_round2.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
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

# 每次运行使用唯一后缀，脚本可反复执行，不与上一轮的测试数据冲突
SUFFIX = str(int(time.time()))[-6:]
TEST_USERNAME = "smoke" + SUFFIX
TEST_ROLE_CODE = "smoke_role_" + SUFFIX


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


def login(username, password):
    return call("POST", "/auth/login", body={"username": username, "password": password})


def main():
    status, body = login("admin", "123456")
    check("admin 登录成功", status == 200 and body.get("code") == "Success", body)
    admin = (body.get("data") or {}).get("token")
    if not admin:
        print("admin 登录失败，后续用例无法执行")
        return 1

    # ---------- M11-2-1 用户列表与检索 ----------
    status, body = call("GET", "/m11/users?pageNo=1&pageSize=10", admin)
    check("M11-2-1 用户列表返回 Success", status == 200 and body.get("code") == "Success", body.get("code"))
    check("M11-2-1 列表含演示账号", (body.get("data") or {}).get("total", 0) >= 6,
          (body.get("data") or {}).get("total"))
    status, body = call("GET", "/m11/users?keyword=caigou", admin)
    records = (body.get("data") or {}).get("records") or []
    check("M11-2-1 关键词检索命中", len(records) >= 1 and "caigou" in records[0].get("username", ""), records)
    first_user = records[0] if records else {}

    # ---------- M11-2-2 新增用户 ----------
    new_user = {
        "username": TEST_USERNAME,
        "realName": "冒烟测试员",
        "deptId": 1101,
        "phone": "13800000000",
        "email": "smoke@demo.local",
        "remark": "轮次 2 冒烟数据",
        "roleIds": [1],
    }
    status, body = call("POST", "/m11/users", admin, new_user)
    created_ok = status == 200 and body.get("code") == "Success"
    check("M11-2-2 新增用户成功", created_ok, body)
    created = body.get("data") or {}
    new_id = created.get("userId")
    new_pwd = created.get("initialPassword")
    check("M11-2-4 新增返回一次性初始密码", bool(new_pwd), created)

    status, body = call("POST", "/m11/users", admin, new_user)
    check("M11-2-2 账号重复被拒", body.get("code") == "InvalidParameter", body.get("code"))

    # 新用户用初始密码登录，且首登需改密
    status, body = login(TEST_USERNAME, new_pwd or "")
    check("M11-2-4 初始密码可登录", status == 200 and body.get("code") == "Success", body)
    check("M11-2-4 登录返回首登必改标记", (body.get("data") or {}).get("pwdNeedChange") is True,
          (body.get("data") or {}).get("pwdNeedChange"))
    new_token = (body.get("data") or {}).get("token")

    # ---------- M11-2-2 编辑用户 ----------
    if new_id:
        edit = dict(new_user)
        edit["username"] = TEST_USERNAME
        edit["realName"] = "冒烟测试员二号"
        edit["roleIds"] = [1, 3]
        status, body = call("PUT", "/m11/users/%d" % new_id, admin, edit)
        check("M11-2-2 编辑用户成功", body.get("code") == "Success", body.get("code"))
        status, body = call("GET", "/m11/users/%d" % new_id, admin)
        detail = body.get("data") or {}
        check("M11-2-2 编辑后姓名已更新", detail.get("realName") == "冒烟测试员二号", detail.get("realName"))
        check("M11-2-6 编辑带角色后为两个角色", len(detail.get("roleIds") or []) == 2, detail.get("roleIds"))

    # ---------- M11-2-6 用户角色分配 ----------
    if new_id:
        status, body = call("PUT", "/m11/users/%d/roles" % new_id, admin, {"roleIds": [2]})
        check("M11-2-6 角色分配成功", body.get("code") == "Success", body.get("code"))
        status, body = call("GET", "/m11/users/%d" % new_id, admin)
        check("M11-2-6 角色已替换为单个", len((body.get("data") or {}).get("roleIds") or []) == 1,
              (body.get("data") or {}).get("roleIds"))
        status, body = call("PUT", "/m11/users/%d/roles" % new_id, admin, {"roleIds": []})
        check("M11-2-6 清空角色被拒", body.get("code") == "InvalidParameter", body.get("code"))

    # ---------- M11-2-3 启用停用 ----------
    if new_id:
        status, body = call("PATCH", "/m11/users/%d/status" % new_id, admin, {"status": 2})
        check("M11-2-3 停用成功", body.get("code") == "Success", body.get("code"))
        status, body = login(TEST_USERNAME, new_pwd or "")
        check("M11-2-3 停用后不可登录", status == 403 or body.get("code") == "NoPermission", body.get("code"))
        if new_token:
            status, body = call("GET", "/auth/me", new_token)
            check("M11-2-3 停用后旧会话被拒", status == 401, status)
        status, body = call("PATCH", "/m11/users/%d/status" % new_id, admin, {"status": 1})
        check("M11-2-3 启用成功", body.get("code") == "Success", body.get("code"))
        status, body = call("PATCH", "/m11/users/1/status", admin, {"status": 2})
        check("M11-2-3 不能停用自己", body.get("code") == "InvalidParameter", body.get("code"))

    # ---------- M11-2-4 重置密码 ----------
    if new_id:
        status, body = call("POST", "/m11/users/%d/reset-password" % new_id, admin)
        reset = body.get("data") or {}
        check("M11-2-4 重置密码返回初始密码", body.get("code") == "Success" and bool(reset.get("initialPassword")), body)
        status, body = login(TEST_USERNAME, reset.get("initialPassword") or "")
        check("M11-2-4 重置后可登录", body.get("code") == "Success", body.get("code"))

    # ---------- M11-2-5 角色列表与维护 ----------
    status, body = call("GET", "/m11/roles", admin)
    roles = body.get("data") or []
    check("M11-2-5 角色列表四个内置角色", len(roles) >= 4, len(roles))
    code_by_name = {r.get("roleCode"): r for r in roles}
    check("M11-2-5 内置角色标记正确",
          all(r.get("isSystem") == 1 for r in roles if r.get("roleCode") in
              ("buyer", "buyer_manager", "warehouse_keeper", "admin")), roles)

    status, body = call("POST", "/m11/roles", admin,
                        {"roleCode": TEST_ROLE_CODE, "roleName": "冒烟角色", "status": 1})
    check("M11-2-5 新增角色成功", body.get("code") == "Success", body)
    smoke_role_x_id = body.get("data")

    status, body = call("POST", "/m11/roles", admin,
                        {"roleCode": TEST_ROLE_CODE, "roleName": "重复编码", "status": 1})
    check("M11-2-5 角色编码重复被拒", body.get("code") == "InvalidParameter", body.get("code"))

    status, body = call("POST", "/m11/roles", admin,
                        {"roleCode": "smoke_all", "roleName": "全量角色", "dataScopeAll": 1})
    check("M11-2-5 新角色不能持全量范围", body.get("code") == "InvalidParameter", body.get("code"))

    if smoke_role_x_id:
        status, body = call("PUT", "/m11/roles/%d" % smoke_role_x_id, admin,
                            {"roleCode": TEST_ROLE_CODE, "roleName": "冒烟角色改名", "status": 1})
        check("M11-2-5 编辑角色成功", body.get("code") == "Success", body.get("code"))

    buyer = code_by_name.get("buyer") or {}
    if buyer.get("id"):
        status, body = call("PUT", "/m11/roles/%d" % buyer["id"], admin,
                            {"roleCode": "smoke_rename", "roleName": "采购员", "status": 1})
        check("M11-2-5 内置角色编码不可改", body.get("code") == "InvalidParameter", body.get("code"))

    # ---------- M11-3-1 权限点列表 ----------
    status, body = call("GET", "/m11/permissions", admin)
    groups = body.get("data") or []
    check("M11-3-1 权限点按模块分组 12 组", len(groups) == 12, len(groups))
    total_perms = sum(len(g.get("permissions") or []) for g in groups)
    check("M11-3-1 权限点总数 156", total_perms == 156, total_perms)
    m7 = next((g for g in groups if g.get("moduleCode") == "M7"), {})
    check("M11-3-1 智能模块只读动作",
          all(p.get("actionType") in ("list", "view", "query", "audit")
              for p in (m7.get("permissions") or [])), m7)

    # ---------- M11-3-2 角色授权 ----------
    if smoke_role_x_id:
        all_perm_ids = [p["id"] for g in groups for p in g["permissions"]]
        grant_ids = all_perm_ids[:3]
        status, body = call("PUT", "/m11/roles/%d/permissions" % smoke_role_x_id, admin,
                            {"permIds": grant_ids})
        check("M11-3-2 角色授权成功", body.get("code") == "Success", body.get("code"))
        status, body = call("GET", "/m11/roles/%d/permissions" % smoke_role_x_id, admin)
        check("M11-3-2 授权结果可回读", sorted(body.get("data") or []) == sorted(grant_ids),
              body.get("data"))

        # 智能模块只读约束：拿一个智能模块的写动作点去授权应被拒
        ai_write = None
        for g in groups:
            if g.get("moduleCode") in ("M7", "M8", "M9"):
                for p in g.get("permissions") or []:
                    if p.get("actionType") not in ("list", "view", "query", "audit"):
                        ai_write = p
        if ai_write:
            status, body = call("PUT", "/m11/roles/%d/permissions" % smoke_role_x_id, admin,
                                {"permIds": [ai_write["id"]]})
            check("M11-3-2 智能模块写权限被拒", body.get("code") == "InvalidParameter", body.get("code"))

    # ---------- M11-3-3 数据范围授权 ----------
    status, body = call("GET", "/m11/data-scopes/options", admin)
    options = body.get("data") or {}
    check("M11-3-3 范围选项含三维度",
          bool(options.get("warehouses")) and bool(options.get("depts")) and bool(options.get("owners")),
          options)
    warehouse_id = (options.get("warehouses") or [{}])[0].get("value")
    dept_id = (options.get("depts") or [{}])[0].get("value")
    owner_no = (options.get("owners") or [{}])[0].get("value")

    if buyer.get("id") and warehouse_id and dept_id:
        status, body = call("GET", "/m11/data-scopes?subjectType=2&subjectId=%d" % buyer["id"], admin)
        before_scope = body.get("data") or []
        status, body = call("PUT", "/m11/data-scopes", admin, {
            "subjectType": 2, "subjectId": buyer["id"],
            "items": [
                {"scopeDimension": 1, "scopeValue": warehouse_id},
                {"scopeDimension": 2, "scopeValue": dept_id},
            ] + ([{"scopeDimension": 3, "scopeValue": owner_no}] if owner_no else []),
        })
        check("M11-3-3 数据范围保存成功", body.get("code") == "Success", body.get("code"))
        status, body = call("GET", "/m11/data-scopes?subjectType=2&subjectId=%d" % buyer["id"], admin)
        after_scope = body.get("data") or []
        check("M11-3-3 范围保存后条数正确", len(after_scope) == (3 if owner_no else 2), after_scope)
        check("M11-3-3 范围值带可读名称", all(s.get("scopeValueName") for s in after_scope), after_scope)

        status, body = call("PUT", "/m11/data-scopes", admin, {
            "subjectType": 2, "subjectId": buyer["id"],
            "items": [{"scopeDimension": 1, "scopeValue": "999999"}],
        })
        check("M11-3-3 不存在的仓库被拒", body.get("code") == "NotFound", body.get("code"))

        # 还原原范围，避免影响其余用例
        restore = [{"scopeDimension": s["scopeDimension"], "scopeValue": s["scopeValue"]} for s in before_scope]
        call("PUT", "/m11/data-scopes", admin,
             {"subjectType": 2, "subjectId": buyer["id"], "items": restore})

    # ---------- M11-3-5 授权变更留痕 ----------
    status, body = call("GET", "/m11/operation-logs?moduleCode=M11&pageSize=50", admin)
    logs = (body.get("data") or {}).get("records") or []
    actions = [row.get("action") for row in logs]
    check("M11-3-5 授权变更写入日志", "grant" in actions, actions[:10])
    check("M11-4-4 用户新增写入日志", "create" in actions, actions[:10])
    grant_log = next((r for r in logs if r.get("action") == "grant"), {})
    check("M11-3-5 日志含修改前后值", "->" in (grant_log.get("detail") or ""), grant_log.get("detail"))

    # ---------- M11-4-1 价格偏离阈值 ----------
    status, body = call("GET", "/m11/params?group=price", admin)
    params = body.get("data") or []
    param_by_code = {p.get("paramCode"): p for p in params}
    check("M11-4-1 偏离阈值参数存在",
          "deviation_threshold_focus" in param_by_code and "deviation_threshold_significant" in param_by_code,
          list(param_by_code))

    status, body = call("PUT", "/m11/params/deviation_threshold_focus", admin, {"paramValue": "0.2"})
    check("M11-4-1 关注阈值不得大于显著阈值", body.get("code") == "InvalidParameter", body.get("code"))
    status, body = call("PUT", "/m11/params/deviation_threshold_focus", admin, {"paramValue": "0.08"})
    check("M11-4-1 合法阈值可保存", body.get("code") == "Success", body.get("code"))
    call("PUT", "/m11/params/deviation_threshold_focus", admin, {"paramValue": "0.05"})

    # ---------- M11-4-2 库存预警下限 ----------
    status, body = call("GET", "/m11/params?group=stock", admin)
    check("M11-4-2 库存预警下限参数存在",
          any(p.get("paramCode") == "stock_alert_default" for p in (body.get("data") or [])), body.get("data"))
    status, body = call("PUT", "/m11/params/stock_alert_default", admin, {"paramValue": "-1"})
    check("M11-4-2 负数下限被拒", body.get("code") == "InvalidParameter", body.get("code"))

    # ---------- M11-4-3 单据编号规则 ----------
    status, body = call("GET", "/m11/params?group=bill_no", admin)
    bill_params = body.get("data") or []
    check("M11-4-3 四类单据编号规则齐备", len(bill_params) == 4, len(bill_params))
    status, body = call("PUT", "/m11/params/bill_no_rule_request", admin, {"paramValue": "CG-SQ-{yyyyMMdd}"})
    check("M11-4-3 缺流水段的规则被拒", body.get("code") == "InvalidParameter", body.get("code"))
    status, body = call("PUT", "/m11/params/bill_no_rule_request", admin, {"paramValue": "CG-SQ-{yyyyMMdd}-{seq:5}"})
    check("M11-4-3 合法规则可保存", body.get("code") == "Success", body.get("code"))
    call("PUT", "/m11/params/bill_no_rule_request", admin, {"paramValue": "CG-SQ-{yyyyMMdd}-{seq:4}"})

    # ---------- M11-4-4 操作日志导出 ----------
    status, content, headers = call("GET", "/m11/operation-logs/export", admin, raw=True)
    check("M11-4-4 日志导出返回 200", status == 200, status)
    check("M11-4-4 导出为 CSV 且带 BOM", content[:3] == b"\xef\xbb\xbf", content[:6])
    check("M11-4-4 导出含表头", "操作人账号".encode("utf-8") in content, content[:80])

    # ---------- M11-4-5 / M11-4-6 智能审计 ----------
    status, body = call("GET", "/m11/query-audits", admin)
    check("M11-4-5 审计列表可访问", body.get("code") == "Success", body.get("code"))
    status, content, _ = call("GET", "/m11/query-audits/export", admin, raw=True)
    check("M11-4-6 审计导出可访问", status == 200, status)
    status, body = call("GET", "/m11/query-audits/999999", admin)
    check("M11-4-6 不存在的审计返回 NotFound", body.get("code") == "NotFound", body.get("code"))

    # ---------- M11-5-1 / M11-5-2 审批流程配置 ----------
    status, body = call("GET", "/m11/approval-flows/options", admin)
    flow_options = body.get("data") or {}
    check("M11-5-2 审批人候选含角色与用户",
          bool(flow_options.get("roles")) and bool(flow_options.get("users")), flow_options)

    buyer_role_id = (buyer.get("id") or 1)
    flow_body = {"nodes": [
        {"nodeSeq": 1, "nodeName": "采购主管审批", "signType": 1,
         "approvers": [{"approverType": 1, "roleId": buyer_role_id}]},
        {"nodeSeq": 2, "nodeName": "财务会签", "signType": 2,
         "approvers": [{"approverType": 1, "roleId": buyer_role_id},
                       {"approverType": 2, "userId": 3}]},
    ]}
    status, body = call("PUT", "/m11/approval-flows/2", admin, flow_body)
    check("M11-5-1 审批流程保存成功", body.get("code") == "Success", body)

    status, body = call("GET", "/m11/approval-flows/2", admin)
    nodes = body.get("data") or []
    check("M11-5-1 节点按序号返回两个", len(nodes) == 2, len(nodes))
    check("M11-5-2 第二节点两个审批人", len((nodes[1] if len(nodes) > 1 else {}).get("approvers") or []) == 2,
          nodes)
    check("M11-5-2 审批人显示名已解析",
          all(a.get("displayName") for n in nodes for a in (n.get("approvers") or [])), nodes)

    bad_flow = {"nodes": [
        {"nodeSeq": 1, "nodeName": "第一节点", "signType": 1,
         "approvers": [{"approverType": 1, "roleId": buyer_role_id}]},
        {"nodeSeq": 3, "nodeName": "跳号节点", "signType": 1,
         "approvers": [{"approverType": 1, "roleId": buyer_role_id}]},
    ]}
    status, body = call("PUT", "/m11/approval-flows/2", admin, bad_flow)
    check("M11-5-1 节点序号跳号被拒", body.get("code") == "InvalidParameter", body.get("code"))

    empty_approver = {"nodes": [
        {"nodeSeq": 1, "nodeName": "无审批人", "signType": 1, "approvers": []},
    ]}
    status, body = call("PUT", "/m11/approval-flows/2", admin, empty_approver)
    check("M11-5-2 空审批人被拒", body.get("code") == "InvalidParameter", body.get("code"))

    status, body = call("PUT", "/m11/approval-flows/2", admin, flow_body)
    check("M11-5-1 非法请求后配置仍可用", body.get("code") == "Success", body.get("code"))

    # ---------- 越权校验 ----------
    status, body = login("caigou01", "123456")
    buyer_token = (body.get("data") or {}).get("token")
    status, body = call("GET", "/m11/users", buyer_token)
    check("越权 采购员访问用户管理被拒", status == 403, status)
    status, body = call("GET", "/m11/params?group=price", buyer_token)
    check("越权 采购员访问参数配置被拒", status == 403, status)
    status, body = call("GET", "/m11/approval-flows/2", buyer_token)
    check("越权 采购员访问审批配置被拒", status == 403, status)

    print()
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  - " + name)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
