#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
轮次 9 冒烟测试：M10 知识库管理。

覆盖功能点：
    M10-1-1 文档上传（类型与大小校验后存储，md/docx 上传后自动解析）
    M10-1-2 正文解析（重新解析；pdf 记解析失败并指向正文补录）
    M10-1-3 解析结果校对（正文补录/修正后重建切块与索引）
    M10-1-4 文档分类与标签（上传与元数据维护落库）
    M10-2-1 切分粒度配置（sys_param kb 分组参数驱动）
    M10-2-2 条款边界保护（不跨编号条款切断；超长条款句界硬切+相邻重叠）
    M10-2-3 切块预览与调整（相邻合并 + 索引随文档重建）
    M10-3-1 向量化调用（本地确定性向量器产出并入库）
    M10-3-2 稀疏索引构建（字符二元组倒排）
    M10-3-3 索引增量更新（单文档重建）
    M10-3-4 索引一键重建（全量并记录耗时）
    M10-4-1 文档列表与检索（名称/分类/状态过滤）
    M10-4-2 版本与生效日期维护（元数据更新 + 同文档名新版本并存）
    M10-4-3 文档启用/停用（停用后不参与两路召回）
    M10-4-4 切块查看
    M10-4-5 文档删除与索引清理（物理删除后检索不再命中）
    M10-5-1 检索测试台（BM25 与向量两路 Top-K 并列 + 命中判定）
    M10-5-2 命中率统计（综合/分路命中率与平均命中排名）
    口径：无期望问句不判定、停用不召回、删除清索引、权限边界。

权限口径（与 sys_role_permission 种子一致）：
    m10 全部 11 个权限点只授系统管理员（admin），其余角色一律不持。

用法：python scripts/smoke_round9.py
前置：后端已在 http://localhost:8080 启动，数据库已导入 db 目录脚本，
且已执行 db/21_clean_smoke_round8.sql（演示基线：知识文档 2 篇、切块 10 个，
倒排索引与向量由本轮索引重建生成）。
测试数据落 smk9 前缀，跑完执行 db/22_clean_smoke_round9.sql 复原演示规模。
退出码 0 表示全部通过。
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import quote

BASE = "http://localhost:8080/api"
passed = 0
failed = []

# 每次运行使用唯一后缀，脚本可反复执行
SUFFIX = str(int(time.time()))[-6:]
MARK = "smk9" + SUFFIX
TODAY = time.strftime("%Y-%m-%d")
# 演示基线口径（14_init_kb_demo.sql）
DEMO_DOC1 = 1   # 采购管理制度（演示），6 个切块
DEMO_DOC2 = 2   # ERP 采购模块操作手册（演示），4 个切块
DEMO_CHUNKS = 10
# 切块参数（sys_param kb 分组）
CHUNK_SIZE = 500
CHUNK_OVERLAP = 50

# 短条款文档：12 条编号条款，验证条款边界保护与合并
MD_SHORT = """# 冒烟短条款制度（演示）

第一条 目的。为验证条款边界保护切块，本制度自造演示文本，不对应任何真实制度。

第二条 申请。需求部门根据生产计划提出采购申请，申请单应载明物料、数量与需求日期。

第三条 审批。采购申请单提交后，采购主管应在两个工作日内完成审批，驳回时应填写意见。

第四条 订单。审批通过的申请可转采购订单，订单明细的单价不得高于参考价太多。

第五条 到货。仓管员应在货物到达当日完成到货登记，并按要求判定合格与不合格数量。

第六条 验收。合格数量与不合格数量之和必须等于本次到货数量，守恒律不可违反。

第七条 入库。验收完成后按合格数量生成采购入库单，确认后库存数量方可增加。

第八条 作废。入库单作废时应冲减库存并保留冲减记录，不得直接修改库存数量。

第九条 价格。采购单价高于参考价百分之五记为关注，高于百分之十五记为显著。

第十条 追溯。入库单可溯源到验收单、采购订单与采购申请单，链条不可断裂。

第十一条 归档。验收单与入库单应按年度归档，归档后不得修改，仅可查询与导出。

第十二条 解释权。本制度由系统管理员负责解释，自发布之日起施行，解释权归管理部门。
"""

