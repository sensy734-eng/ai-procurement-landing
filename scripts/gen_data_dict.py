# -*- coding: utf-8 -*-
"""
数据字典生成器

从 information_schema 读取真实表结构，生成：
  DATA_DICTIONARY.md      —— 按八域组织的完整数据字典
  docs/data_dictionary.csv —— 同一份内容的表格版（Excel 可直接打开筛选）

为什么用脚本而不是手写：表结构会随开发变化，手写的数据字典必然失真。
脚本每次从库里读，改完表重跑一次即可；表的中文说明与业务用途写在下方元数据里，
它不随结构变化，属于需要人工维护的部分。

用法：
    python scripts/gen_data_dict.py
    python scripts/gen_data_dict.py --check    # 只校验元数据是否覆盖了全部表
"""
import subprocess
import sys
import io
import os

MYSQL = os.environ.get("MYSQL_BIN", "E:/XAMPP/mysql/bin/mysql.exe")
DB = os.environ.get("MYSQL_DB", "procurement_db")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MD_OUT = os.path.join(ROOT, "DATA_DICTIONARY.md")
CSV_OUT = os.path.join(ROOT, "docs", "data_dictionary.csv")

# ---------------------------------------------------------------- 八域定义
# (域代号, 域名称, 建表脚本, [表名...])  表按业务阅读顺序排列，不按字母序
DOMAINS = [
    ("A", "基础数据域", "db/01_schema_a_base.sql", [
        "material_category", "material", "material_ext_attr",
        "supplier", "supplier_contact", "supplier_attachment", "supplier_scope",
        "warehouse", "warehouse_location", "warehouse_user",
        "dict_type", "dict_item", "owner_object",
    ]),
    ("B", "采购单据域", "db/02_schema_b_bill.sql", [
        "purchase_request", "purchase_request_item",
        "purchase_order", "purchase_order_item",
        "order_change_log", "delivery_progress",
        "arrival_inspection", "arrival_inspection_item",
        "inbound_order", "inbound_item",
    ]),
    ("C", "库存域", "db/03_schema_c_stock.sql", [
        "stock_balance", "stock_flow",
    ]),
    ("D", "审批域", "db/04_schema_d_approval.sql", [
        "approval_flow_node", "approval_task",
    ]),
    ("E", "价格与参数域", "db/05_schema_e_price.sql", [
        "price_reference", "price_stat_snapshot", "sys_param",
    ]),
    ("F", "平台权限域", "db/06_schema_f_platform.sql", [
        "sys_user", "sys_role", "sys_permission", "sys_role_permission",
        "sys_user_role", "sys_data_scope", "sys_operation_log", "sys_session",
    ]),
    ("G", "知识库域", "db/07_schema_g_kb.sql", [
        "kb_document", "kb_chunk", "kb_sparse_index", "kb_reject_log", "kb_retrieval_test",
    ]),
    ("H", "智能助手域", "db/08_schema_h_ai.sql", [
        "ai_session", "ai_message", "ai_intent", "ai_intent_param",
        "ai_entity_alias", "ai_query_audit",
    ]),
]

