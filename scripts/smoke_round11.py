#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 11 冒烟测试：M12 管理后台与数据可视化。

覆盖功能点：
    M12-1-1 后台首页与功能导航         M12-1-2 维护入口聚合
    M12-2-1 采购单据汇总查询           M12-2-2 库存汇总查询
    M12-2-3 结果导出
    M12-3-1 采购订单状态分布           M12-3-2 采购金额月度趋势
    M12-3-3 供应商采购金额排名         M12-3-4 库存不足物料统计
    M12-3-5 时间范围与筛选联动         M12-3-6 数据范围联动过滤

断言策略（与前面几轮不同，值得说明）：
    M12 是"汇总与图表"模块，最容易出的错不是接口报错，而是**同一个口径在不同页面各算一遍**。
    所以本轮大量断言是"跨接口对账"而不是写死数字：
      状态分布五桶 + 不计入 = 订单列表总数
      状态分布金额 = 采购汇总（按状态）金额
      趋势总金额 = 供应商排名总金额
      库存不足图表 = M5 库存预警清单（逐项相同）
      库存汇总数量 = M5 库存台账逐行汇总
    这样即使回归轮次在库里造了新单据，断言依然成立，而且真的能抓住口径漂移。

用法：python scripts/smoke_round11.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本
      （含 db/24_init_ops_demo.sql 运营演示数据）。