# 超长条款文档：单条 1000+ 字（>1.5 倍块长 750），验证句界硬切与相邻重叠，
# 切出 3+ 块供 M10-2-3「不相邻切块合并被拒」取 [0] 与 [2]
LONG_CLAUSE = (
    "第九十九条 超长条款。"
    + "采购活动应当遵守公开透明原则，任何部门与个人不得拆分采购批次规避审批，"
    "不得将应当集中采购的项目化整为零自行采购，不得以紧急需求为名免除必要的审批环节。"
    + "采购人员与供应商存在利害关系的，应当主动回避，采购主管部门有权要求其回避，"
    "回避决定作出前，相关采购流程暂停推进，待回避事项确认后再行恢复，回避记录存档备查。"
    + "采购过程形成的申请单、审批记录、订单、验收单、入库单与付款凭证应当保存至少十年，"
    "保存期限自采购项目结项之日起计算，销毁档案须经部门负责人与档案管理员双人复核，"
    "复核无异议后方可销毁，销毁清单作为会计档案的组成部分同步归档留存。"
    + "跨年度采购项目应当在每年年末对未完结部分进行结转，结转时核对已交付数量与在途数量，"
    "在途数量继续占用采购计划额度，不得因年度结转重复计算计划额度，避免超计划采购。"
    + "供应商报价明显偏离市场行情的，采购员应当在比价单中注明偏离原因，"
    "并经采购主管部门确认后方可进入审批流程，未经确认的偏离报价不得作为定标依据。"
    + "紧急采购事后应当补充完整审批手续，补办时限不得超过五个工作日，"
    "逾期未补办的，由采购主管部门通报批评并计入部门绩效考核记录。"
    + "验收不合格的物资应当就地封存，不得办理入库手续，"
    "由采购员联系供应商限期退换，退换期间采购订单保持未完结状态直至换货完成验收。"
    + "库存物资实行先进先出管理，仓管员每月末盘点并编制盘点表，"
    "盘盈盘亏须查明原因报主管部门审批，未经批准不得自行调整库存账面记录。"
    + "采购系统操作记录留痕备查，任何用户不得删除或篡改业务数据，"
    "系统管理员变更参数须填写变更申请并经审批后执行，变更记录保存三年。"
    + "本制度与其他管理办法不一致的，以本制度为准，"
    "制度修订须经采购管理部门起草、财务部门会签后发布施行。"
    + "采购付款须凭入库单与发票双单匹配，财务部门核对无误后按合同约定账期支付，"
    "预付款比例超过合同金额百分之三十的，须报分管领导审批。"
    + "年度采购计划每年第四季度编制，经主管部门审核汇总后提交单位领导办公会议审定，"
    "计划外临时采购项目须专项说明理由并走加急审批通道。"
    + "供应商履约评价每季度开展一次，评价结果作为后续采购定标的重要参考依据，"
    "连续两个季度评价不合格的供应商，暂停其投标资格六个月。"
    + "比价参与供应商不得少于三家，少于三家的须专项说明理由并经主管部门批准，"
    "比价过程形成的询价记录与供应商报价单作为审批附件一并上传系统存档。"
    + "采购合同变更须签订书面补充协议，涉及金额变更超过原合同百分之十的，"
    "须重新履行审批程序，补充协议与主合同同步编号归档管理。"
)
MD_LONG = "# 冒烟超长条款制度（演示）\n\n" + LONG_CLAUSE + "\n"


def call(method, path, token=None, body=None, raw=False):
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    # 查询参数里的中文需先做 percent 编码，否则 urllib 按 ASCII 编码 URL 直接抛
    # UnicodeEncodeError（docName=短条款制度 等场景）
    if "?" in path:
        base, query = path.split("?", 1)
        encoded = "&".join(
            "%s=%s" % (k, quote(v, safe=""))
            for k, _, v in (pair.partition("=") for pair in query.split("&")))
        path = base + "?" + encoded
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
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