# ------------------------------------------------- 表的中文名与业务用途（人工维护）
# 中文名优先取建表脚本里的表注释前缀，用途一句话讲"这张表解决什么问题"
TABLE_DESC = {
    # A 基础数据域
    "material_category": "物料分类树。parent_id 自引用成树，用 level 与 sort_no 控制层级与排序；分类是物料的归类维度，也是报表按分类汇总的键。",
    "material": "物料主数据。物料编号全局唯一，挂分类、计量单位与安全库存；安全库存是 M5 预警与 M12 库存不足图的判定依据。",
    "material_ext_attr": "物料扩展属性。用 attr_key/attr_value 键值对承载非通用属性，避免为个别物料的特殊字段反复改主表。",
    "supplier": "供应商主数据。供应商编号唯一，状态区分启用与停用；联系人与资质拆到子表，供货范围单独成表。",
    "supplier_contact": "供应商联系人。一家供应商可有多位联系人，用 is_primary 标记主联系人。",
    "supplier_attachment": "供应商资质附件。登记资质类型与有效期，供准入审核取证。",
    "supplier_scope": "供应商供货范围。声明该供应商能供哪些物料（或整类），下单与申请时是**硬校验**——范围外的物料直接拒绝。",
    "warehouse": "仓库档案。库存的物理维度，也是仓管员数据范围的取值来源。",
    "warehouse_location": "库位。仓库下的具体存放位置，入库确认前必须逐行补登。",
    "warehouse_user": "仓库使用人关系。记录仓管员分管哪些仓库，是 sys_data_scope 之外更细的仓库归属。",
    "dict_type": "字典类型。统一管理性别、单位、部门、物料类型等下拉数据的数据源。",
    "dict_item": "字典项。注意：**部门字典（type=dept）的项 id 就是部门 id**，不是项编码，跨表关联时按 id 走。",
    "owner_object": "归属对象。区分公共库存与项目/工单专用库存。全系统统一走 owner_id=1 / owner_no='PUBLIC' 的公共库存行，不在各处硬编码 0。",
    # B 采购单据域
    "purchase_request": "采购申请单主表。三状态（生命周期/审核/无履约）中的两个：lifecycle_status 与 audit_status；驳回时生命周期回落草稿，由 audit_status=3 表达驳回。",
    "purchase_request_item": "采购申请明细行。item_status 表达转单进度（待转/部分已转/已转完/已退回/已关闭），transferred_qty 记录已转数量。",
    "purchase_order": "采购订单主表。source_request_id 记录来源申请，构成单据链追溯的一环；lifecycle/audit/fulfill 三状态正交，互不推导。",
    "purchase_order_item": "采购订单明细行。delivered_qty 是已交付量（只加合格量），item_fulfill_status 是行级履约；deviation_rate/level 存价格偏离结果。",
    "order_change_log": "订单变更历史。反审、作废、改价等动作逐条留痕，按自增 id 判先后——秒级时间戳分不清同秒的多次操作。",
    "delivery_progress": "交付进度登记。M3-5-1 的分批交付凭据，是订单反审与作废的禁止条件之一。",
    "arrival_inspection": "到货验收单主表。status 1 草稿 / 2 已验收；inspection_result 由明细重算（全合格→1、有不合格未让步→2、有不合格且让步→3）。",
    "arrival_inspection_item": "到货验收明细。守恒约束的落地点：合格量 + 不合格量 = 到货量，登记与提交双校验。",
    "inbound_order": "采购入库单主表。status 1 草稿 / 2 已确认 / 3 已作废；**只有确认动作会写库存**，inspection_id 指回验收单。",
    "inbound_item": "采购入库明细。quantity 只含合格量（让步时含被放行的不合格量）；location_id 确认前必须补齐，batch_no 是库存批次号。",
    # C 库存域
    "stock_balance": "库存余额。唯一键 = 物料 + 仓库 + 批次 + 归属，是库存的当前状态；**可用量刻意不落库**（数量 − 冻结 − 预留现算），避免派生值失同步。",
    "stock_flow": "库存变动流水。记录 before_qty → after_qty 与来源单据，是「余额 = 流水累计」对账的依据；流水只增不删。",
    # D 审批域
    "approval_flow_node": "审批流程节点配置。bill_type + node_seq 定义几级审批，sign_type 区分或签（任一通过即推进）与会签（全部通过才推进）。",
    "approval_task": "审批任务。每个审批人一条任务，task_status 1 待处理 / 2 通过 / 3 驳回 / 4 失效；审批历史不另建表，按 node_seq 与 id 排序即得。",
    # E 价格与参数域
    "price_reference": "物料价格参考。同一物料 price_version 递增成版本链，旧版永久保留；status=1 且 effective_date≤基准日 才算生效。",
    "price_stat_snapshot": "成交价统计快照。按「最近 N 笔 / 最近 N 天」窗口预计算，与参考价严格分离——历史成交不等于当前报价。",
    "sys_param": "系统参数。param_group + param_key 唯一，价格偏离阈值、交付统计窗口等口径全部走这里，不散落在代码常量里。",
    # F 平台权限域
    "sys_user": "用户。密码存哈希，状态区分启用停用；用户是权限与数据范围的授权主体。",
    "sys_role": "角色。采购员、采购主管、仓管员、系统管理员四类业务角色 + 基础角色。",
    "sys_permission": "权限点。perm_code 采用「模块:对象:动作」三段式（如 m5:inbound:confirm），接口上以 @RequirePermission 声明。",
    "sys_role_permission": "角色权限关联。多对多中间表，决定角色能调哪些接口。",
    "sys_user_role": "用户角色关联。多对多中间表，一个用户可兼多个角色，权限取并集。",
    "sys_data_scope": "数据范围授权。按仓库/部门/归属对象三个维度限制可见数据；subject_type 区分授给用户还是角色。缺部门范围时**失败关闭**，缺仓库范围则不过滤。",
    "sys_operation_log": "操作日志。记录谁在何时对什么做了什么；全库唯一不做清理的表，审计追溯靠它。",
    "sys_session": "登录会话。登录签发的 token 及其有效期，是鉴权拦截器的第一道查证。",
    # G 知识库域
    "kb_document": "知识文档。parse_status 记录解析链路状态（待解析/解析中/成功/失败），status 停用与失效不参与默认召回；删除走物理删除并清索引。",
    "kb_chunk": "文档切块。切块尊重条款边界（第X条、3.2.1、一、），条款型问句才能原文直引；chunk_no 保序，chunk_text 是检索与引用的最小单位。",
    "kb_sparse_index": "稀疏检索倒排索引。二元组词元 → 文档列表，**派生数据**，可随时从切块全量重建。",
    "kb_reject_log": "拒答记录。记录被拒的问题与判定依据，用于度量拒答率；纯事件记录，无查询侧需求，故不建实体类。",
    "kb_retrieval_test": "检索测试记录。人工发起检索测试的留档，含查询词、命中切块与排序，用于调参与效果取证。",
    # H 智能助手域
    "ai_session": "智能会话。多轮对话的容器，一条会话下挂多条消息。",
    "ai_message": "会话消息。role 区分用户与助手，msg_type 区分知识类/教程类/数据类/澄清/拒答/纯文本，result_code 存七类结果码。",
    "ai_intent": "意图白名单。受控查询只认这里启用的五类意图（订单状态/交付进度/历史价格/库存余量/供应商交付），不在白名单的问题进不来。",
    "ai_intent_param": "意图参数规格。每个意图允许哪些参数、什么类型、允许哪些操作符与枚举值——这是四道闸里「参数校验」的判定表。",
    "ai_entity_alias": "实体别名映射。把「一号仓」「华东五金」这类口语说法映射到真实 id，实体归一与多候选消歧靠它。",
    "ai_query_audit": "智能查询审计。一次问答一行，params_json 记提取到的参数、rejected_params_json 记被拒参数、data_scope_json 记注入的数据范围，是「受控」的可验证凭据。",
}

