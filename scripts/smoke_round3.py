#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 3 冒烟测试：M1 基础数据管理。

覆盖功能点：
    M1-1-1 物料列表与检索      M1-1-2 新增物料（编码唯一）
    M1-1-3 编辑物料（留痕）    M1-1-4 物料启停（停用不可选）
    M1-1-5 物料导入导出        M1-1-6 扩展属性整体替换
    M1-1-7 分类树与防环        M1-2-1 供应商列表
    M1-2-2 供应商新增编辑      M1-2-3 供应商启停
    M1-2-4 联系人（主联系人唯一）M1-2-5 资质附件（类型大小校验）
    M1-2-6 供货范围（对象必须存在）M1-3-1 仓库档案
    M1-3-2 库位（仓内唯一）    M1-3-3 仓库使用人（默认人在列表内）
    M1-4-1 字典类型与字典项    M1-4-2 单据类型编号前缀
另含动作级权限断言：采购员不可建仓库、仓管员不可建物料、管理员可建。

用法：python scripts/smoke_round3.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本。
测试数据统一 smoke3 前缀，跑完执行 db/16_clean_smoke_round3.sql 复原。
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
PREFIX = "smk3" + SUFFIX
MATERIAL_NO = PREFIX + "-WL"
CATEGORY_NO = PREFIX + "-FL"
SUPPLIER_NO = PREFIX + "-GYS"
WAREHOUSE_NO = PREFIX + "-CK"
LOCATION_NO = PREFIX + "-KW"
DICT_TYPE_CODE = "smk3_type_" + SUFFIX


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