def upload(path, token, fields, filename=None, content=None, content_type="application/octet-stream"):
    """multipart 上传；filename 为 None 时只传字段（用于不带文件的新版本）"""
    boundary = "----smoke9" + SUFFIX
    body = b""
    for key, value in fields.items():
        body += ("--" + boundary + "\r\n").encode()
        body += ('Content-Disposition: form-data; name="%s"\r\n\r\n' % key).encode()
        body += (str(value) + "\r\n").encode()
    if filename is not None:
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


def token_of(username):
    status, body = call("POST", "/auth/login", body={"username": username, "password": "123456"})
    assert status == 200 and body.get("code") == "Success", (username, body)
    return body["data"]["token"]


def f(value):
    """JSON 数字统一转 float 比较（冒烟脚本历史坑）。"""
    return float(value)


def split_clauses(text):
    """按「第X条」拆出条款（保留条款头；丢弃首个条款之前的标题等碎片）"""
    parts = re.split(r"(?=第[一二三四五六七八九十百零]+条)", text)
    return [p.strip() for p in parts if p.strip().startswith("第")]


def main():
    admin = token_of("admin")
    buyer = token_of("caigou01")
    manager = token_of("zhuguan01")
    keeper = token_of("cangku01")

    # ================= M10-3-4 索引一键重建（检索前置） =================
    print("== M10-3-4 索引一键重建 ==")
    status, body = call("POST", "/m10/index/rebuild", token=admin)
    check("M10-3-4 索引一键重建（演示 2 文档 10 切块全量建倒排与向量）",
          status == 200 and body.get("code") == "Success"
          and body["data"]["docCount"] == 2 and body["data"]["chunkCount"] == DEMO_CHUNKS
          and body["data"]["tokenCount"] > 0 and body["data"]["elapsedMs"] >= 0,
          body)

    # ================= M10-1-1 文档上传（md 自动解析） =================
    print("== M10-1-1 文档上传与自动解析 ==")
    status, body = upload("/m10/documents", admin, {
        "docName": MARK + "短条款制度",
        "docType": 1,
        "tags": "冒烟,制度",
        "version": "1.0",
        "effectiveDate": TODAY,
        "remark": "轮次 9 冒烟上传"
    }, filename=MARK + "_short.md", content=MD_SHORT.encode("utf-8"),
        content_type="text/markdown")
    check("M10-1-1 md 上传成功并返回文档 id",
          status == 200 and body.get("code") == "Success" and isinstance(body.get("data"), int),
          body)
    short_doc = body.get("data")
    status, body = call("GET", "/m10/documents/%d" % short_doc, token=admin)
    short_detail = body.get("data") or {}
    check("M10-1-1 md 上传后自动解析成功（parse_status=3）",
          short_detail.get("parseStatus") == 3 and short_detail.get("fileType") == "md"
          and short_detail.get("docType") == 1 and short_detail.get("tags") == "冒烟,制度",
          short_detail)
    check("M10-1-4 分类与标签落库（docType=1 制度）",
          short_detail.get("docTypeName") == "制度" and short_detail.get("version") == "1.0",
          short_detail)

    # 条款边界保护：12 条短条款打成 2 块，任何条款不被跨块切断
    status, body = call("GET", "/m10/documents/%d/chunks" % short_doc, token=admin)
    short_chunks = body.get("data") or []
    check("M10-4-4 切块查看（短条款文档切出 2 块）",
          status == 200 and body.get("code") == "Success" and len(short_chunks) == 2,
          [len(short_chunks), [c["charCount"] for c in short_chunks]])
    check("M10-2-2 条款边界保护：每条完整落在同一块内（不跨条切断）",
          all(any(c[:8] in ch["chunkText"] and c[-8:] in ch["chunkText"] for ch in short_chunks)
              for c in split_clauses(MD_SHORT)),
          [c[:12] for c in split_clauses(MD_SHORT)])
    check("M10-2-1 块长受 kb_chunk_size 约束（不超过 1.5 倍目标块长）",
          all(ch["charCount"] <= CHUNK_SIZE * 3 // 2 for ch in short_chunks),
          [ch["charCount"] for ch in short_chunks])

    # ================= M10-2-2 超长条款硬切与重叠 =================
    print("== M10-2-2 超长条款句界硬切 ==")
    status, body = upload("/m10/documents", admin, {
        "docName": MARK + "超长条款制度",
        "docType": 1,
        "version": "1.0",
        "effectiveDate": TODAY
    }, filename=MARK + "_long.md", content=MD_LONG.encode("utf-8"),
        content_type="text/markdown")
    long_doc = body.get("data")
    status, body = call("GET", "/m10/documents/%d/chunks" % long_doc, token=admin)
    long_chunks = body.get("data") or []
    texts = [c["chunkText"] for c in long_chunks]
    check("M10-2-2 测试构造：单条款字数超过 1.5 倍块长（触发硬切的前提）",
          len(LONG_CLAUSE) > CHUNK_SIZE * 3 // 2, [len(LONG_CLAUSE)])
    check("M10-2-2 超长条款切成多块且每块不超 1.5 倍块长",
          status == 200 and len(long_chunks) >= 3
          and all(len(t) <= CHUNK_SIZE * 3 // 2 for t in texts),
          [len(t) for t in texts])
    check("M10-2-2 相邻块保留 kb_chunk_overlap 个字重叠（句界硬切衔接）",
          all(texts[i + 1][:CHUNK_OVERLAP] == texts[i][-CHUNK_OVERLAP:]
              for i in range(len(texts) - 1)),
          [texts[i][-CHUNK_OVERLAP:] == texts[i + 1][:CHUNK_OVERLAP] for i in range(len(texts) - 1)])

    # ================= M10-1-1 pdf 上传与 M10-1-2 重新解析 =================
    print("== M10-1-1 pdf 上传与 M10-1-2 解析边界 ==")
    status, body = upload("/m10/documents", admin, {
        "docName": MARK + "扫描版制度",
        "docType": 1,
        "version": "1.0",
        "effectiveDate": TODAY
    }, filename=MARK + "_scan.pdf", content=b"%PDF-1.4 fake binary for smoke",
        content_type="application/pdf")
    pdf_doc = body.get("data")
    status, body = call("GET", "/m10/documents/%d" % pdf_doc, token=admin)
    pdf_detail = body.get("data") or {}
    check("M10-1-1 pdf 上传入库成功（parse_status=4 且备注指向正文补录）",
          pdf_detail.get("parseStatus") == 4 and "正文补录" in (pdf_detail.get("remark") or ""),
          pdf_detail)
    status, body = call("POST", "/m10/documents/%d/parse" % pdf_doc, token=admin)
    check("M10-1-2 pdf 重新解析被拒并提示补录（Unsupported）",
          body.get("code") == "Unsupported" and "正文补录" in body.get("message", ""),
          body)

    # M10-1-3 正文补录：粘贴文本后重建切块与索引
    status, body = call("PUT", "/m10/documents/%d/text" % pdf_doc, token=admin,
                        body={"text": "第一百条 补录条款。本段文本由管理员粘贴补录，用于验证解析结果校对入口。\n\n第一百零一条 补录第二条。补录文本同样走条款边界保护切块，并按文档重建倒排索引。"})
    check("M10-1-3 正文补录提交成功", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m10/documents/%d/chunks" % pdf_doc, token=admin)
    pdf_chunks = body.get("data") or []
    check("M10-1-3 补录后解析成功且切块可查（两条条款各自完整）",
          (body.get("code") == "Success") and len(pdf_chunks) >= 1
          and all(any(c[:8] in ch["chunkText"] and c[-8:] in ch["chunkText"] for ch in pdf_chunks)
                  for c in split_clauses("第一百条 补录条款。本段文本由管理员粘贴补录，用于验证解析结果校对入口。\n\n第一百零一条 补录第二条。补录文本同样走条款边界保护切块，并按文档重建倒排索引。")),
          [c["chunkText"][:20] for c in pdf_chunks])

    # ================= 上传类型校验 =================
    print("== M10-1-1 类型与大小校验 ==")
    status, body = upload("/m10/documents", admin, {
        "docName": MARK + "非法类型", "docType": 1, "version": "1.0", "effectiveDate": TODAY
    }, filename=MARK + "_bad.txt", content=b"plain text", content_type="text/plain")
    check("M10-1-1 非 pdf/docx/md 类型被拒（InvalidParameter）",
          body.get("code") == "InvalidParameter", body)

    # ================= M10-2-3 切块合并 =================
    print("== M10-2-3 切块合并 ==")
    status, body = call("POST", "/m10/documents/%d/chunks/merge" % short_doc, token=admin,
                        body={"chunkIds": [c["id"] for c in short_chunks]})
    check("M10-2-3 相邻切块合并成功并重建索引",
          status == 200 and body.get("code") == "Success" and body["data"]["chunkCount"] == 1,
          body)
    status, body = call("GET", "/m10/documents/%d/chunks" % short_doc, token=admin)
    merged = body.get("data") or []
    check("M10-2-3 合并后剩 1 块且文本为原两块拼接",
          len(merged) == 1 and "第一条" in merged[0]["chunkText"] and "第十二条" in merged[0]["chunkText"],
          [len(merged), [c["chunkIndex"] for c in merged]])
    status, body = call("POST", "/m10/documents/%d/chunks/merge" % long_doc, token=admin,
                        body={"chunkIds": [long_chunks[0]["id"], long_chunks[2]["id"]]})
    check("M10-2-3 不相邻切块合并被拒（条款边界保护）",
          body.get("code") == "InvalidParameter", body)

    # ================= M10-3-3 单文档索引重建 =================
    print("== M10-3-3 单文档索引重建 ==")
    status, body = call("POST", "/m10/documents/%d/index/rebuild" % long_doc, token=admin)
    check("M10-3-3 单文档索引重建（只重建该文档切块）",
          status == 200 and body.get("code") == "Success"
          and body["data"]["docCount"] == 1 and body["data"]["chunkCount"] == len(long_chunks)
          and body["data"]["tokenCount"] > 0,
          body)

    # ================= M10-4-1 文档列表与检索 =================
    print("== M10-4-1 文档列表与检索 ==")
    status, body = call("GET", "/m10/documents?docName=%s&pageNo=1&pageSize=10" % MARK, token=admin)
    check("M10-4-1 按名称检索到冒烟文档（3 篇：短条款/超长/扫描）",
          body.get("code") == "Success" and body["data"]["total"] == 3,
          body.get("data", {}).get("total"))
    status, body = call("GET", "/m10/documents?docType=1&status=1&pageNo=1&pageSize=10", token=admin)
    check("M10-4-1 按分类与状态过滤（演示 2 篇中仅制度类 1 篇 + 冒烟 3 篇，共 4）",
          body.get("code") == "Success" and body["data"]["total"] == 4,
          body.get("data", {}).get("total"))

    # ================= M10-4-2 版本与生效日期 =================
    print("== M10-4-2 版本与生效日期 ==")
    status, body = call("PUT", "/m10/documents/%d" % short_doc, token=admin, body={
        "docName": MARK + "短条款制度",
        "docType": 1,
        "tags": "冒烟,制度",
        "version": "1.1",
        "effectiveDate": TODAY,
        "remark": "修订版本号与生效日期"
    })
    check("M10-4-2 元数据维护（版本 1.0→1.1）", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m10/documents/%d" % short_doc, token=admin)
    check("M10-4-2 版本与生效日期已更新",
          body["data"]["version"] == "1.1" and body["data"]["effectiveDate"] == TODAY,
          body.get("data", {}).get("version"))
    status, body = upload("/m10/documents/%d/versions" % short_doc, admin, {
        "version": "2.0",
        "effectiveDate": TODAY,
        "remark": "升版不带文件，复制来源版本切块"
    })
    check("M10-4-2 新版本创建成功（同文档名并存）",
          status == 200 and body.get("code") == "Success" and isinstance(body.get("data"), int),
          body)
    version_doc = body.get("data")
    status, body = call("GET", "/m10/documents/%d" % version_doc, token=admin)
    check("M10-4-2 新版本复制来源切块（版本 2.0，1 块）",
          body["data"]["version"] == "2.0" and body["data"]["chunkCount"] == 1
          and body["data"]["parseStatus"] == 3,
          body.get("data"))
    status, body = call("GET", "/m10/documents?docName=%s短条款制度" % MARK, token=admin)
    check("M10-4-2 同文档名多版本并存（检索按生效日期取现行有效，列表两行都在）",
          body["data"]["total"] == 2, body.get("data", {}).get("total"))

    # ================= M10-4-3 启用/停用与召回 =================
    print("== M10-4-3 启用停用与召回 ==")
    status, body = upload("/m10/documents", admin, {
        "docName": MARK + "独有词汇制度",
        "docType": 2,
        "version": "1.0",
        "effectiveDate": TODAY
    }, filename=MARK + "_uniq.md",
        content=("# 冒烟独有词汇规范\n\n第二百条 独有词汇。本文档包含 zzqq 冒烟独有词汇 zzqq，用于验证停用后不参与召回。\n").encode("utf-8"),
        content_type="text/markdown")
    uniq_doc = body.get("data")
    status, body = call("POST", "/m10/retrieval-tests", token=admin,
                        body={"question": "zzqq 独有词汇"})
    check("M10-5-1 独有词汇问句可召回（启用状态）",
          body.get("code") == "Success" and len(body["data"]["bm25"]) >= 1
          and body["data"]["bm25"][0]["docId"] == uniq_doc,
          body.get("data", {}).get("bm25"))
    status, body = call("POST", "/m10/documents/%d/toggle" % uniq_doc, token=admin)
    check("M10-4-3 停用成功", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m10/documents/%d" % uniq_doc, token=admin)
    check("M10-4-3 状态为停用", body["data"]["status"] == 2, body.get("data", {}).get("status"))
    status, body = call("POST", "/m10/retrieval-tests", token=admin,
                        body={"question": "zzqq 独有词汇"})
    check("M10-4-3 停用后两路均不召回该文档（其他文档仍可被召回，属正常）",
          body.get("code") == "Success" and len(body["data"]["bm25"]) == 0
          and all(h["docId"] != uniq_doc for h in body["data"]["vector"]),
          {"bm25": len(body["data"]["bm25"]),
           "vectorDocs": [h["docId"] for h in body["data"]["vector"]]})
    status, body = call("POST", "/m10/documents/%d/toggle" % uniq_doc, token=admin)
    status, body = call("POST", "/m10/retrieval-tests", token=admin,
                        body={"question": "zzqq 独有词汇"})
    check("M10-4-3 重新启用后恢复召回",
          body.get("code") == "Success" and len(body["data"]["bm25"]) >= 1,
          body.get("data", {}).get("bm25"))

    # ================= M10-5-1 检索测试台（双路对照） =================
    print("== M10-5-1 检索测试台 ==")
    status, body = call("POST", "/m10/retrieval-tests", token=admin, body={
        "question": "采购申请提交后，采购主管应该在几个工作日内完成审批？",
        "expectDocId": DEMO_DOC1,
        "testBatch": MARK
    })
    test1 = body.get("data") or {}
    check("M10-5-1 审批时限问句两路均命中演示文档 1",
          body.get("code") == "Success" and test1.get("hitFlag") == 1
          and test1.get("hitRoute") == 3
          and len(test1.get("bm25", [])) >= 1 and len(test1.get("vector", [])) >= 1
          and test1["bm25"][0]["docId"] == DEMO_DOC1,
          {"hit": test1.get("hitFlag"), "route": test1.get("hitRoute"),
           "bm25Top": test1.get("bm25", [{}])[0].get("docId")})
    status, body = call("POST", "/m10/retrieval-tests", token=admin, body={
        "question": "入库单确认时如何核对批次号与库位",
        "expectDocId": DEMO_DOC2,
        "testBatch": MARK
    })
    test2 = body.get("data") or {}
    check("M10-5-1 入库确认问句命中演示文档 2（条款级引用带章节号）",
          body.get("code") == "Success" and test2.get("hitFlag") == 1
          and test2["bm25"][0]["docId"] == DEMO_DOC2,
          {"hit": test2.get("hitFlag"), "top": test2.get("bm25", [{}])[0]})
    status, body = call("POST", "/m10/retrieval-tests", token=admin, body={
        "question": "今天天气怎么样适合去哪里游玩",
        "expectDocId": DEMO_DOC1,
        "testBatch": MARK
    })
    test3 = body.get("data") or {}
    check("M10-5-1 无关问句不误命中（两路无结果或未命中）",
          body.get("code") == "Success" and test3.get("hitFlag") == 0,
          {"bm25": len(test3.get("bm25", [])), "vector": len(test3.get("vector", []))})
    status, body = call("POST", "/m10/retrieval-tests", token=admin,
                        body={"question": "采购申请", "testBatch": MARK})
    test4 = body.get("data") or {}
    check("M10-5-1 无期望问句只展示不判定（hitFlag=0 但两路有结果）",
          body.get("code") == "Success" and test4.get("hitFlag") == 0
          and len(test4.get("bm25", [])) >= 1 and len(test4.get("vector", [])) >= 1,
          {"hit": test4.get("hitFlag")})
    status, body = call("GET", "/m10/retrieval-tests/stats?testBatch=%s" % MARK, token=admin)
    stat = body.get("data") or {}
    check("M10-5-2 命中率统计（4 条测试 2 命中，综合 0.5，两路均 0.5）",
          body.get("code") == "Success" and stat.get("total") == 4
          and stat.get("hitCount") == 2 and f(stat.get("hitRate")) == 0.5
          and f(stat.get("bm25Rate")) == 0.5 and f(stat.get("vectorRate")) == 0.5
          and stat.get("bothHit") == 2 and f(stat.get("avgHitRank")) >= 1.0,
          stat)

    # ================= M10-4-5 删除与索引清理 =================
    print("== M10-4-5 删除与索引清理 ==")
    status, body = call("DELETE", "/m10/documents/%d" % version_doc, token=admin)
    check("M10-4-5 删除新版本文档", status == 200 and body.get("code") == "Success", body)
    status, body = call("GET", "/m10/documents/%d" % version_doc, token=admin)
    check("M10-4-5 删除后详情 NotFound", body.get("code") == "NotFound", body)
    status, body = call("DELETE", "/m10/documents/%d" % uniq_doc, token=admin)
    check("M10-4-5 删除独有词汇文档", status == 200 and body.get("code") == "Success", body)
    status, body = call("POST", "/m10/retrieval-tests", token=admin,
                        body={"question": "zzqq 独有词汇"})
    check("M10-5-1 删除独有词汇文档后两路不再命中（索引已清理，其他文档仍可召回）",
          len(body["data"]["bm25"]) == 0
          and all(h["docId"] != uniq_doc for h in body["data"]["vector"]),
          {"bm25": len(body["data"]["bm25"]),
           "vectorDocs": [h["docId"] for h in body["data"]["vector"]]})

    # ================= 下载 =================
    print("== 文件下载 ==")
    status, content, headers = call("GET", "/m10/documents/%d/download" % long_doc,
                                    token=admin, raw=True)
    check("上传文件可下载且内容一致",
          status == 200 and MD_LONG.encode("utf-8") in content, (status, len(content)))

    # ================= 权限边界 =================
    print("== 权限边界 ==")
    for name, token in (("采购员", buyer), ("采购主管", manager), ("仓管员", keeper)):
        status, body = call("GET", "/m10/documents", token=token)
        check("M10 权限只授系统管理员：%s查文档列表 NoPermission" % name,
              body.get("code") == "NoPermission", body)
    status, body = upload("/m10/documents", manager, {
        "docName": MARK + "越权上传", "docType": 1, "version": "1.0", "effectiveDate": TODAY
    }, filename=MARK + "_x.md", content=b"# x\n", content_type="text/markdown")
    check("M10 权限只授系统管理员：主管上传 NoPermission",
          body.get("code") == "NoPermission", body)
    status, body = call("POST", "/m10/retrieval-tests", token=buyer,
                        body={"question": "采购申请"})
    check("M10 权限只授系统管理员：采购员跑检索测试 NoPermission",
          body.get("code") == "NoPermission", body)
    status, body = call("GET", "/m10/documents", token=None)
    check("无 token 访问文档列表 401", status == 401, status)

    print("\n============================================================")
    print("轮次 9 冒烟结果：%d 通过，%d 失败" % (passed, len(failed)))
    if failed:
        for name in failed:
            print("  FAIL " + name)
        sys.exit(1)


main()