KEY_LABEL = {"PRI": "PK", "UNI": "UK", "MUL": "IDX", "": ""}


def query(sql):
    out = subprocess.run([MYSQL, "-uroot", "--default-character-set=utf8mb4", DB,
                          "-N", "-B", "-e", sql],
                         capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise RuntimeError("mysql 查询失败：%s\n%s" % (sql[:80], out.stderr))
    return [l.split("\t") for l in out.stdout.rstrip("\n").split("\n") if l.strip()]


def load():
    cols = query("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, "
                 "IFNULL(COLUMN_DEFAULT,''), COLUMN_KEY, EXTRA, COLUMN_COMMENT "
                 "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='%s' "
                 "ORDER BY TABLE_NAME, ORDINAL_POSITION" % DB)
    tbls = {r[0]: (r[1] if len(r) > 1 else "") for r in
            query("SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
                  "WHERE TABLE_SCHEMA='%s'" % DB)}
    idx = query("SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, "
                "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') "
                "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='%s' "
                "GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE "
                "ORDER BY TABLE_NAME, INDEX_NAME" % DB)
    all_tables = list(tbls.keys())
    parts = " UNION ALL ".join("SELECT '%s' AS t, COUNT(*) AS c FROM `%s`" % (t, t)
                               for t in all_tables)
    counts = {r[0]: int(r[1]) for r in query("SELECT * FROM (%s) x" % parts)}
    return cols, tbls, idx, counts


def short_comment(comment):
    """表注释形如「物料主表 M1-1」，拆出中文名与功能点编号。"""
    c = comment.strip()
    for sep in (" ", "（"):
        if sep in c:
            head, tail = c.split(sep, 1)
            if tail.startswith("M") or sep == "（":
                fp = tail.strip("（）() ") if sep == "（" else tail.strip()
                return head.strip(), (fp if fp.startswith("M") else "")
    return c, ""


def fmt_default(v):
    if v == "" or v.lower() == "null":
        return "—"
    if v.upper() == "CURRENT_TIMESTAMP":
        return "CURRENT_TIMESTAMP"
    return "`%s`" % v


def main():
    check_only = "--check" in sys.argv
    cols, tbls, idx, counts = load()

    # 覆盖性自检：库里的表必须在 DOMAINS 里，且必须有用途说明
    declared = [t for _, _, _, ts in DOMAINS for t in ts]
    missing_in_domains = sorted(set(tbls) - set(declared))
    missing_in_db = sorted(set(declared) - set(tbls))
    missing_desc = sorted(set(declared) - set(TABLE_DESC))
    dup = sorted({t for t in declared if declared.count(t) > 1})
    problems = []
    if missing_in_domains:
        problems.append("库里有表未归域：%s" % ", ".join(missing_in_domains))
    if missing_in_db:
        problems.append("归域里有表但库里没有：%s" % ", ".join(missing_in_db))
    if missing_desc:
        problems.append("表缺少用途说明：%s" % ", ".join(missing_desc))
    if dup:
        problems.append("重复归域：%s" % ", ".join(dup))
    total_cols = len(cols)
    if sum(len([c for c in cols if c[0] == t]) for t in declared) != total_cols:
        problems.append("字段数与表清单不一致")
    if problems:
        print("校验未通过：")
        for p in problems:
            print("  -", p)
        return 1
    print("校验通过：%d 张表 / %d 个字段，全部归域且有用途说明" % (len(declared), total_cols))
    if check_only:
        return 0

    by_table = {}
    for c in cols:
        by_table.setdefault(c[0], []).append(c)
    idx_by = {}
    for i in idx:
        idx_by.setdefault(i[0], []).append(i)

    L = []
    W = L.append
    W("# 采购管理系统 数据字典")
    W("")
    W("> 本文件由 `scripts/gen_data_dict.py` 从 `information_schema` 直接读取生成，")
    W("> 不属于手写文档——表结构改了重跑一次即可，避免文档与实现脱节。")
    W("> 生成时间：%s　库：`%s`　规模：%d 张表 / %d 个字段 / 当前 %d 行数据"
      % (__import__("datetime").date.today().isoformat(), DB, len(declared), total_cols,
         sum(counts.values())))
    W("")
    W("阅读约定：")
    W("")
    W("- **键**列：`PK` 主键、`UK` 唯一键组成部分、`IDX` 普通索引列。")
    W("- **空**列：`是` 允许 NULL，`否` 非空。")
    W("- **默认**列：`—` 表示无默认值。")
    W("- **说明**列取自列的 COMMENT，枚举字段的取值含义直接写在里面（如「状态：1 生效、2 停用」）。")
    W("- 行数是当前库的实际值，反映的是**演示数据规模**，不等于设计容量。")
    W("- 通用字段是**按需裁剪**而非全表照抄：`created_at` 与 `is_deleted` 49 张表全有，")
    W("  `updated_at` 44 张、`version` 43 张、`created_by` 37 张、`updated_by` 10 张，")
    W("  缺少的那几张是只增不改的表（消息、审计、操作日志、拒答记录），见 10.1 说明。")
    W("")
    W("---")
    W("")
    W("## 一、总览")
    W("")
    W("数据库共 %d 张表，按业务内聚划分为八个域，每域由一支建表脚本建立：" % len(declared))
    W("")
    W("| 域 | 名称 | 建表脚本 | 表数 | 字段数 | 内容 |")
    W("|---|---|---|---|---|---|")
    domain_content = {
        "A": "物料、分类、扩展属性、供应商（主表/联系人/资质/供货范围）、仓库、库位、仓库使用人、字典、归属对象",
        "B": "采购申请（主表/明细）、采购订单（主表/明细）、变更历史、交付登记、到货验收（主表/明细）、入库（主表/明细）",
        "C": "库存余额、库存变动流水",
        "D": "审批流程节点配置、审批任务",
        "E": "价格参考、成交价统计快照、系统参数",
        "F": "用户、角色、权限点、两张关联表、数据范围、操作日志、登录会话",
        "G": "知识文档、切块、稀疏倒排索引、拒答记录、检索测试",
        "H": "会话、消息、意图白名单、意图参数规格、实体别名、查询审计",
    }
    for code, name, script, ts in DOMAINS:
        ncol = sum(len(by_table.get(t, [])) for t in ts)
        W("| %s | %s | `%s` | %d | %d | %s |"
          % (code, name, script, len(ts), ncol, domain_content[code]))
    W("| — | **合计** | — | **%d** | **%d** | — |" % (len(declared), total_cols))
    W("")
    W("表清单（按域内业务顺序，含功能点编号与当前行数）：")
    W("")
    W("| # | 表名 | 中文名 | 功能点 | 字段数 | 行数 |")
    W("|---:|---|---|---|---:|---:|")
    n = 0
    for code, name, script, ts in DOMAINS:
        for t in ts:
            n += 1
            cn, fp = short_comment(tbls.get(t, ""))
            W("| %d | `%s` | %s | %s | %d | %d |"
              % (n, t, cn, fp or "—", len(by_table.get(t, [])), counts.get(t, 0)))
    W("")
    W("---")
    W("")

    # ---------------------------------------------------------- 逐域展开
    sec = ["二", "三", "四", "五", "六", "七", "八", "九"]
    for i, (code, name, script, ts) in enumerate(DOMAINS):
        ncol = sum(len(by_table.get(t, [])) for t in ts)
        W("## %s、%s（%d 张表 / %d 字段）" % (sec[i], name, len(ts), ncol))
        W("")
        W("建表脚本 `%s`。" % script)
        W("")
        for t in ts:
            cn, fp = short_comment(tbls.get(t, ""))
            rows = by_table.get(t, [])
            W("### %s　%s%s" % (t, cn, ("（%s）" % fp) if fp else ""))
            W("")
            W(TABLE_DESC[t])
            W("")
            W("| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |")
            W("|---:|---|---|:--:|---|:--:|---|")
            for k, r in enumerate(rows, 1):
                _, colname, ctype, nullable, dflt, key, extra, comment = r[:8]
                keytxt = KEY_LABEL.get(key, key)
                if "auto_increment" in extra:
                    keytxt = (keytxt + " AI").strip()
                W("| %d | `%s` | `%s` | %s | %s | %s | %s |"
                  % (k, colname, ctype, "是" if nullable == "YES" else "否",
                     fmt_default(dflt), keytxt or "", comment.replace("|", "\\|")))
            W("")
            # 索引
            idxs = idx_by.get(t, [])
            uk = [x for x in idxs if x[2] == "0"]
            ik = [x for x in idxs if x[2] == "1"]
            if uk or ik:
                W("索引：")
                W("")
                for x in uk:
                    W("- 唯一 `%s`（%s）" % (x[1], x[3]))
                for x in ik:
                    W("- 普通 `%s`（%s）" % (x[1], x[3]))
                W("")
        W("---")
        W("")

    # ---------------------------------------------------------- 附录
    W("## 十、全局约定与状态码总表")
    W("")
    W("### 10.1 通用字段")
    W("")
    W("通用字段是**按表裁剪**的，不是每张表都照抄一套。实测分布：")
    W("")
    W("| 字段 | 类型 | 含义 | 覆盖表数 |")
    W("|---|---|---|---:|")
    W("| `id` | bigint | 自增主键。跨表关联一律用 id，不用业务编号 | 49 / 49 |")
    W("| `created_at` | datetime | 创建时间，默认 `current_timestamp()` | 49 / 49 |")
    W("| `is_deleted` | tinyint | 逻辑删除标记：0 未删、1 已删 | 49 / 49 |")
    W("| `updated_at` | datetime | 最后修改时间 | 44 / 49 |")
    W("| `version` | int | 乐观锁版本号，并发更新时用 | 43 / 49 |")
    W("| `created_by` | bigint | 创建人 id | 37 / 49 |")
    W("| `updated_by` | bigint | 最后修改人 id | 10 / 49 |")
    W("| `remark` | varchar | 业务备注，冒烟脚本用它打 `smk` 前缀以便清理 | 若干 |")
    W("")
    W("缺 `updated_at` 的正好是五张**只增不改**的表：`ai_message`、`ai_query_audit`、")
    W("`kb_reject_log`、`kb_retrieval_test`、`sys_operation_log`。消息写出去就不改、审计与日志")
    W("写一次就定型——给这类表加 `updated_at` 只会得到一个恒等于 `created_at` 的字段，")
    W("反而让人误以为它会被更新。同理，它们也不带 `version`：没有并发更新就不需要乐观锁。")
    W("")
    W("还有三处是刻意的不对称，读表时容易误解，先说清楚：**库存流水的 `is_deleted` 恒为 0**")
    W("（流水只增不删，作废靠写反向冲减流水）；**操作日志不提供删除入口**；")
    W("**`kb_*` 删除走物理删除**并同步清索引，因为切块与倒排索引是派生数据，")
    W("留逻辑删除的壳只会让索引与库不一致。")
    W("")
    W("### 10.2 单据状态码")
    W("")
    W("采购申请与采购订单**不用单状态机**，而是三个互不推导的字段并行表达三件事")
    W("（「审核通过、部分交付、后被关闭」用单状态装不下）：")
    W("")
    W("| 字段 | 含义 | 取值 |")
    W("|---|---|---|")
    W("| `lifecycle_status` | 单据生命周期 | 1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 |")
    W("| `audit_status` | 审核结果 | 1 未审核、2 通过、3 驳回 |")
    W("| `fulfill_status` | 履约进度（仅订单） | 1 待交付、2 部分交付、3 全部交付、4 已关闭 |")
    W("")
    W("驳回时 `lifecycle_status` 回落为 1（草稿），由 `audit_status=3` 表达驳回——")
    W("不新增第 6 个生命周期值，否则「草稿」与「被驳回的草稿」会在列表里分成两种状态、两套筛选。")
    W("")
    W("### 10.3 各单据状态码汇总")
    W("")
    W("| 对象 | 字段 | 取值 |")
    W("|---|---|---|")
    W("| 申请明细行 | `item_status` | 1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭 |")
    W("| 订单明细行 | `item_fulfill_status` | 1 待交付、2 部分交付、3 全部交付、4 已关闭 |")
    W("| 订单明细行 | `deviation_level` | 0 正常、1 关注、2 显著（按偏离**绝对值**比 sys_param 阈值） |")
    W("| 到货验收单 | `status` / `inspection_result` | status：1 草稿、2 已验收；result：1 合格、2 不合格、3 让步接收 |")
    W("| 入库单 | `status` | 1 草稿、2 已确认、3 已作废 |")
    W("| 审批任务 | `task_status` | 1 待处理、2 已通过、3 已驳回、4 已失效 |")
    W("| 审批节点 | `sign_type` | 1 或签（任一通过即推进）、2 会签（全部通过才推进） |")
    W("| 知识文档 | `parse_status` | 1 待解析、2 解析中、3 解析成功、4 解析失败 |")
    W("| 知识文档 | `status` | 1 启用、2 停用、3 失效（停用与失效不参与默认召回） |")
    W("| 库存流水 | `change_type` | 1 入库增加、2 作废冲减 |")
    W("| 价格参考 | `status` | 1 生效、2 停用 |")
    W("| 助手消息 | `msg_type` | 1 知识类、2 教程类、3 数据类、4 澄清、5 拒答、6 纯文本 |")
    W("")
    W("### 10.4 七类结果码")
    W("")
    W("接口统一返回 `{code, message, data}`，`code` 取值集合固定为七个，")
    W("**`NotFound`（对象不存在）与 `NoData`（对象存在但没有数据）必须分开**，")
    W("混用会让前端无法区分「查错了」与「确实没有」：")
    W("")
    W("| 结果码 | 含义 | 典型场景 |")
    W("|---|---|---|")
    W("| `Success` | 成功 | 正常返回 |")
    W("| `InvalidParameter` | 参数非法 | 必填缺失、类型不符、跨字段矛盾 |")
    W("| `NotFound` | 对象不存在 | 按 id 查不到单据 |")
    W("| `NoData` | 无数据 | 查询成立但结果为空（如无可入库明细） |")
    W("| `NoPermission` | 无权限 | 缺权限点，或数据范围不含该对象 |")
    W("| `Ambiguous` | 存在歧义 | 实体归一命中多个候选，需用户确认 |")
    W("| `Unsupported` | 不支持 | 落到意图白名单之外的问题 |")
    W("")
    W("### 10.5 唯一键与逻辑删除的配合")
    W("")
    W("唯一键都不含 `is_deleted`，于是「删了再建同名」会撞键。三种情形三种处理，")
    W("这是本项目最容易踩的坑之一：")
    W("")
    W("| 情形 | 处理 |")
    W("|---|---|")
    W("| 主数据（物料、供应商） | 查重时**连带已删记录**一起查，提示改用停用而非删除 |")
    W("| 关联子表（供货范围、联系用） | **物理删除后重建**，不留逻辑删除的壳 |")
    W("| 无唯一键的从表 | 从表走逻辑删除即可 |")
    W("")
    W("另需注意：全局配置了 `logic-delete-field` 之后，MyBatis-Plus 的 `delete` 对任何含")
    W("`is_deleted` 的实体都会降级成 UPDATE，物理删除必须显式写原生 `@Delete`。")
    W("")
    W("---")
    W("")
    W("## 十一、生成与校验")
    W("")
    W("```bash")
    W("# 重新生成（表结构变更后跑一次）")
    W("python scripts/gen_data_dict.py")
    W("")
    W("# 只做覆盖性校验，不写文件（可进 CI）")
    W("python scripts/gen_data_dict.py --check")
    W("```")
    W("")
    W("校验会检查四件事：库里的表是否全部归域、归域的表是否都在库里、每张表是否都有用途说明、")
    W("字段数是否与表清单吻合。任一项不通过即退出码 1。")
    W("")

    with io.open(MD_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L))

    # ---------------------------------------------------------- CSV
    os.makedirs(os.path.dirname(CSV_OUT), exist_ok=True)
    with io.open(CSV_OUT, "w", encoding="utf-8-sig", newline="") as f:
        f.write("域,表名,表中文名,功能点,字段序号,字段,类型,允许空,默认值,键,说明\n")
        for code, name, script, ts in DOMAINS:
            for t in ts:
                cn, fp = short_comment(tbls.get(t, ""))
                for k, r in enumerate(by_table.get(t, []), 1):
                    _, colname, ctype, nullable, dflt, key, extra, comment = r[:8]
                    cells = [code, t, cn, fp, str(k), colname, ctype,
                             "是" if nullable == "YES" else "否",
                             dflt if dflt not in ("", "null") else "",
                             KEY_LABEL.get(key, key), comment]
                    f.write(",".join('"%s"' % c.replace('"', '""') for c in cells) + "\n")

    print("已生成：%s（%d 行）" % (MD_OUT, len(L)))
    print("已生成：%s" % CSV_OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