退出码 0 表示全部通过。
"""
import json
import sys
import urllib.error
import urllib.request
from datetime import date

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 演示基线（db/24_init_ops_demo.sql 造的固定单据）
DEMO_CLOSED_ORDER = "CG-DD-20260415-0001"   # 已关闭，金额 28450
DEMO_PENDING_ORDER = "CG-DD-20261027-0001"  # 待审批，会签待办两条
DEMO_CLOSED_AMOUNT = 28450.0
DISABLED_MATERIAL = 8                        # 切削液，已停用，不参与库存不足统计
ALERT_MATERIALS = {3, 4, 5, 6}               # 演示数据里四个不足物料


def call(method, path, token=None, body=None, raw=False):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            content = response.read()
            if raw:
                return response.status, content, response.headers
            return response.status, json.loads(content.decode("utf-8"))
    except urllib.error.HTTPError as error:
        content = error.read()
        if raw:
            return error.code, content, error.headers
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


def data_of(body):
    return body.get("data")


def num(value):
    return float(value) if value is not None else 0.0


def main():
    admin = token_of("admin")
    buyer = token_of("caigou01")
    manager = token_of("zhuguan01")
    keeper = token_of("cangku01")

    # 只读基线：本轮全部接口只读，前后订单数与库存量必须不变（M9-5-4 同一条纪律）
    _, orders_before = call("GET", "/m3/orders/page?pageNo=1&pageSize=1", token=manager)
    orders_total = data_of(orders_before).get("total")
    _, stock_before = call("GET", "/m5/stock/balance?pageNo=1&pageSize=100", token=keeper)
    stock_qty_before = sum(num(r.get("quantity")) for r in (data_of(stock_before).get("records") or []))

    # ================= M12-1-1 / M12-1-2 后台首页 =================
    print("== M12-1 后台首页与维护入口聚合 ==")
    status, body = call("GET", "/m12/overview")
    check("M12-1-1 无 token 访问首页返回 401", status == 401, status)

    status, body = call("GET", "/m12/overview", token=admin)
    overview_admin = data_of(body) or {}
    groups = overview_admin.get("groups") or []
    check("M12-1-2 管理员首页聚合五类入口（基础数据/字典/用户权限/知识库/审计）",
          status == 200 and [g["key"] for g in groups] == ["base", "dict", "platform", "kb", "audit"],
          [g["key"] for g in groups])
    base_entries = [e["title"] for g in groups if g["key"] == "base" for e in g["entries"]]
    check("M12-1-1 管理员的基础数据只剩仓库与库位（不持 m1:material/supplier/category 权限）",
          base_entries == ["仓库与库位"], base_entries)
    perms = set()
    for group in groups:
        for entry in group["entries"]:
            perms.add(entry["perm"])
    check("M12-1-1 返回的入口权限点都在管理员权限集合内（无权限的入口不下发）",
          "m1:material:list" not in perms and "m11:user:list" in perms, sorted(perms))
    metric_keys = [m["key"] for m in (overview_admin.get("metrics") or [])]
    check("M12-1-1 管理员指标卡四项（待办/本月金额/库存不足/在途交付）",
          metric_keys == ["todo", "monthAmount", "stockAlert", "pendingDelivery"], metric_keys)
    check("M12-1-1 管理员数据范围提示为全量",
          "全部部门与仓库" in (overview_admin.get("scopeHint") or ""),
          overview_admin.get("scopeHint"))

    status, body = call("GET", "/m12/overview", token=buyer)
    overview_buyer = data_of(body) or {}
    buyer_groups = [g["key"] for g in (overview_buyer.get("groups") or [])]
    buyer_base = [e["title"] for g in (overview_buyer.get("groups") or []) if g["key"] == "base"
                  for e in g["entries"]]
    check("M12-1-1 采购员的基础数据四入口（物料/供应商/仓库/分类）",
          len(buyer_base) == 4 and "物料档案" in buyer_base, buyer_base)
    check("M12-1-1 采购员不返回用户权限/知识库/审计三类入口",
          buyer_groups == ["base", "dict"], buyer_groups)
    buyer_metrics = [m["key"] for m in (overview_buyer.get("metrics") or [])]
    check("M12-1-1 采购员只有待办与库存不足两项指标（无采购金额权限）",
          buyer_metrics == ["todo", "stockAlert"], buyer_metrics)
    check("M12-1-1 采购员数据范围提示含本部门",
          "采购部" in (overview_buyer.get("scopeHint") or ""), overview_buyer.get("scopeHint"))

    status, body = call("GET", "/m12/overview", token=keeper)
    overview_keeper = data_of(body) or {}
    check("M12-1-1 仓管员数据范围提示按仓库维度呈现",
          "原料仓" in (overview_keeper.get("scopeHint") or ""), overview_keeper.get("scopeHint"))
    check("M12-1-1 仓管员入口不含知识库与审计",
          "kb" not in [g["key"] for g in (overview_keeper.get("groups") or [])],
          [g["key"] for g in (overview_keeper.get("groups") or [])])

    # ================= M12-3-1 订单状态分布 =================
    print("== M12-3-1 采购订单状态分布 ==")
    status, body = call("GET", "/m12/charts/status", token=admin)
    status_chart = data_of(body) or {}
    buckets = status_chart.get("buckets") or []
    check("M12-3-1 五个业务桶齐全且顺序固定",
          [b["code"] for b in buckets] == ["PENDING_APPROVAL", "PENDING_DELIVERY", "PARTIAL",
                                           "DONE", "CLOSED"],
          [b["code"] for b in buckets])
    bucket_total = sum(b["count"] for b in buckets)
    excluded = status_chart.get("excluded") or []
    excluded_total = sum(b["count"] for b in excluded)
    check("M12-3-1 五桶 + 不计入 = 订单列表总数（与 M3 列表对账）",
          bucket_total + excluded_total == orders_total,
          (bucket_total, excluded_total, orders_total))
    check("M12-3-1 草稿单独计入不计入集合（不进五桶）",
          any(b["code"] == "DRAFT" and b["count"] >= 1 for b in excluded), excluded)
    closed = next((b for b in buckets if b["code"] == "CLOSED"), None)
    check("M12-3-1 演示单 CG-DD-20260415-0001 落在已关闭桶",
          closed is not None and closed["count"] >= 1 and num(closed["amount"]) >= DEMO_CLOSED_AMOUNT - 0.01,
          closed)
    ratio_sum = sum(num(b.get("ratio")) for b in buckets if b["count"] > 0)
    check("M12-3-1 占比之和为 1（分母是参与分桶的单据数）",
          abs(ratio_sum - 1) < 0.01 or bucket_total == 0, (ratio_sum, bucket_total))

    # ================= M12-3-2 月度趋势 =================
    print("== M12-3-2 采购金额月度趋势 ==")
    status, body = call("GET", "/m12/charts/trend", token=admin)
    trend = data_of(body) or {}
    points = trend.get("points") or []
    check("M12-3-2 默认返回 12 个连续月份",
          len(points) == 12 and [p["month"] for p in points] == sorted(p["month"] for p in points),
          len(points))
    check("M12-3-2 最后一个月是当前月",
          bool(points) and points[-1]["month"] == date.today().strftime("%Y-%m"),
          points[-1] if points else None)
    zero_months = [p["month"] for p in points if p["orderCount"] == 0]
    check("M12-3-2 无单据月份补零而不是断点",
          all(num(p["amount"]) == 0 for p in points if p["orderCount"] == 0),
          zero_months[:3])
    check("M12-3-2 区间合计 = 各月之和",
          abs(num(trend.get("totalAmount")) - sum(num(p["amount"]) for p in points)) < 0.01,
          (trend.get("totalAmount"), sum(num(p["amount"]) for p in points)))

    # ================= M12-3-3 供应商排名 =================
    print("== M12-3-3 供应商采购金额排名 ==")
    status, body = call("GET", "/m12/charts/supplier?topN=10", token=admin)
    rank = data_of(body) or {}
    rows = rank.get("rows") or []
    amounts = [num(r["amount"]) for r in rows]
    check("M12-3-3 排名按金额降序",
          amounts == sorted(amounts, reverse=True), amounts)
    check("M12-3-3 排名条数不超过 Top-N",
          len(rows) <= (rank.get("topN") or 10), (len(rows), rank.get("topN")))
    check("M12-3-3 区间总金额与月度趋势合计一致（同一已成交口径）",
          abs(num(rank.get("totalAmount")) - num(trend.get("totalAmount"))) < 0.01,
          (rank.get("totalAmount"), trend.get("totalAmount")))
    check("M12-3-3 占比分母是区间全额，不是 Top-N 之和",
          all(abs(num(r["ratio"]) - num(r["amount"]) / num(rank.get("totalAmount"))) < 0.001
              for r in rows) if num(rank.get("totalAmount")) > 0 else True,
          [(r["supplierName"], r["ratio"]) for r in rows[:2]])

    status, body = call("GET", "/m12/charts/supplier?topN=2", token=admin)
    rank2 = data_of(body) or {}
    check("M12-3-3 Top-N 截断只影响展示条数，不影响分母与供应商家数",
          len(rank2.get("rows") or []) == 2
          and abs(num(rank2.get("totalAmount")) - num(rank.get("totalAmount"))) < 0.01
          and rank2.get("supplierCount") == rank.get("supplierCount"),
          (len(rank2.get("rows") or []), rank2.get("totalAmount"), rank2.get("supplierCount")))

    # ================= M12-3-4 库存不足 =================
    print("== M12-3-4 库存不足物料统计 ==")
    status, body = call("GET", "/m12/charts/stock-alert", token=buyer)
    alert_chart = data_of(body) or {}
    alert_rows = alert_chart.get("rows") or []
    _, m5_body = call("GET", "/m5/stock/alert", token=buyer)
    m5_rows = data_of(m5_body) or []
    m12_pairs = sorted((r["materialId"], round(num(r["gapQty"]), 4)) for r in alert_rows)
    m5_pairs = sorted((r["materialId"], round(num(r["gapQty"]), 4)) for r in m5_rows)
    check("M12-3-4 库存不足清单与 M5-5-1 库存预警逐项一致（物料与缺口都相同）",
          m12_pairs == m5_pairs, (m12_pairs, m5_pairs))
    check("M12-3-4 缺口合计 = 各行缺口之和",
          abs(num(alert_chart.get("totalGap")) - sum(num(r["gapQty"]) for r in alert_rows)) < 0.01,
          alert_chart.get("totalGap"))
    check("M12-3-4 缺口 = 预警下限 − 可用量",
          all(abs(num(r["gapQty"]) - (num(r["safetyStock"]) - num(r["availableQty"]))) < 0.01
              for r in alert_rows), alert_rows[:2])
    check("M12-3-4 停用物料不参与不足统计",
          all(r["materialId"] != DISABLED_MATERIAL for r in alert_rows),
          [r["materialId"] for r in alert_rows])
    check("M12-3-4 不足物料数 = 明细行数",
          alert_chart.get("materialCount") == len(alert_rows),
          (alert_chart.get("materialCount"), len(alert_rows)))
    check("M12-3-4 演示数据四个不足物料全部命中",
          ALERT_MATERIALS <= {r["materialId"] for r in alert_rows},
          sorted(r["materialId"] for r in alert_rows))

    # ================= M12-2-1 采购单据汇总 =================
    print("== M12-2-1 采购单据汇总 ==")
    status, body = call("GET", "/m12/summary/purchase?groupBy=status", token=admin)
    sum_status = data_of(body) or []
    check("M12-2-1 按状态分组：单据数之和 = 订单总数",
          sum(r["orderCount"] for r in sum_status) == orders_total,
          (sum(r["orderCount"] for r in sum_status), orders_total))
    check("M12-2-1 按状态分组：金额之和 = 状态分布五桶 + 不计入",
          abs(sum(num(r["amount"]) for r in sum_status)
              - (num(status_chart.get("amount")) + sum(num(b["amount"]) for b in excluded))) < 0.01,
          sum(num(r["amount"]) for r in sum_status))
    check("M12-2-1 状态分组名由三状态组合给出（生命周期·审批·履约）",
          all(r["groupName"].count("·") == 2 for r in sum_status),
          [r["groupName"] for r in sum_status[:3]])

    status, body = call("GET", "/m12/summary/purchase?groupBy=supplier", token=admin)
    sum_supplier = data_of(body) or []
    status, body = call("GET", "/m12/summary/purchase?groupBy=material", token=admin)
    sum_material = data_of(body) or []
    status, body = call("GET", "/m12/summary/purchase?groupBy=month", token=admin)
    sum_month = data_of(body) or []

    total_status = sum(num(r["amount"]) for r in sum_status)
    total_supplier = sum(num(r["amount"]) for r in sum_supplier)
    total_month = sum(num(r["amount"]) for r in sum_month)
    check("M12-2-1 按供应商分组：金额之和与按状态一致（同源不同切法）",
          abs(total_supplier - total_status) < 0.01, (total_supplier, total_status))
    check("M12-2-1 按月份分组：金额之和与按状态一致",
          abs(total_month - total_status) < 0.01, (total_month, total_status))
    check("M12-2-1 按物料分组：明细金额之和与主单金额之和一致",
          abs(sum(num(r["amount"]) for r in sum_material) - total_status) < 0.01,
          (sum(num(r["amount"]) for r in sum_material), total_status))
    check("M12-2-1 按物料分组带明细行数，其余分组为空（不拿单据数冒充明细行）",
          all(r["itemCount"] is not None and r["itemCount"] >= 1 for r in sum_material)
          and all(r["itemCount"] is None for r in sum_status),
          (sum_material[:1], sum_status[:1]))
    check("M12-2-1 按月份分组按月份倒序",
          [r["groupKey"] for r in sum_month] == sorted([r["groupKey"] for r in sum_month],
                                                       reverse=True),
          [r["groupKey"] for r in sum_month])

    # ================= M12-2-2 库存汇总 =================
    print("== M12-2-2 库存汇总 ==")
    status, body = call("GET", "/m12/summary/stock?groupBy=warehouse", token=manager)
    sum_wh = data_of(body) or []
    check("M12-2-2 按仓库分组返回非空（空列表上的 all() 恒真，必须先证明有数据）",
          status == 200 and len(sum_wh) >= 2, (status, len(sum_wh)))
    check("M12-2-2 按仓库分组：可用量 = 数量 − 冻结 − 预留（逐行）",
          all(abs(num(r["availableQty"])
                  - (num(r["quantity"]) - num(r["frozenQty"]) - num(r["reservedQty"]))) < 0.01
              for r in sum_wh), sum_wh)
    stock_records = data_of(stock_before).get("records") or []
    ledger_qty = sum(num(r.get("quantity")) for r in stock_records)
    check("M12-2-2 库存汇总合计大于 0（演示数据非空）",
          sum(num(r["quantity"]) for r in sum_wh) > 0, sum(num(r["quantity"]) for r in sum_wh))
    check("M12-2-2 按仓库分组：数量合计 = 库存台账逐行汇总（与 M5 对账）",
          abs(sum(num(r["quantity"]) for r in sum_wh) - ledger_qty) < 0.01,
          (sum(num(r["quantity"]) for r in sum_wh), ledger_qty))
    ledger_available = sum(num(r.get("availableQty")) for r in stock_records)
    check("M12-2-2 按仓库分组：可用量合计 = 台账可用量合计",
          abs(sum(num(r["availableQty"]) for r in sum_wh) - ledger_available) < 0.01,
          (sum(num(r["availableQty"]) for r in sum_wh), ledger_available))

    status, body = call("GET", "/m12/summary/stock?groupBy=category", token=manager)
    sum_cat = data_of(body) or []
    status, body = call("GET", "/m12/summary/stock?groupBy=owner", token=manager)
    sum_owner = data_of(body) or []
    check("M12-2-2 三种分组维度数量合计一致（同一批库存换切法）",
          abs(sum(num(r["quantity"]) for r in sum_cat) - ledger_qty) < 0.01
          and abs(sum(num(r["quantity"]) for r in sum_owner) - ledger_qty) < 0.01,
          (sum(num(r["quantity"]) for r in sum_cat), sum(num(r["quantity"]) for r in sum_owner),
           ledger_qty))
    public_row = next((r for r in sum_owner if r["groupKey"] == "PUBLIC"), None)
    check("M12-2-2 按归属对象分组能区分公共库存",
          public_row is not None and public_row["groupName"] == "公共库存", public_row)
    check("M12-2-2 项目库存单独成组（生产线改造项目）",
          any(r["groupKey"] == "PRJ-2026-001" for r in sum_owner),
          [r["groupKey"] for r in sum_owner])

    # ================= M12-2-3 导出 =================
    print("== M12-2-3 汇总结果导出 ==")
    status, content, headers = call("GET", "/m12/summary/export?type=purchase&groupBy=supplier",
                                   token=admin, raw=True)
    check("M12-2-3 采购汇总导出返回 CSV 且带 UTF-8 BOM",
          status == 200 and content.startswith(b"\xef\xbb\xbf")
          and "text/csv" in (headers.get("Content-Type") or ""),
          (status, content[:6], headers.get("Content-Type")))
    export_lines = [line for line in content.decode("utf-8-sig").splitlines() if line.strip()]
    check("M12-2-3 导出行数 = 汇总行数 + 表头",
          len(export_lines) == len(sum_supplier) + 1, (len(export_lines), len(sum_supplier)))
    check("M12-2-3 导出内容与页面同源（首行供应商名一致）",
          sum_supplier and sum_supplier[0]["groupName"] in export_lines[1], export_lines[1])

    status, content, headers = call("GET", "/m12/summary/export?type=stock&groupBy=warehouse",
                                   token=admin, raw=True)
    stock_lines = [line for line in content.decode("utf-8-sig").splitlines() if line.strip()]
    check("M12-2-3 库存汇总导出：行数与表头正确",
          status == 200 and len(stock_lines) == len(sum_wh) + 1
          and "可用量" in stock_lines[0],
          (status, len(stock_lines), stock_lines[0] if stock_lines else None))

    # ================= M12-3-5 联动筛选 =================
    print("== M12-3-5 时间范围与筛选联动 ==")
    today = date.today()
    start_3m = date(today.year, today.month, 1)
    for _ in range(2):
        start_3m = date(start_3m.year - (1 if start_3m.month == 1 else 0),
                        12 if start_3m.month == 1 else start_3m.month - 1, 1)
    range_query = f"startDate={start_3m.isoformat()}&endDate={today.isoformat()}"
    status, body = call("GET", "/m12/charts/trend?" + range_query, token=admin)
    trend_3m = data_of(body) or {}
    status, body = call("GET", "/m12/charts/supplier?" + range_query, token=admin)
    rank_3m = data_of(body) or {}
    check("M12-3-5 指定三个月区间后趋势点数收敛为 3",
          len(trend_3m.get("points") or []) == 3, len(trend_3m.get("points") or []))
    check("M12-3-5 同一区间下趋势合计与供应商排名总额一致（联动生效）",
          abs(num(trend_3m.get("totalAmount")) - num(rank_3m.get("totalAmount"))) < 0.01,
          (trend_3m.get("totalAmount"), rank_3m.get("totalAmount")))
    check("M12-3-5 区间收敛后总额不大于全量区间",
          num(trend_3m.get("totalAmount")) <= num(trend.get("totalAmount")) + 0.01,
          (trend_3m.get("totalAmount"), trend.get("totalAmount")))

    status, body = call("GET", "/m12/charts/status?startDate=2026-04-01&endDate=2026-04-30",
                        token=admin)
    april = data_of(body) or {}
    check("M12-3-5 时间范围对状态分布同样生效（2026-04 只有已关闭一单）",
          status == 200 and april.get("orderCount") == 1
          and [b["code"] for b in april.get("buckets") or [] if b["count"] > 0] == ["CLOSED"],
          (april.get("orderCount"), [(b["code"], b["count"]) for b in april.get("buckets") or []]))

    status, body = call("GET", "/m12/charts/stock-alert?warehouseId=2", token=buyer)
    wh2 = data_of(body) or {}
    check("M12-3-4 按仓库筛选时按该仓库存量单独判定（缺口随范围扩大）",
          status == 200 and num(wh2.get("totalGap")) >= num(alert_chart.get("totalGap")),
          (wh2.get("totalGap"), alert_chart.get("totalGap")))
    status, body = call("GET", "/m12/charts/stock-alert?categoryId=3", token=buyer)
    cat3 = data_of(body) or {}
    check("M12-3-4 按分类筛选只保留该分类物料",
          status == 200 and all(r["categoryName"] == "电气件" for r in cat3.get("rows") or []),
          [(r["materialName"], r["categoryName"]) for r in cat3.get("rows") or []])

    # ================= M12-3-6 数据范围 =================
    print("== M12-3-6 数据范围联动过滤 ==")
    status, body = call("GET", "/m12/charts/status", token=manager)
    manager_chart = data_of(body) or {}
    manager_total = sum(b["count"] for b in manager_chart.get("buckets") or []) + sum(
        b["count"] for b in manager_chart.get("excluded") or [])
    check("M12-3-6 主管（1101+1102）看到与管理员相同的全量单据",
          manager_total == orders_total, (manager_total, orders_total))
    check("M12-3-6 主管的趋势合计与管理员一致（同一部门范围）",
          abs(num((data_of(call("GET", "/m12/charts/trend", token=manager)[1]) or {})
                  .get("totalAmount")) - num(trend.get("totalAmount"))) < 0.01)

    status, body = call("GET", "/m12/charts/stock-alert", token=keeper)
    keeper_alert = data_of(body) or {}
    check("M12-3-6 库存类不按部门范围过滤（与 M5 库存台账同语义）",
          [r["materialId"] for r in keeper_alert.get("rows") or []]
          == [r["materialId"] for r in alert_rows],
          ([r["materialId"] for r in keeper_alert.get("rows") or []],
           [r["materialId"] for r in alert_rows]))

    # ================= 权限边界 =================
    print("== 权限边界 ==")
    for path in ["/m12/summary/purchase", "/m12/summary/stock", "/m12/charts/status",
                 "/m12/charts/trend", "/m12/charts/supplier"]:
        status, body = call("GET", path, token=buyer)
        check("权限边界：采购员访问 %s 返回 NoPermission" % path,
              status == 403 and body.get("code") == "NoPermission", (status, body.get("code")))
    status, body = call("GET", "/m12/charts/stock-alert", token=buyer)
    check("权限边界：采购员可看库存不足图（m12:chart:stockalert）",
          status == 200 and body.get("code") == "Success", status)
    status, body = call("GET", "/m12/summary/stock", token=keeper)
    check("权限边界：仓管员无库存汇总权限（汇总归主管与管理员）",
          status == 403, status)
    status, body = call("GET", "/m12/charts/stock-alert", token=keeper)
    check("权限边界：仓管员可看库存不足图", status == 200, status)
    status, body = call("GET", "/m12/summary/export?type=purchase", token=keeper)
    check("权限边界：仓管员无导出权限", status == 403, status)

    # 八个端点对管理员全部可用
    # 导出返回 CSV 不是 JSON，单独在 M12-2-3 断言，不放进这条循环
    admin_paths = ["/m12/overview", "/m12/summary/purchase", "/m12/summary/stock",
                   "/m12/charts/status", "/m12/charts/trend",
                   "/m12/charts/supplier", "/m12/charts/stock-alert"]
    failures = []
    for path in admin_paths:
        status, _ = call("GET", path, token=admin)
        if status != 200:
            failures.append((path, status))
    check("M12 八个端点管理员全部可用", not failures, failures)

    # ================= 边界与只读 =================
    print("== 边界与只读约束 ==")
    status, body = call("GET", "/m12/charts/trend?startDate=2026-05-01&endDate=2026-04-01",
                        token=admin)
    check("边界：起始日晚于结束日返回空序列而不是报错",
          status == 200 and (data_of(body) or {}).get("points") == [], status)
    status, body = call("GET", "/m12/summary/purchase?groupBy=unknown", token=admin)
    check("边界：未知分组维度回落到按状态（不报 500）",
          status == 200 and isinstance(data_of(body), list), status)
    status, body = call("GET", "/m12/charts/supplier?topN=999", token=admin)
    check("边界：Top-N 超上限按 50 截断",
          status == 200 and (data_of(body) or {}).get("topN") == 50,
          (data_of(body) or {}).get("topN"))

    _, orders_after = call("GET", "/m3/orders/page?pageNo=1&pageSize=1", token=manager)
    _, stock_after = call("GET", "/m5/stock/balance?pageNo=1&pageSize=100", token=keeper)
    stock_qty_after = sum(num(r.get("quantity")) for r in (data_of(stock_after).get("records") or []))
    check("只读约束：全部查询前后订单总数不变",
          data_of(orders_after).get("total") == orders_total,
          (orders_total, data_of(orders_after).get("total")))
    check("只读约束：全部查询前后库存数量不变",
          abs(stock_qty_after - stock_qty_before) < 0.01,
          (stock_qty_before, stock_qty_after))

    # ================= 汇总 =================
    print()
    print("=" * 60)
    print("通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAILED: " + name)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