def upload(path, token, fields, filename, content, content_type="application/octet-stream"):
    """multipart 上传，供导入与资质附件使用"""
    boundary = "----smoke3" + SUFFIX
    body = b""
    for key, value in fields.items():
        body += ("--" + boundary + "\r\n").encode()
        body += ('Content-Disposition: form-data; name="%s"\r\n\r\n' % key).encode()
        body += (str(value) + "\r\n").encode()
    body += ("--" + boundary + "\r\n").encode()
    body += ('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode()
    body += ("Content-Type: %s\r\n\r\n" % content_type).encode()
    body += content + b"\r\n"
    body += ("--" + boundary + "--\r\n").encode()
    headers = {"Content-Type": "multipart/form-data; boundary=" + boundary}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.loads(error.read().decode("utf-8"))
        except json.JSONDecodeError:
            return error.code, {"raw": "decode error"}


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


def q(value):
    """查询参数中的中文需要 URL 编码"""
    return urllib.parse.quote(str(value))


def main():
    # ---------- 登录三个角色 ----------
    status, body = login("admin")
    check("admin 登录", status == 200 and body.get("code") == "Success", body)
    admin = (body.get("data") or {}).get("token")

    status, body = login("caigou01")
    check("采购员登录", status == 200 and body.get("code") == "Success", body)
    buyer = (body.get("data") or {}).get("token")

    status, body = login("cangku01")
    check("仓管员登录", status == 200 and body.get("code") == "Success", body)
    keeper = (body.get("data") or {}).get("token")

    # ---------- M1-1-7 先建分类（物料要引用；分类维护归采购员，管理员未授权） ----------
    status, body = call("GET", "/m1/material-categories/tree", token=buyer)
    check("M1-1-7 分类树可读", status == 200 and body.get("code") == "Success", body)
    root_no = body["data"][0]["categoryNo"] if body.get("data") else None
    check("M1-1-7 演示分类存在", bool(root_no), body)

    status, body = call("POST", "/m1/material-categories", token=buyer, body={
        "categoryNo": CATEGORY_NO, "categoryName": "冒烟测试分类", "parentId": 0, "sortNo": 99
    })
    check("M1-1-7 采购员可建分类", status == 200 and body.get("code") == "Success", body)
    category_id = body.get("data")

    status, body = call("POST", "/m1/material-categories", token=buyer, body={
        "categoryNo": CATEGORY_NO, "categoryName": "重复编码", "parentId": 0
    })
    check("M1-1-7 分类编码唯一", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("PUT", "/m1/material-categories/%d" % category_id, token=buyer, body={
        "categoryNo": "other", "categoryName": "改编码", "parentId": 0
    })
    check("M1-1-7 分类编码不可改", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("PUT", "/m1/material-categories/%d" % category_id, token=buyer, body={
        "categoryName": "冒烟测试分类", "parentId": category_id
    })
    check("M1-1-7 父节点不能是自身", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("GET", "/m1/material-categories/tree", token=admin)
    check("M1-1-7 分类维护归采购员，管理员无权限", status == 403, body)

    # ---------- 取一个启用单位 ----------
    status, body = call("GET", "/m1/dicts/unit/items", token=buyer)
    check("字典单位可读", status == 200 and body.get("code") == "Success", body)
    unit = body["data"][0]
    unit_id, unit_code = unit["id"], unit["itemCode"]

    # ---------- M1-1-2 新增物料 ----------
    status, body = call("POST", "/m1/materials", token=buyer, body={
        "materialNo": MATERIAL_NO, "materialName": "冒烟测试物料", "categoryId": category_id,
        "spec": "规格A", "brand": "品牌A", "unitId": unit_id, "materialType": 1,
        "safetyStock": 10,
        "extAttrs": [{"attrName": "材质", "attrValue": "钢", "sortNo": 1},
                     {"attrName": "颜色", "attrValue": "银", "sortNo": 2}]
    })
    check("M1-1-2 采购员可建物料", status == 200 and body.get("code") == "Success", body)
    material_id = body.get("data")

    status, body = call("POST", "/m1/materials", token=buyer, body={
        "materialNo": MATERIAL_NO, "materialName": "重复", "categoryId": category_id,
        "unitId": unit_id, "materialType": 1
    })
    check("M1-1-2 物料编码唯一", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m1/materials", token=buyer, body={
        "materialNo": PREFIX + "-X1", "materialName": "坏分类", "categoryId": 999999,
        "unitId": unit_id, "materialType": 1
    })
    check("M1-1-2 分类不存在返回 NotFound", status == 200 and body.get("code") == "NotFound", body)

    status, body = call("POST", "/m1/materials", token=buyer, body={
        "materialNo": PREFIX + "-X2", "materialName": "坏类型", "categoryId": category_id,
        "unitId": unit_id, "materialType": 9
    })
    check("M1-1-2 物料类型不合法", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m1/materials", token=buyer, body={
        "materialNo": PREFIX + "-X3", "materialName": "负库存下限", "categoryId": category_id,
        "unitId": unit_id, "materialType": 1, "safetyStock": -1
    })
    check("M1-1-2 预警下限不能为负", status == 200 and body.get("code") == "InvalidParameter", body)

    # ---------- M1-1-1 列表与详情 ----------
    status, body = call("GET", "/m1/materials/page?pageNo=1&pageSize=10&keyword=%s" % MATERIAL_NO,
                        token=buyer)
    check("M1-1-1 按编码检索命中", status == 200
          and body["data"]["total"] == 1
          and body["data"]["records"][0]["materialNo"] == MATERIAL_NO, body)

    status, body = call("GET", "/m1/materials/page?pageNo=1&pageSize=10&keyword=%s" % q("冒烟测试物料"),
                        token=buyer)
    check("M1-1-1 按名称模糊检索", status == 200 and body["data"]["total"] >= 1, body)

    status, body = call("GET", "/m1/materials/%d" % material_id, token=buyer)
    check("M1-1-1 详情含分类单位名称", status == 200
          and body["data"]["categoryName"] == "冒烟测试分类"
          and body["data"]["unitName"] == unit["itemName"], body)

    # ---------- M1-1-6 扩展属性 ----------
    status, body = call("GET", "/m1/materials/%d" % material_id, token=buyer)
    check("M1-1-6 新增带回扩展属性", status == 200
          and len(body["data"]["extAttrs"]) == 2
          and body["data"]["extAttrs"][0]["attrName"] == "材质", body)

    # ---------- M1-1-3 编辑与留痕 ----------
    status, body = call("PUT", "/m1/materials/%d" % material_id, token=buyer, body={
        "materialNo": MATERIAL_NO, "materialName": "冒烟测试物料改", "categoryId": category_id,
        "unitId": unit_id, "materialType": 1, "safetyStock": 20,
        "extAttrs": [{"attrName": "材质", "attrValue": "不锈钢", "sortNo": 1}]
    })
    check("M1-1-3 编辑物料", status == 200 and body.get("code") == "Success", body)

    status, body = call("PUT", "/m1/materials/%d" % material_id, token=buyer, body={
        "materialNo": "changed", "materialName": "改编码", "categoryId": category_id,
        "unitId": unit_id, "materialType": 1
    })
    check("M1-1-3 物料编码不可改", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("GET", "/m1/materials/%d" % material_id, token=buyer)
    check("M1-1-6 扩展属性整体替换", status == 200
          and len(body["data"]["extAttrs"]) == 1
          and body["data"]["extAttrs"][0]["attrValue"] == "不锈钢", body)

    status, body = call("GET", "/m11/operation-logs?pageNo=1&pageSize=50&moduleCode=M1", token=admin)
    details = [row["detail"] for row in body["data"]["records"]]
    check("M1-1-3 编辑写操作日志含前后值", status == 200
          and any(MATERIAL_NO in (row.get("targetNo") or "") and "->" in (row.get("detail") or "")
                  for row in body["data"]["records"]), details[:5])

    # ---------- M1-1-4 启停 ----------
    status, body = call("PATCH", "/m1/materials/%d/status" % material_id, token=buyer)
    check("M1-1-4 停用物料", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/materials/options", token=buyer)
    codes = [item["materialNo"] for item in body["data"]]
    check("M1-1-4 停用后不在可选列表", MATERIAL_NO not in codes, codes[:5])

    status, body = call("PATCH", "/m1/materials/%d/status" % material_id, token=buyer)
    check("M1-1-4 重新启用", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/materials/options", token=buyer)
    codes = [item["materialNo"] for item in body["data"]]
    check("M1-1-4 启用后回到可选列表", MATERIAL_NO in codes, codes[:5])

    # ---------- M1-1-5 导入导出 ----------
    csv_text = (
        "物料编码,物料名称,分类编码,规格,品牌,单位编码,物料类型,预警下限,备注\r\n"
        + MATERIAL_NO + ",导入更新行, " + CATEGORY_NO + ",规格B,品牌B," + unit_code + ",2,5,更新已有\r\n"
        + PREFIX + "-NEW,导入新建行," + CATEGORY_NO + ",规格C,品牌C," + unit_code + ",1,0,新建\r\n"
        + PREFIX + "-BAD,坏分类行,NO-SUCH-CAT,,," + unit_code + ",1,0,分类不存在\r\n"
        + MATERIAL_NO + ",重复行," + CATEGORY_NO + ",规格D,品牌D," + unit_code + ",1,0,文件内重复\r\n"
    )
    status, body = upload("/m1/materials/import", buyer, {}, "import.csv",
                          csv_text.encode("utf-8"), "text/csv")
    rows = (body.get("data") or {}).get("rows") or []
    check("M1-1-5 导入逐行明细", status == 200
          and body["data"]["totalCount"] == 4
          and body["data"]["successCount"] == 2
          and body["data"]["failCount"] == 2, body.get("data"))
    check("M1-1-5 失败行有行号与原因", status == 200
          and any(row["lineNo"] == 4 and "分类编码不存在" in row["message"] for row in rows)
          and any(row["lineNo"] == 5 and "重复" in row["message"] for row in rows), rows)

    status, body = call("GET", "/m1/materials/%d" % material_id, token=buyer)
    check("M1-1-5 导入更新生效", status == 200
          and body["data"]["materialName"] == "导入更新行"
          and body["data"]["materialType"] == 2, body.get("data"))

    status, body = call("GET", "/m1/materials/page?keyword=%s-NEW" % PREFIX, token=buyer)
    check("M1-1-5 导入新建生效", status == 200 and body["data"]["total"] == 1, body)

    status, raw, headers = call("GET", "/m1/materials/export?keyword=%s" % PREFIX,
                                token=buyer, raw=True)
    text = raw.decode("utf-8-sig")
    check("M1-1-5 导出带 BOM 与表头", status == 200
          and raw[:3] == b"\xef\xbb\xbf" and text.splitlines()[0].startswith("物料编码"), text[:80])
    check("M1-1-5 导出含数据行", MATERIAL_NO in text and PREFIX + "-NEW" in text, text[:200])

    status, raw, headers = call("GET", "/m1/materials/import-template", token=buyer, raw=True)
    check("M1-1-5 模板可下载", status == 200 and raw[:3] == b"\xef\xbb\xbf", raw[:40])

    # ---------- M1-2 供应商 ----------
    status, body = call("POST", "/m1/suppliers", token=buyer, body={
        "supplierNo": SUPPLIER_NO, "supplierName": "冒烟测试供应商", "supplierType": 2,
        "taxNo": "91330100MA27XK0T0X"
    })
    check("M1-2-2 采购员可建供应商", status == 200 and body.get("code") == "Success", body)
    supplier_id = body.get("data")

    status, body = call("POST", "/m1/suppliers", token=buyer, body={
        "supplierNo": SUPPLIER_NO, "supplierName": "重复", "supplierType": 2
    })
    check("M1-2-2 供应商编码唯一", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m1/suppliers", token=buyer, body={
        "supplierNo": PREFIX + "-G2", "supplierName": "坏税号", "supplierType": 2, "taxNo": "123"
    })
    check("M1-2-2 税号格式校验", status == 200 and body.get("code") == "InvalidParameter", body)

    # M1-2-4 联系人
    status, body = call("PUT", "/m1/suppliers/%d/contacts" % supplier_id, token=buyer, body={
        "contacts": [
            {"contactName": "联系人甲", "phone": "13800000001", "isDefault": 1},
            {"contactName": "联系人乙", "phone": "13800000002", "isDefault": 1}
        ]
    })
    check("M1-2-4 主联系人只能一个", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("PUT", "/m1/suppliers/%d/contacts" % supplier_id, token=buyer, body={
        "contacts": [
            {"contactName": "联系人甲", "phone": "13800000001", "isDefault": 1},
            {"contactName": "联系人乙", "phone": "13800000002"}
        ]
    })
    check("M1-2-4 联系人整体保存", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/suppliers/%d/contacts" % supplier_id, token=buyer)
    check("M1-2-4 联系人可读", status == 200 and len(body["data"]) == 2, body)

    # M1-2-6 供货范围
    status, body = call("PUT", "/m1/suppliers/%d/scopes" % supplier_id, token=buyer, body={
        "scopes": [{"scopeType": 1, "scopeId": material_id}, {"scopeType": 2, "scopeId": category_id}]
    })
    check("M1-2-6 供货范围保存", status == 200 and body.get("code") == "Success", body)

    status, body = call("PUT", "/m1/suppliers/%d/scopes" % supplier_id, token=buyer, body={
        "scopes": [{"scopeType": 1, "scopeId": 999999}]
    })
    check("M1-2-6 范围对象不存在返回 NotFound", status == 200 and body.get("code") == "NotFound", body)

    status, body = call("GET", "/m1/suppliers/%d/scopes" % supplier_id, token=buyer)
    check("M1-2-6 范围名称已补全", status == 200
          and body["data"][0]["scopeName"] == "导入更新行"
          and body["data"][1]["scopeName"] == "冒烟测试分类", body)

    # M1-2-5 资质附件
    status, body = call("GET", "/m1/suppliers/%d/attachments" % supplier_id, token=buyer)
    check("M1-2-5 附件列表初始为空", status == 200 and body["data"] == [], body)

    status, body = upload("/m1/suppliers/%d/attachments" % supplier_id, buyer,
                          {"expireDate": "2027-12-31", "remark": "营业执照"},
                          "license.pdf", b"%PDF-1.4 smoke test content", "application/pdf")
    check("M1-2-5 上传 pdf 附件", status == 200 and body.get("code") == "Success", body)
    attachment_id = body.get("data")

    status, body = upload("/m1/suppliers/%d/attachments" % supplier_id, buyer, {},
                          "virus.exe", b"MZ fake exe")
    check("M1-2-5 非白名单类型被拒", status == 200 and body.get("code") == "InvalidParameter", body)

    status, raw, headers = call("GET", "/m1/suppliers/attachments/%d/download" % attachment_id,
                                token=buyer, raw=True)
    check("M1-2-5 附件可下载", status == 200 and raw.startswith(b"%PDF"), raw[:20])

    status, body = call("DELETE", "/m1/suppliers/attachments/%d" % attachment_id, token=buyer)
    check("M1-2-5 删除附件记录", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/suppliers/attachments/%d/download" % attachment_id,
                        token=buyer)
    check("M1-2-5 删除后下载返回 NotFound", status == 200 and body.get("code") == "NotFound", body)

    # M1-2-1 / M1-2-3 列表与启停
    status, body = call("GET", "/m1/suppliers/page?keyword=%s" % SUPPLIER_NO, token=buyer)
    check("M1-2-1 供应商检索命中", status == 200
          and body["data"]["total"] == 1
          and body["data"]["records"][0]["defaultContactName"] == "联系人甲"
          and body["data"]["records"][0]["scopeCount"] == 2, body)

    status, body = call("PATCH", "/m1/suppliers/%d/status" % supplier_id, token=buyer)
    check("M1-2-3 停用供应商", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/suppliers/options", token=buyer)
    nos = [item["supplierNo"] for item in body["data"]]
    check("M1-2-3 停用后不在可选列表", SUPPLIER_NO not in nos, nos[:5])

    status, body = call("PATCH", "/m1/suppliers/%d/status" % supplier_id, token=buyer)
    check("M1-2-3 重新启用", status == 200 and body.get("code") == "Success", body)

    # ---------- M1-3 仓库（管理员） ----------
    status, body = call("POST", "/m1/warehouses", token=buyer, body={
        "warehouseNo": WAREHOUSE_NO, "warehouseName": "冒烟测试仓库"
    })
    check("M1-3-1 采购员不可建仓库（403）", status == 403, body)

    status, body = call("POST", "/m1/warehouses", token=admin, body={
        "warehouseNo": WAREHOUSE_NO, "warehouseName": "冒烟测试仓库"
    })
    check("M1-3-1 管理员可建仓库", status == 200 and body.get("code") == "Success", body)
    warehouse_id = body.get("data")

    status, body = call("POST", "/m1/warehouses/%d/locations" % warehouse_id, token=admin,
                        body={"locationNo": LOCATION_NO, "locationName": "冒烟库位"})
    check("M1-3-2 库位新增", status == 200 and body.get("code") == "Success", body)
    location_id = body.get("data")

    status, body = call("POST", "/m1/warehouses/%d/locations" % warehouse_id, token=admin,
                        body={"locationNo": LOCATION_NO})
    check("M1-3-2 库位仓内唯一", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("GET", "/m1/warehouses/%d/locations" % warehouse_id, token=admin)
    check("M1-3-2 库位列表", status == 200 and len(body["data"]) == 1, body)

    status, body = call("PUT", "/m1/warehouses/locations/%d" % location_id, token=admin,
                        body={"locationName": "冒烟库位改", "status": 2})
    check("M1-3-2 库位编辑含停用", status == 200 and body.get("code") == "Success", body)

    # M1-3-3 仓库使用人
    status, body = call("GET", "/m1/warehouses/%d/users" % warehouse_id, token=admin)
    check("M1-3-3 使用人初始为空", status == 200 and body["data"] == [], body)

    status, body = call("PUT", "/m1/warehouses/%d/users" % warehouse_id, token=admin, body={
        "userIds": [], "defaultUserId": 1
    })
    check("M1-3-3 默认人不在列表被拒", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("GET", "/m11/users?pageNo=1&pageSize=50", token=admin)
    keeper_user = next(row for row in body["data"]["records"] if row["username"] == "cangku01")
    keeper_id = keeper_user["id"]

    status, body = call("PUT", "/m1/warehouses/%d/users" % warehouse_id, token=admin, body={
        "userIds": [keeper_id], "defaultUserId": keeper_id
    })
    check("M1-3-3 使用人整体保存", status == 200 and body.get("code") == "Success", body)

    status, body = call("GET", "/m1/warehouses/%d/users" % warehouse_id, token=admin)
    check("M1-3-3 使用人含姓名部门", status == 200
          and body["data"][0]["username"] == "cangku01"
          and body["data"][0]["isDefault"] == 1
          and bool(body["data"][0]["deptName"]), body)

    # ---------- M1-4 字典 ----------
    status, body = call("POST", "/m1/dicts/types", token=admin, body={
        "typeCode": DICT_TYPE_CODE, "typeName": "冒烟测试字典"
    })
    check("M1-4-1 新增字典类型", status == 200 and body.get("code") == "Success", body)
    dict_type_id = body.get("data")

    status, body = call("POST", "/m1/dicts/types", token=buyer, body={
        "typeCode": DICT_TYPE_CODE, "typeName": "重复"
    })
    check("M1-4-1 采购员无字典创建权（403）", status == 403, body)

    status, body = call("GET", "/m1/dicts/types", token=admin)
    row = next((item for item in body["data"] if item["typeCode"] == DICT_TYPE_CODE), None)
    check("M1-4-1 类型列表含项数", status == 200 and row and row["itemCount"] == 0, body)

    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": dict_type_id, "itemCode": "A1", "itemName": "甲项", "sortNo": 1
    })
    check("M1-4-1 新增字典项", status == 200 and body.get("code") == "Success", body)
    dict_item_id = body.get("data")

    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": dict_type_id, "itemCode": "A1", "itemName": "重复编码"
    })
    check("M1-4-1 项编码类型内唯一", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("PUT", "/m1/dicts/items/%d" % dict_item_id, token=admin, body={
        "itemCode": "A2", "itemName": "改编码"
    })
    check("M1-4-1 项编码不可改", status == 200 and body.get("code") == "InvalidParameter", body)

    # M1-4-2 单据类型编号前缀
    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": 9, "itemCode": "9", "itemName": "无前缀单据"
    })
    check("M1-4-2 单据类型必须填前缀", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": 9, "itemCode": "9", "itemName": "坏前缀", "extValue": "cg-sq"
    })
    check("M1-4-2 前缀须大写", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": 9, "itemCode": "9", "itemName": "好前缀", "extValue": "CG-XY"
    })
    check("M1-4-2 合法前缀可保存", status == 200 and body.get("code") == "Success", body)

    status, body = call("POST", "/m1/dicts/items", token=admin, body={
        "typeId": 4, "itemCode": "9", "itemName": "坏供应商类型"
    })
    check("M1-4-2 供应商类型编码须 1 至 3", status == 200 and body.get("code") == "InvalidParameter", body)

    status, body = call("GET", "/m1/dicts/types/%s/items" % DICT_TYPE_CODE, token=admin)
    check("M1-4-1 管理端全量字典项", status == 200 and len(body["data"]) == 1, body)

    # ---------- 权限：仓管员只读 ----------
    status, body = call("POST", "/m1/materials", token=keeper, body={
        "materialNo": PREFIX + "-K1", "materialName": "仓管员建模", "categoryId": category_id,
        "unitId": unit_id, "materialType": 1
    })
    check("M1-1-2 仓管员无建模权（403）", status == 403, body)

    status, body = call("GET", "/m1/materials/page?pageNo=1&pageSize=10", token=keeper)
    check("M1-1-1 仓管员可读列表", status == 200 and body.get("code") == "Success", body)

    status, body = call("POST", "/m1/dicts/types", token=keeper, body={
        "typeCode": "smk3_keeper", "typeName": "越权"
    })
    check("M1-4-1 仓管员无字典创建权（403）", status == 403, body)

    # ---------- 汇总 ----------
    print("\n通过 %d 项，失败 %d 项" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  未过：" + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
