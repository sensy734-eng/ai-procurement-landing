#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 8 冒烟测试：M6 价格参考与审批辅助 + M3-6 价格偏离关注。

覆盖功能点：
    M6-1-1 参考价维护（查询/详情/版本历史；改价=新版本，旧版留痕）
    M6-1-2 历史采购均价统计（金额加权，快照刷新后查询）
    M6-1-3 最近一次成交价（含来源单号溯源 M6-4-2）
    M6-1-4 参考价与成交统计严格分离（R6，不把参考价当报价）
    M6-2   价格口径与阈值配置（sys_param price 分组，阈值驱动偏离等级）
    M6-4-1 审批证据面板（单价/参考价/偏离/历史成交一屏）
    M3-6   偏离计算（建行/改行按当前生效参考价，rate=(单价−参考价)/参考价）
    M3-6   偏离等级（|rate| 与关注/显著阈值比较，整单取最高）
    M3-6   偏离关注清单（订单维度 + 等级/物料筛选 + 汇总）
    M3-6   偏离重算（参考价变更后按当前生效版同步历史行）
    口径：版本链（立即生效停用旧版、未来预售版并存）、as-of 还原下单时点、
          无参考价不判定、无成交样本不伪造、权限边界。

权限口径（与 sys_role_permission 种子一致）：
    m6:price:list/view  —— admin / buyer / buyer_manager
    m6:price:create/update —— buyer_manager（主管）
    m6:stat:view —— buyer / buyer_manager
    m6:param:config —— admin（统计刷新挂此点）
    m6:evidence:view —— buyer_manager（审批人）
    仓管员 cangku01 不持任何 m6 权限。

用法：python scripts/smoke_round8.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本，
且已执行 db/21_clean_smoke_round8.sql（演示基线：参考价 4 行均 V1 生效、
统计快照 4 行、订单 1/2 及其偏离种子值）。
测试数据落 smk8 前缀，跑完执行 db/21_clean_smoke_round8.sql 复原演示规模。
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
MARK = "smk8" + SUFFIX
TODAY = time.strftime("%Y-%m-%d")
# 演示基线口径（21_clean 执行后）
MAT1 = 1          # M-1001，参考价 1.20，SUP-001 可供
MAT7 = 7          # M-1007，无参考价，SUP-001 可供（不判定用例）
SUP1 = 1          # SUP-001，供 M-1001 / M-1007
REF_V1_MAT1 = 1.20  # 物料 1 种子参考价（V1）
FOCUS = 0.05      # 关注阈值（sys_param）
SIG = 0.15        # 显著阈值（sys_param）


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


def create_ref(token, material_id, price, effective_date, remark=None):
    payload = {"materialId": material_id, "refPrice": price, "effectiveDate": effective_date}
    if remark:
        payload["remark"] = remark
    return call("POST", "/m6/references", token=token, body=payload)


def create_order(token, remark=MARK):
    return call("POST", "/m3/orders", token=token, body={
        "supplierId": SUP1, "deliveryDate": "2026-12-20",
        "deliveryPlace": "台州市演示工业园 1 号库", "settlementType": 1, "remark": remark})


def add_item(token, order_id, material_id, price, qty=10):
    return call("POST", "/m3/orders/%d/items" % order_id, token=token, body={
        "materialId": material_id, "quantity": qty, "unitPrice": price, "taxRate": 0.13,
        "deliveryDate": "2026-12-20", "deliveryPlace": "台州市演示工业园 1 号库"})


def order_item_of(order_id, item_id, token):
    status, body = call("GET", "/m3/orders/%d" % order_id, token=token)
    for row in body["data"]["items"]:
        if row["id"] == item_id:
            return row, body["data"]
    return None, body["data"]


