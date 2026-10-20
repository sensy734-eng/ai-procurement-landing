#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 1 冒烟测试：认证、会话、权限拦截、数据范围。

用法：
    python scripts/smoke_round1.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
退出码 0 表示全部通过，非 0 表示有断言失败。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
passed = 0
failed = []


def call(method, path, token=None, body=None):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            return error.code, json.loads(raw)
        except json.JSONDecodeError:
            return error.code, {"raw": raw}


def check(name, condition, detail=""):
    global passed
    if condition:
        passed += 1
        print("PASS  " + name)
    else:
        failed.append(name)
        print("FAIL  " + name + ("  -> " + detail if detail else ""))


def login(username, password):
    return call("POST", "/auth/login", body={"username": username, "password": password})


def main():
    # 1 登录成功，返回会话与权限点
    status, body = login("admin", "123456")
    check("admin 登录返回 200", status == 200, str(status))
    check("admin 登录结果码 Success", body.get("code") == "Success", body.get("code"))
    admin_token = (body.get("data") or {}).get("token")
    check("admin 拿到会话令牌", bool(admin_token))
    admin_perms = (body.get("data") or {}).get("permissions") or []
    check("admin 持有动作级权限点", len(admin_perms) > 0, str(len(admin_perms)))
    admin_scope = (body.get("data") or {}).get("dataScope") or {}
    check("admin 数据范围为全量", admin_scope.get("all") is True, str(admin_scope))

    # 2 采购员登录，数据范围应为部门 1101
    status, body = login("caigou01", "123456")
    buyer_token = (body.get("data") or {}).get("token")
    buyer_scope = (body.get("data") or {}).get("dataScope") or {}
    check("caigou01 登录成功", status == 200 and body.get("code") == "Success", str(status))
    check("caigou01 数据范围不是全量", buyer_scope.get("all") is False, str(buyer_scope))
    check("caigou01 部门范围为 1101", buyer_scope.get("deptIds") == [1101], str(buyer_scope.get("deptIds")))

    # 3 仓管员登录，数据范围应为仓库 1 与 2
    status, body = login("cangku01", "123456")
    keeper_scope = (body.get("data") or {}).get("dataScope") or {}
    check("cangku01 仓库范围为 1 和 2",
          sorted(keeper_scope.get("warehouseIds") or []) == [1, 2],
          str(keeper_scope.get("warehouseIds")))

    # 4 密码错误返回 InvalidParameter
    status, body = login("admin", "wrong-password")
    check("错误密码返回 InvalidParameter", body.get("code") == "InvalidParameter", str(body.get("code")))

    # 5 未带令牌访问受保护接口返回 401
    status, body = call("GET", "/m11/users")
    check("未登录访问返回 401", status == 401, str(status))
    check("未登录结果码 NoPermission", body.get("code") == "NoPermission", str(body.get("code")))

    # 6 采购员调用户管理接口应 403
    status, body = call("GET", "/m11/users", token=buyer_token)
    check("采购员访问用户管理返回 403", status == 403, str(status))
    check("越权结果码 NoPermission", body.get("code") == "NoPermission", str(body.get("code")))

    # 7 管理员调用户列表应看到 6 个演示用户
    status, body = call("GET", "/m11/users?pageNo=1&pageSize=10", token=admin_token)
    total = ((body.get("data") or {}).get("total"))
    check("管理员用户列表返回 Success", body.get("code") == "Success", str(body.get("code")))
    check("用户总数为 6", total == 6, str(total))

    # 8 字典接口四角色可用
    status, body = call("GET", "/m1/dicts/dept/items", token=buyer_token)
    items = body.get("data") or []
    check("字典接口返回部门项", body.get("code") == "Success" and len(items) > 0, str(len(items)))

    # 9 会话保持：带令牌取当前用户
    status, body = call("GET", "/auth/me", token=buyer_token)
    check("/auth/me 返回当前用户",
          body.get("code") == "Success" and (body.get("data") or {}).get("user", {}).get("username") == "caigou01",
          str(body.get("code")))

    # 10 退出后旧令牌失效
    status, _ = call("POST", "/auth/logout", token=buyer_token)
    check("退出登录返回 Success", status == 200, str(status))
    status, body = call("GET", "/auth/me", token=buyer_token)
    check("退出后旧令牌返回 401", status == 401, str(status))

    print()
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        print("失败项：" + "；".join(failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