def main():
    manager = token_of("zhuguan01")  # 采购主管：参考价维护、证据面板、重算
    buyer = token_of("caigou01")     # 采购员：参考价查询、统计、偏离清单、建单
    admin = token_of("admin")        # 管理员：统计刷新（param:config）
    keeper = token_of("cangku01")    # 仓管员：无任何 m6 权限

    # ================= M6-1-1 参考价查询与版本链 =================
    print("== M6-1-1 参考价维护与版本策略 ==")
    status, body = call("GET", "/m6/references?pageNo=1&pageSize=10", token=manager)
    check("M6-1-1 参考价列表（种子 4 行均 V1 生效）",
          status == 200 and body.get("code") == "Success"
          and body["data"]["total"] == 4
          and all(r["priceVersion"] == 1 and r["status"] == 1 and r["current"] is True
                  for r in body["data"]["records"]),
          (status, body.get("message")))

    status, body = call("GET", "/m6/references/1", token=manager)
    check("M6-1-1 详情含版本历史（V1=1.20 生效）",
          status == 200 and body.get("code") == "Success"
          and f(body["data"]["reference"]["refPrice"]) == REF_V1_MAT1
          and len(body["data"]["versions"]) == 1,
          (status, body.get("message")))

    # 立即生效新版本：V1 停用、V2 接任
    status, body = create_ref(manager, MAT1, 1.50, TODAY, MARK + " V2")
    v2_id = body.get("data")
    check("M6-1-1 新建参考价生成新版本（V2=1.50 立即生效）",
          status == 200 and body.get("code") == "Success" and v2_id is not None,
          (status, body.get("message")))
    status, body = call("GET", "/m6/references/versions/1", token=manager)
    versions = body["data"]
    check("M6-1-1 版本链：V1 自动停用、V2 当前生效、历史留痕",
          [v["priceVersion"] for v in versions] == [2, 1]
          and versions[0]["status"] == 1 and versions[0]["current"] is True
          and versions[1]["status"] == 2 and versions[1]["current"] is False
          and f(versions[1]["refPrice"]) == REF_V1_MAT1,
          [(v["priceVersion"], v["refPrice"], v["status"], v["current"]) for v in versions])

    # 再建立即生效版 V3：只停「立即生效」旧版
    status, body = create_ref(manager, MAT1, 1.80, TODAY)
    v3_id = body.get("data")
    check("M6-1-1 再建立即生效版 V3=1.80（V2 停用）",
          status == 200 and body.get("code") == "Success", (status, body.get("message")))

    # 未来生效预售版：与当前版并存，不接任
    status, body = create_ref(manager, MAT1, 1.60, "2026-12-01")
    v4_id = body.get("data")
    check("M6-1-1 预售版 V4（2026-12-01）与 V3 并存不接任",
          status == 200 and body.get("code") == "Success", (status, body.get("message")))
    status, body = call("GET", "/m6/references/versions/1", token=manager)
    versions = body["data"]
    v3_row = [v for v in versions if v["priceVersion"] == 3][0]
    v4_row = [v for v in versions if v["priceVersion"] == 4][0]
    check("M6-1-1 预售期间 V3 仍是当前生效版、V4 生效但非当前",
          v3_row["status"] == 1 and v3_row["current"] is True
          and v4_row["status"] == 1 and v4_row["current"] is False,
          [(v["priceVersion"], v["status"], v["current"]) for v in versions])

    # 原地改备注（不产生新版本）
    status, body = call("PUT", "/m6/references", token=manager,
                        body={"id": v2_id, "remark": MARK + " 补备注"})
    check("M6-1-1 原地改备注（不生成新版本）",
          status == 200 and body.get("code") == "Success", (status, body.get("message")))
    status, body = call("GET", "/m6/references/versions/1", token=manager)
    check("M6-1-1 版本数仍为 4（V1~V4）",
          len(body["data"]) == 4, [v["priceVersion"] for v in body["data"]])

    # 停用预售版（原地）
    status, body = call("PUT", "/m6/references", token=manager, body={"id": v4_id, "status": 2})
    check("M6-1-1 停用预售版 V4（原地更新）",
          status == 200 and body.get("code") == "Success", (status, body.get("message")))

    # 权限边界
    check("M6-1-1 采购员无新建权 NoPermission",
          create_ref(buyer, MAT1, 9.99, TODAY)[1].get("code") == "NoPermission")
    check("M6-1-1 采购员无编辑权 NoPermission",
          call("PUT", "/m6/references", token=buyer,
               body={"id": v2_id, "remark": "x"})[1].get("code") == "NoPermission")
    check("M6-1-1 仓管员无参考价查询权 NoPermission",
          call("GET", "/m6/references", token=keeper)[1].get("code") == "NoPermission")

    # ================= M6-1-2/1-3 成交统计 =================
    print("== M6-1-2/1-3 成交价统计（快照只读 + 刷新）==")
    status, body = call("GET", "/m6/stats?materialId=1", token=buyer)
    stat = body["data"][0]
    check("M6-1-2 种子快照可查（M-1001 均价 1.18 样本 5）",
          status == 200 and body.get("code") == "Success"
          and f(stat["avgPrice"]) == 1.18 and stat["avgSampleCount"] == 5,
          (status, stat))
    check("M6-1-3 最近成交价 + 来源单号（M6-4-2 溯源）",
          f(stat["lastPrice"]) == 1.20 and stat["lastBillNo"] == "CG-DD-20261011-0001", stat)
    check("M6-1-4 参考价与统计分离展示（refPrice 单独字段，非报价）",
          f(stat["refPrice"]) == 1.80 and stat["statWindow"] == "LAST_5_DEALS", stat)

    # 主管无 param:config，刷新被拒
    check("M6-2 统计刷新挂 param:config（主管无权）NoPermission",
          call("POST", "/m6/stats/refresh", token=manager)[1].get("code") == "NoPermission")

    # admin 刷新：已审核订单（订单 1）真实计算覆盖种子
    status, body = call("POST", "/m6/stats/refresh", token=admin)
    check("M6-1-2 刷新统计（admin，2 个物料有成交）",
          status == 200 and body.get("code") == "Success" and body["data"] == 2,
          (status, body.get("data"), body.get("message")))
    status, body = call("GET", "/m6/stats?materialId=1", token=buyer)
    stat = body["data"][0]
    check("M6-1-2 刷新后均价 = 订单 1 真实成交（1 笔 1.20，金额加权）",
          f(stat["avgPrice"]) == 1.20 and stat["avgSampleCount"] == 1, stat)
    check("M6-1-3 刷新后最近成交价与来源单号不变（订单 1 即最近）",
          f(stat["lastPrice"]) == 1.20 and stat["lastBillNo"] == "CG-DD-20261011-0001", stat)
    check("M6-1-4 刷新后参考价仍单独展示（V3=1.80），偏离率现算",
          f(stat["refPrice"]) == 1.80
          and abs(f(stat["avgDeviationRate"]) - round((1.20 - 1.80) / 1.80, 4)) < 1e-6, stat)

    status, body = call("GET", "/m6/stats?materialId=4", token=buyer)
    check("M6-1-2 无成交物料不伪造（M-1004 均价/样本为 null）",
          body["data"][0]["avgPrice"] is None and body["data"][0]["avgSampleCount"] is None,
          body["data"][0])

    # ================= M3-6 偏离计算（建行/改行）=================
    print("== M3-6 偏离计算与等级 ==")
    status, body = create_order(buyer)
    order_id = body["data"]
    check("M3-6 前置：冒烟订单草稿建成", status == 200 and body.get("code") == "Success",
          (status, body.get("message")))

    # 单价 2.00 vs 当前生效 V3=1.80 → rate=0.1111 level=1 关注
    status, body = add_item(buyer, order_id, MAT1, 2.00)
    item_id = body["data"]
    item, header = order_item_of(order_id, item_id, buyer)
    check("M3-6 建行即算偏离（rate=0.1111 level=1 关注）",
          status == 200 and body.get("code") == "Success"
          and abs(f(item["deviationRate"]) - round((2.00 - 1.80) / 1.80, 4)) < 1e-6
          and item["deviationLevel"] == 1,
          (status, item))
    check("M3-6 整单偏离标记取明细最高等级（flag=1 关注）",
          header["deviationFlag"] == 1, header["deviationFlag"])

    # 改行单价 3.00 → rate=0.6667 level=2 显著
    status, body = call("PUT", "/m3/orders/%d/items/%d" % (order_id, item_id), token=buyer, body={
        "materialId": MAT1, "quantity": 10, "unitPrice": 3.00, "taxRate": 0.13,
        "deliveryDate": "2026-12-20", "deliveryPlace": "台州市演示工业园 1 号库"})
    item, header = order_item_of(order_id, item_id, buyer)
    check("M3-6 改行重算偏离（rate=0.6667 level=2 显著，flag=2）",
          status == 200 and body.get("code") == "Success"
          and abs(f(item["deviationRate"]) - round((3.00 - 1.80) / 1.80, 4)) < 1e-6
          and item["deviationLevel"] == 2 and header["deviationFlag"] == 2,
          (status, item, header["deviationFlag"]))

    # 改行单价 1.80（=参考价）→ rate=0 level=0，整单回落
    status, body = call("PUT", "/m3/orders/%d/items/%d" % (order_id, item_id), token=buyer, body={
        "materialId": MAT1, "quantity": 10, "unitPrice": 1.80, "taxRate": 0.13,
        "deliveryDate": "2026-12-20", "deliveryPlace": "台州市演示工业园 1 号库"})
    item, header = order_item_of(order_id, item_id, buyer)
    check("M3-6 单价等于参考价 rate=0 level=0，整单回落 0",
          f(item["deviationRate"]) == 0.0 and item["deviationLevel"] == 0
          and header["deviationFlag"] == 0,
          (item, header["deviationFlag"]))

    # 无参考价物料（M-1007）不判定
    status, body = add_item(buyer, order_id, MAT7, 50.00)
    item7_id = body["data"]
    item7, header = order_item_of(order_id, item7_id, buyer)
    check("M3-6 无生效参考价不判定（rate=null level=0，不伪造）",
          item7["deviationRate"] is None and item7["deviationLevel"] == 0, item7)

    # 关注清单（演示单 + 冒烟单当前无偏离 → 只剩演示订单 2）
    status, body = call("GET", "/m6/deviations?pageNo=1&pageSize=10", token=buyer)
    check("M3-6 关注清单只含偏离单（演示订单 2 关注）",
          status == 200 and body.get("code") == "Success"
          and body["data"]["total"] == 1
          and body["data"]["records"][0]["orderNo"] == "CG-DD-20261016-0001"
          and body["data"]["records"][0]["deviationFlag"] == 1,
          (status, body.get("message")))
    check("M3-6 清单行带明细偏离（M-1004 rate=0.0698 level=1）",
          body["data"]["records"][0]["items"][0]["deviationLevel"] == 1
          and abs(f(body["data"]["records"][0]["items"][0]["deviationRate"]) - 0.0698) < 1e-6,
          body["data"]["records"][0]["items"])

    status, body = call("GET", "/m6/deviations?deviationLevel=2", token=buyer)
    check("M3-6 按显著等级筛选（当前无显著单，total=0）",
          body.get("code") == "Success" and body["data"]["total"] == 0,
          body["data"]["total"])
    status, body = call("GET", "/m6/deviations/summary", token=buyer)
    check("M3-6 偏离汇总（关注 1 单、阈值 0.05/0.15 展示）",
          body.get("code") == "Success" and body["data"]["focusOrders"] == 1
          and body["data"]["significantOrders"] == 0
          and f(body["data"]["focusThreshold"]) == FOCUS
          and f(body["data"]["significantThreshold"]) == SIG,
          body.get("data"))

    # ================= M3-6 偏离重算 =================
    print("== M3-6 偏离重算（参考价变更后同步）==")
    # 参考价改 2.00（V5 立即生效）→ 冒烟行单价 1.80 → rate=-0.1 level=1
    status, body = create_ref(manager, MAT1, 2.00, TODAY)
    v5_id = body.get("data")
    check("M3-6 前置：参考价改 2.00（V5 立即生效，V3 停用）",
          status == 200 and body.get("code") == "Success", (status, body.get("message")))
    status, body = call("POST", "/m6/deviations/recalc", token=manager, body={"materialId": MAT1})
    check("M3-6 重算按物料范围（订单 1 的 M-1001 行 + 冒烟单行，共 2 行）",
          status == 200 and body.get("code") == "Success" and body["data"] == 2,
          (status, body.get("data"), body.get("message")))
    item, header = order_item_of(order_id, item_id, buyer)
    check("M3-6 重算后冒烟行 rate=-0.1 level=1（单价 1.80 vs V5=2.00），整单 flag=1",
          abs(f(item["deviationRate"]) - round((1.80 - 2.00) / 2.00, 4)) < 1e-6
          and item["deviationLevel"] == 1 and header["deviationFlag"] == 1,
          (item, header["deviationFlag"]))
    # 演示订单 1 的 M-1001 行（单价 1.20）→ rate=-0.4 显著
    status, body = call("GET", "/m3/orders/1", token=buyer)
    demo_item = [r for r in body["data"]["items"] if r["materialId"] == MAT1][0]
    check("M3-6 重算同步演示单（订单 1 行 1 rate=-0.4 level=2，整单 flag=2）",
          abs(f(demo_item["deviationRate"]) - round((1.20 - 2.00) / 2.00, 4)) < 1e-6
          and demo_item["deviationLevel"] == 2 and body["data"]["deviationFlag"] == 2,
          (demo_item, body["data"]["deviationFlag"]))
    status, body = call("GET", "/m6/deviations?pageNo=1&pageSize=10", token=buyer)
    records = body["data"]["records"]
    check("M3-6 重算后清单 3 单，显著单排最前（订单 1 > 冒烟单/订单 2）",
          body["data"]["total"] == 3
          and records[0]["orderNo"] == "CG-DD-20261011-0001"
          and records[0]["deviationFlag"] == 2
          and {r["orderNo"] for r in records} == {"CG-DD-20261011-0001",
                                                  "CG-DD-20261016-0001",
                                                  "CG-DD-20261026-0001"},
          [(r["orderNo"], r["deviationFlag"]) for r in records])
    # 采购员无重算权（挂 price:update）
    check("M3-6 采购员无重算权 NoPermission",
          call("POST", "/m6/deviations/recalc", token=buyer)[1].get("code") == "NoPermission")

    # ================= M6-4-1 审批证据面板 =================
    print("== M6-4-1 审批证据面板 ==")
    status, body = call("GET", "/m6/evidence/order/1", token=manager)
    rows = body["data"]["rows"]
    m1 = [r for r in rows if r["materialNo"] == "M-1001"][0]
    m2 = [r for r in rows if r["materialNo"] == "M-1002"][0]
    check("M6-4-1 证据面板一屏齐（单价/参考价/偏离/历史成交）",
          status == 200 and body.get("code") == "Success"
          and len(rows) == 2 and f(m1["unitPrice"]) == 1.20 and f(m2["unitPrice"]) == 25.00,
          (status, rows))
    check("M6-4-1 as-of 还原下单时点参考价（订单 1 创建日 → V1=1.20，虽已停用）",
          f(m1["refPrice"]) == REF_V1_MAT1 and m1["refEffectiveDate"] == "2026-09-01", m1)
    check("M6-4-1 偏离等级名与来源单号（M6-4-2）",
          m1["deviationLevelName"] in ("正常", "关注", "显著")
          and m1["lastBillNo"] == "CG-DD-20261011-0001"
          and f(m1["avgPrice"]) == 1.20, m1)
    check("M6-4-1 无参考价物料给缺口提示（不伪造）",
          all(r["note"] is None or "无" in r["note"] for r in rows), rows)
    check("M6-4-1 采购员无证据面板权 NoPermission",
          call("GET", "/m6/evidence/order/1", token=buyer)[1].get("code") == "NoPermission")
    check("M6-4-1 仓管员无证据面板权 NoPermission",
          call("GET", "/m6/evidence/order/1", token=keeper)[1].get("code") == "NoPermission")

    # ================= 口径：无 token / 阈值参数 =================
    print("== 收尾：鉴权与参数口径 ==")
    status, body = call("GET", "/m6/references")
    check("无 token 访问参考价列表 401",
          status == 401, (status, body.get("code")))
    status, body = call("GET", "/m11/params?group=price", token=admin)
    codes = {p["paramCode"] for p in body["data"]} if body.get("code") == "Success" else set()
    check("价格口径参数在位（窗口/阈值 4 项）",
          {"price_stat_window_type", "price_stat_window_value",
           "deviation_threshold_focus", "deviation_threshold_significant"} <= codes,
          codes)

    print("")
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAILED: " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
