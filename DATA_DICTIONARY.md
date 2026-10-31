# 采购管理系统 数据字典

> 本文件由 `scripts/gen_data_dict.py` 从 `information_schema` 直接读取生成，
> 不属于手写文档——表结构改了重跑一次即可，避免文档与实现脱节。
> 生成时间：2026-10-31　库：`procurement_db`　规模：49 张表 / 718 个字段 / 当前 5498 行数据

阅读约定：

- **键**列：`PK` 主键、`UK` 唯一键组成部分、`IDX` 普通索引列。
- **空**列：`是` 允许 NULL，`否` 非空。
- **默认**列：`—` 表示无默认值。
- **说明**列取自列的 COMMENT，枚举字段的取值含义直接写在里面（如「状态：1 生效、2 停用」）。
- 行数是当前库的实际值，反映的是**演示数据规模**，不等于设计容量。
- 通用字段是**按需裁剪**而非全表照抄：`created_at` 与 `is_deleted` 49 张表全有，
  `updated_at` 44 张、`version` 43 张、`created_by` 37 张、`updated_by` 10 张，
  缺少的那几张是只增不改的表（消息、审计、操作日志、拒答记录），见 10.1 说明。

---

## 一、总览

数据库共 49 张表，按业务内聚划分为八个域，每域由一支建表脚本建立：

| 域 | 名称 | 建表脚本 | 表数 | 字段数 | 内容 |
|---|---|---|---|---|---|
| A | 基础数据域 | `db/01_schema_a_base.sql` | 13 | 162 | 物料、分类、扩展属性、供应商（主表/联系人/资质/供货范围）、仓库、库位、仓库使用人、字典、归属对象 |
| B | 采购单据域 | `db/02_schema_b_bill.sql` | 10 | 202 | 采购申请（主表/明细）、采购订单（主表/明细）、变更历史、交付登记、到货验收（主表/明细）、入库（主表/明细） |
| C | 库存域 | `db/03_schema_c_stock.sql` | 2 | 36 | 库存余额、库存变动流水 |
| D | 审批域 | `db/04_schema_d_approval.sql` | 2 | 33 | 审批流程节点配置、审批任务 |
| E | 价格与参数域 | `db/05_schema_e_price.sql` | 3 | 42 | 价格参考、成交价统计快照、系统参数 |
| F | 平台权限域 | `db/06_schema_f_platform.sql` | 8 | 101 | 用户、角色、权限点、两张关联表、数据范围、操作日志、登录会话 |
| G | 知识库域 | `db/07_schema_g_kb.sql` | 5 | 64 | 知识文档、切块、稀疏倒排索引、拒答记录、检索测试 |
| H | 智能助手域 | `db/08_schema_h_ai.sql` | 6 | 78 | 会话、消息、意图白名单、意图参数规格、实体别名、查询审计 |
| — | **合计** | — | **49** | **718** | — |

表清单（按域内业务顺序，含功能点编号与当前行数）：

| # | 表名 | 中文名 | 功能点 | 字段数 | 行数 |
|---:|---|---|---|---:|---:|
| 1 | `material_category` | 物料分类 | M1-1-7 | 13 | 4 |
| 2 | `material` | 物料主表 | M1-1 | 16 | 8 |
| 3 | `material_ext_attr` | 物料扩展属性 | M1-1-6 | 10 | 3 |
| 4 | `supplier` | 供应商主表 | M1-2 | 17 | 4 |
| 5 | `supplier_contact` | 供应商联系人 | M1-2-4 | 14 | 4 |
| 6 | `supplier_attachment` | 供应商资质附件 | M1-2-5 | 13 | 0 |
| 7 | `supplier_scope` | 供应商供货范围 | M1-2-6 | 11 | 7 |
| 8 | `warehouse` | 仓库档案 | M1-3-1 | 13 | 2 |
| 9 | `warehouse_location` | 库位 | M1-3-2 | 11 | 4 |
| 10 | `warehouse_user` | 仓库使用人关系 | M1-3-3 | 9 | 2 |
| 11 | `dict_type` | 字典类型 | M1-4-1 | 10 | 18 |
| 12 | `dict_item` | 字典项 | M1-4-1 | 14 | 69 |
| 13 | `owner_object` | 归属对象 | M1/M2-2-5 | 11 | 3 |
| 14 | `purchase_request` | 采购申请单 | M2 | 17 | 3 |
| 15 | `purchase_request_item` | 采购申请明细行 | M2-2 | 21 | 5 |
| 16 | `purchase_order` | 采购订单 | M3 | 27 | 9 |
| 17 | `purchase_order_item` | 采购订单明细行 | M3-2 | 26 | 16 |
| 18 | `order_change_log` | 订单变更历史 | M3-4 | 16 | 0 |
| 19 | `delivery_progress` | 交付进度登记 | M3-5-1 | 16 | 2 |
| 20 | `arrival_inspection` | 到货验收单 | M4 | 22 | 1 |
| 21 | `arrival_inspection_item` | 到货验收明细 | M4-2 | 18 | 1 |
| 22 | `inbound_order` | 采购入库单 | M5-1 | 22 | 3 |
| 23 | `inbound_item` | 采购入库明细 | M5-1/M5-2 | 17 | 10 |
| 24 | `stock_balance` | 库存余额 | M5-3 | 15 | 10 |
| 25 | `stock_flow` | 库存变动流水 | M5-4 | 21 | 10 |
| 26 | `approval_flow_node` | 审批流程节点配置 | M11-5 | 16 | 4 |
| 27 | `approval_task` | 审批任务 | M11-5 | 17 | 6 |
| 28 | `price_reference` | 物料价格参考 | M6-1-1 | 14 | 4 |
| 29 | `price_stat_snapshot` | 成交价统计快照 | M6-1-2/M6-1-3 | 14 | 4 |
| 30 | `sys_param` | 系统参数 | M6/M11-4 | 14 | 17 |
| 31 | `sys_user` | 用户 | M11 | 19 | 7 |
| 32 | `sys_role` | 角色 | M11-2-5 | 13 | 5 |
| 33 | `sys_permission` | 权限点 | M11-3-1 | 12 | 156 |
| 34 | `sys_role_permission` | 角色权限关联 | M11-3-2 | 8 | 246 |
| 35 | `sys_user_role` | 用户角色关联 | M11-2-6 | 8 | 7 |
| 36 | `sys_data_scope` | 数据范围授权 | M11-3-3 | 12 | 10 |
| 37 | `sys_operation_log` | 操作日志 | M11-4-4 | 14 | 3199 |
| 38 | `sys_session` | 会话 | M11-1 | 15 | 822 |
| 39 | `kb_document` | 知识文档 | M10-1 | 20 | 2 |
| 40 | `kb_chunk` | 文档切块 | M10-2 | 14 | 10 |
| 41 | `kb_sparse_index` | 稀疏检索倒排索引 | M10-3-2 | 8 | 610 |
| 42 | `kb_reject_log` | 拒答记录 | M8-5-3 | 9 | 0 |
| 43 | `kb_retrieval_test` | 检索测试记录 | M10-5 | 13 | 0 |
| 44 | `ai_session` | 智能会话 | M7-1-2 | 11 | 36 |
| 45 | `ai_message` | 会话消息 | M7-4 | 13 | 78 |
| 46 | `ai_intent` | 意图白名单 | M9-1-3 | 12 | 5 |
| 47 | `ai_intent_param` | 意图参数规格 | M9-2-4 | 14 | 26 |
| 48 | `ai_entity_alias` | 实体别名映射 | M9-3-1 | 13 | 7 |
| 49 | `ai_query_audit` | 智能查询审计 | M9-5-5 | 15 | 39 |

---

## 二、基础数据域（13 张表 / 162 字段）

建表脚本 `db/01_schema_a_base.sql`。

### material_category　物料分类（M1-1-7）

物料分类树。parent_id 自引用成树，用 level 与 sort_no 控制层级与排序；分类是物料的归类维度，也是报表按分类汇总的键。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `category_no` | `varchar(50)` | 否 | — | UK | 分类编码，全系统唯一 |
| 3 | `category_name` | `varchar(100)` | 否 | — |  | 分类名称 |
| 4 | `parent_id` | `bigint(20)` | 否 | `0` | IDX | 上级分类id，0 表示根节点 |
| 5 | `level_no` | `tinyint(4)` | 否 | `1` |  | 层级序号，从 1 开始 |
| 6 | `sort_no` | `int(11)` | 否 | `0` |  | 同级排序号，升序 |
| 7 | `status` | `tinyint(4)` | 否 | `1` | IDX | 启用状态：1 启用、2 停用 |
| 8 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_category_no`（category_no）
- 普通 `idx_category_parent`（parent_id）
- 普通 `idx_category_status`（status）

### material　物料主表（M1-1）

物料主数据。物料编号全局唯一，挂分类、计量单位与安全库存；安全库存是 M5 预警与 M12 库存不足图的判定依据。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_no` | `varchar(50)` | 否 | — | UK | 物料编码，全系统唯一，逻辑删除后不复用 |
| 3 | `material_name` | `varchar(100)` | 否 | — | IDX | 物料名称 |
| 4 | `category_id` | `bigint(20)` | 否 | — | IDX | 物料分类，关联 material_category.id |
| 5 | `spec` | `varchar(200)` | 是 | — |  | 规格型号 |
| 6 | `brand` | `varchar(100)` | 是 | — |  | 品牌 |
| 7 | `unit_id` | `bigint(20)` | 否 | — |  | 计量单位，关联 dict_item.id（字典类型 unit） |
| 8 | `material_type` | `tinyint(4)` | 否 | `1` |  | 物料类型：1 原材料、2 辅料、3 外购件、4 备件 |
| 9 | `safety_stock` | `decimal(18,4)` | 否 | `0.0000` |  | 预警下限，M5-5-1 库存不足判定基准 |
| 10 | `status` | `tinyint(4)` | 否 | `1` | IDX | 启用状态：1 启用、2 停用；停用后不可在新单据选择（M1-1-4） |
| 11 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 14 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 15 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 16 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_material_no`（material_no）
- 普通 `idx_material_category`（category_id）
- 普通 `idx_material_name`（material_name）
- 普通 `idx_material_status`（status）

### material_ext_attr　物料扩展属性（M1-1-6）

物料扩展属性。用 attr_key/attr_value 键值对承载非通用属性，避免为个别物料的特殊字段反复改主表。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 3 | `attr_name` | `varchar(50)` | 否 | — |  | 属性名 |
| 4 | `attr_value` | `varchar(500)` | 是 | — |  | 属性值 |
| 5 | `sort_no` | `int(11)` | 否 | `0` |  | 排序号 |
| 6 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 7 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 8 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 9 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 10 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_ext_material_attr`（material_id,attr_name）
- 普通 `idx_ext_material`（material_id）

### supplier　供应商主表（M1-2）

供应商主数据。供应商编号唯一，状态区分启用与停用；联系人与资质拆到子表，供货范围单独成表。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `supplier_no` | `varchar(50)` | 否 | — | UK | 供应商编码，全系统唯一 |
| 3 | `supplier_name` | `varchar(100)` | 否 | — | IDX | 供应商名称 |
| 4 | `supplier_type` | `tinyint(4)` | 否 | `2` | IDX | 供应商类型：1 生产厂家、2 经销商、3 服务商 |
| 5 | `tax_no` | `varchar(50)` | 是 | — |  | 统一社会信用代码 |
| 6 | `address` | `varchar(200)` | 是 | — |  | 注册地址 |
| 7 | `phone` | `varchar(30)` | 是 | — |  | 联系电话 |
| 8 | `email` | `varchar(100)` | 是 | — |  | 电子邮箱 |
| 9 | `bank_name` | `varchar(100)` | 是 | — |  | 开户银行 |
| 10 | `bank_account` | `varchar(50)` | 是 | — |  | 银行账号 |
| 11 | `status` | `tinyint(4)` | 否 | `1` | IDX | 启用状态：1 启用、2 停用；停用后不可用于新订单（M1-2-3） |
| 12 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 13 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 14 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 15 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 16 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 17 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_supplier_no`（supplier_no）
- 普通 `idx_supplier_name`（supplier_name）
- 普通 `idx_supplier_status`（status）
- 普通 `idx_supplier_type`（supplier_type）

### supplier_contact　供应商联系人（M1-2-4）

供应商联系人。一家供应商可有多位联系人，用 is_primary 标记主联系人。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `supplier_id` | `bigint(20)` | 否 | — | IDX | 供应商id |
| 3 | `contact_name` | `varchar(50)` | 否 | — |  | 联系人姓名 |
| 4 | `position` | `varchar(50)` | 是 | — |  | 职务 |
| 5 | `phone` | `varchar(30)` | 是 | — |  | 联系电话 |
| 6 | `email` | `varchar(100)` | 是 | — |  | 电子邮箱 |
| 7 | `is_default` | `tinyint(4)` | 否 | `0` |  | 是否主联系人：0 否、1 是 |
| 8 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 9 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 10 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 11 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 12 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_contact_default`（supplier_id,is_default）
- 普通 `idx_contact_supplier`（supplier_id）

### supplier_attachment　供应商资质附件（M1-2-5）

供应商资质附件。登记资质类型与有效期，供准入审核取证。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `supplier_id` | `bigint(20)` | 否 | — | IDX | 供应商id |
| 3 | `file_name` | `varchar(200)` | 否 | — |  | 文件原始名称 |
| 4 | `file_type` | `varchar(20)` | 否 | — |  | 文件类型：pdf、jpg、png、docx 等 |
| 5 | `file_size` | `bigint(20)` | 否 | `0` |  | 文件大小，单位字节 |
| 6 | `file_path` | `varchar(300)` | 否 | — |  | 存储路径，相对路径，不含主机信息 |
| 7 | `expire_date` | `date` | 是 | — |  | 资质有效期，到期由前端提示 |
| 8 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 上传人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_attach_supplier`（supplier_id）

### supplier_scope　供应商供货范围（M1-2-6）

供应商供货范围。声明该供应商能供哪些物料（或整类），下单与申请时是**硬校验**——范围外的物料直接拒绝。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `supplier_id` | `bigint(20)` | 否 | — | IDX | 供应商id |
| 3 | `scope_type` | `tinyint(4)` | 否 | — | IDX | 范围类型：1 按物料、2 按物料分类 |
| 4 | `scope_id` | `bigint(20)` | 否 | — |  | 范围对象id，scope_type=1 时为 material.id，=2 时为 material_category.id |
| 5 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 6 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 7 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 8 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 9 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 10 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 11 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_scope`（supplier_id,scope_type,scope_id）
- 普通 `idx_scope_target`（scope_type,scope_id）

### warehouse　仓库档案（M1-3-1）

仓库档案。库存的物理维度，也是仓管员数据范围的取值来源。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `warehouse_no` | `varchar(50)` | 否 | — | UK | 仓库编码，全系统唯一 |
| 3 | `warehouse_name` | `varchar(100)` | 否 | — |  | 仓库名称 |
| 4 | `manager_id` | `bigint(20)` | 是 | — |  | 仓库负责人，关联 sys_user.id |
| 5 | `address` | `varchar(200)` | 是 | — |  | 仓库地址 |
| 6 | `phone` | `varchar(30)` | 是 | — |  | 联系电话 |
| 7 | `status` | `tinyint(4)` | 否 | `1` | IDX | 启用状态：1 启用、2 停用 |
| 8 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_warehouse_no`（warehouse_no）
- 普通 `idx_warehouse_status`（status）

### warehouse_location　库位（M1-3-2）

库位。仓库下的具体存放位置，入库确认前必须逐行补登。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 所属仓库id |
| 3 | `location_no` | `varchar(50)` | 否 | — |  | 库位编码，仓库内唯一 |
| 4 | `location_name` | `varchar(100)` | 是 | — |  | 库位名称 |
| 5 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 6 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 7 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 8 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 9 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 10 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 11 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_location_no`（warehouse_id,location_no）
- 普通 `idx_location_warehouse`（warehouse_id）

### warehouse_user　仓库使用人关系（M1-3-3）

仓库使用人关系。记录仓管员分管哪些仓库，是 sys_data_scope 之外更细的仓库归属。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 仓库id |
| 3 | `user_id` | `bigint(20)` | 否 | — | IDX | 用户id |
| 4 | `is_default` | `tinyint(4)` | 否 | `0` |  | 是否默认仓库：0 否、1 是 |
| 5 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 6 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 7 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 8 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 9 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_warehouse_user`（warehouse_id,user_id）
- 普通 `idx_wu_user`（user_id）

### dict_type　字典类型（M1-4-1）

字典类型。统一管理性别、单位、部门、物料类型等下拉数据的数据源。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `type_code` | `varchar(50)` | 否 | — | UK | 字典类型编码，全局唯一，如 unit、dept、settlement_type |
| 3 | `type_name` | `varchar(100)` | 否 | — |  | 字典类型名称 |
| 4 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 5 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 6 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 7 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 8 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 9 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 10 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_dict_type_code`（type_code）

### dict_item　字典项（M1-4-1）

字典项。注意：**部门字典（type=dept）的项 id 就是部门 id**，不是项编码，跨表关联时按 id 走。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `type_id` | `bigint(20)` | 否 | — | IDX | 字典类型id |
| 3 | `item_code` | `varchar(50)` | 否 | — |  | 字典项编码，同类型内唯一 |
| 4 | `item_name` | `varchar(100)` | 否 | — |  | 字典项名称 |
| 5 | `parent_id` | `bigint(20)` | 否 | `0` | IDX | 上级字典项id，0 表示根节点，部门树使用 |
| 6 | `sort_no` | `int(11)` | 否 | `0` |  | 排序号，升序 |
| 7 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 8 | `ext_value` | `varchar(200)` | 是 | — |  | 扩展值，如单据编号前缀 |
| 9 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 10 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 11 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 12 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_dict_item_code`（type_id,item_code）
- 普通 `idx_dict_item_parent`（parent_id）
- 普通 `idx_dict_item_type`（type_id,sort_no）

### owner_object　归属对象（M1/M2-2-5）

归属对象。区分公共库存与项目/工单专用库存。全系统统一走 owner_id=1 / owner_no='PUBLIC' 的公共库存行，不在各处硬编码 0。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `owner_type` | `tinyint(4)` | 否 | — | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 3 | `owner_no` | `varchar(50)` | 否 | — |  | 归属对象编号，如项目号、工单号 |
| 4 | `owner_name` | `varchar(100)` | 否 | — |  | 归属对象名称 |
| 5 | `status` | `tinyint(4)` | 否 | `1` |  | 启用状态：1 启用、2 停用 |
| 6 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 7 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 8 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 9 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 10 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 11 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_owner`（owner_type,owner_no）
- 普通 `idx_owner_type`（owner_type）

---

## 三、采购单据域（10 张表 / 202 字段）

建表脚本 `db/02_schema_b_bill.sql`。

### purchase_request　采购申请单（M2）

采购申请单主表。三状态（生命周期/审核/无履约）中的两个：lifecycle_status 与 audit_status；驳回时生命周期回落草稿，由 audit_status=3 表达驳回。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `request_no` | `varchar(50)` | 否 | — | UK | 申请单号，全系统唯一，按编号规则生成 |
| 3 | `request_date` | `date` | 否 | — | IDX | 申请日期 |
| 4 | `dept_id` | `bigint(20)` | 否 | — | IDX | 申请部门，关联 dict_item.id（字典类型 dept） |
| 5 | `applicant_id` | `bigint(20)` | 否 | — | IDX | 申请人，关联 sys_user.id |
| 6 | `require_date` | `date` | 是 | — |  | 需求日期，默认取明细行最早需求日期 |
| 7 | `total_amount` | `decimal(18,2)` | 否 | `0.00` |  | 预估金额合计，明细行 est_amount 汇总 |
| 8 | `lifecycle_status` | `tinyint(4)` | 否 | `1` | IDX | 单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 |
| 9 | `audit_status` | `tinyint(4)` | 否 | `1` | IDX | 审批结果状态：1 未审核、2 通过、3 驳回 |
| 10 | `close_reason` | `varchar(200)` | 是 | — |  | 关闭原因（M2-3-5 整单关闭） |
| 11 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 14 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 15 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 16 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 17 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_request_no`（request_no）
- 普通 `idx_request_applicant`（applicant_id）
- 普通 `idx_request_audit`（audit_status）
- 普通 `idx_request_date`（request_date）
- 普通 `idx_request_dept_date`（dept_id,request_date）
- 普通 `idx_request_lifecycle`（lifecycle_status）

### purchase_request_item　采购申请明细行（M2-2）

采购申请明细行。item_status 表达转单进度（待转/部分已转/已转完/已退回/已关闭），transferred_qty 记录已转数量。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `request_id` | `bigint(20)` | 否 | — | IDX | 主单id |
| 3 | `item_no` | `smallint(6)` | 否 | — |  | 行号，单内唯一 |
| 4 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 5 | `quantity` | `decimal(18,4)` | 否 | — |  | 申请数量 |
| 6 | `unit_id` | `bigint(20)` | 否 | — |  | 计量单位，关联 dict_item.id |
| 7 | `est_price` | `decimal(18,4)` | 是 | — |  | 预估单价，可空；取参考价或人工填写，仅用于预估金额 |
| 8 | `est_amount` | `decimal(18,2)` | 否 | `0.00` |  | 预估金额，由数量乘预估单价得出，服务层计算 |
| 9 | `suggest_supplier_id` | `bigint(20)` | 是 | — |  | 建议供应商，可空（M2-2-3） |
| 10 | `require_date` | `date` | 否 | — |  | 行需求日期 |
| 11 | `owner_type` | `tinyint(4)` | 否 | `1` | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 12 | `owner_id` | `bigint(20)` | 是 | — |  | 归属对象id，owner_type=1 时可空 |
| 13 | `owner_no` | `varchar(50)` | 是 | — |  | 归属对象编号，冗余，便于不联表查询 |
| 14 | `transferred_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 已转订单数量，由转订单动作回写（M2-4-3） |
| 15 | `item_status` | `tinyint(4)` | 否 | `1` |  | 行状态：1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭 |
| 16 | `remark` | `varchar(300)` | 是 | — |  | 行备注 |
| 17 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 18 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 19 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 20 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 21 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_request_item_no`（request_id,item_no）
- 普通 `idx_ri_material`（material_id）
- 普通 `idx_ri_owner`（owner_type,owner_id）
- 普通 `idx_ri_request`（request_id）

### purchase_order　采购订单（M3）

采购订单主表。source_request_id 记录来源申请，构成单据链追溯的一环；lifecycle/audit/fulfill 三状态正交，互不推导。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `order_no` | `varchar(50)` | 否 | — | UK | 订单号，全系统唯一，按编号规则生成 |
| 3 | `source_request_id` | `bigint(20)` | 是 | — | IDX | 来源申请单id，手工建单时为空 |
| 4 | `source_request_no` | `varchar(50)` | 是 | — |  | 来源申请单号，冗余 |
| 5 | `supplier_id` | `bigint(20)` | 否 | — | IDX | 供应商id |
| 6 | `buyer_id` | `bigint(20)` | 否 | — | IDX | 采购员，关联 sys_user.id，数据范围按此人与部门过滤 |
| 7 | `dept_id` | `bigint(20)` | 否 | — | IDX | 采购部门，关联 dict_item.id，数据范围按部门过滤 |
| 8 | `order_date` | `date` | 否 | — | IDX | 下单日期 |
| 9 | `delivery_date` | `date` | 是 | — | IDX | 整单交货期，明细行未设置时取此值 |
| 10 | `delivery_place` | `varchar(200)` | 是 | — |  | 整单交货地点 |
| 11 | `settlement_type` | `tinyint(4)` | 否 | `1` |  | 结算方式：1 月结、2 货到付款、3 预付款 |
| 12 | `lifecycle_status` | `tinyint(4)` | 否 | `1` | IDX | 单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 |
| 13 | `audit_status` | `tinyint(4)` | 否 | `1` | IDX | 审批结果状态：1 未审核、2 通过、3 驳回 |
| 14 | `fulfill_status` | `tinyint(4)` | 否 | `1` | IDX | 业务履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭；由明细行重算，不由人工选择 |
| 15 | `total_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 订单数量合计，服务层汇总 |
| 16 | `total_amount` | `decimal(18,2)` | 否 | `0.00` |  | 含税金额合计，服务层汇总 |
| 17 | `no_tax_amount` | `decimal(18,2)` | 否 | `0.00` |  | 不含税金额合计，服务层汇总 |
| 18 | `tax_amount` | `decimal(18,2)` | 否 | `0.00` |  | 税额合计，服务层汇总 |
| 19 | `deviation_flag` | `tinyint(4)` | 否 | `0` |  | 整单偏离标记：0 无、1 关注、2 显著，取明细行最高等级 |
| 20 | `close_reason` | `varchar(200)` | 是 | — |  | 关闭原因（M3-3-6） |
| 21 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 22 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 23 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 24 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 25 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 26 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 27 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_order_no`（order_no）
- 普通 `idx_order_audit`（audit_status）
- 普通 `idx_order_buyer`（buyer_id）
- 普通 `idx_order_date`（order_date）
- 普通 `idx_order_delivery`（delivery_date）
- 普通 `idx_order_dept`（dept_id）
- 普通 `idx_order_fulfill`（fulfill_status）
- 普通 `idx_order_lifecycle`（lifecycle_status）
- 普通 `idx_order_source`（source_request_id）
- 普通 `idx_order_supplier`（supplier_id）

### purchase_order_item　采购订单明细行（M3-2）

采购订单明细行。delivered_qty 是已交付量（只加合格量），item_fulfill_status 是行级履约；deviation_rate/level 存价格偏离结果。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `order_id` | `bigint(20)` | 否 | — | IDX | 主单id |
| 3 | `item_no` | `smallint(6)` | 否 | — |  | 行号，单内唯一 |
| 4 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 5 | `source_request_item_id` | `bigint(20)` | 是 | — | IDX | 来源申请明细行id，支撑按行转订单的多对多追溯（M2-4-1） |
| 6 | `quantity` | `decimal(18,4)` | 否 | — |  | 订单数量 |
| 7 | `unit_id` | `bigint(20)` | 否 | — |  | 计量单位 |
| 8 | `unit_price` | `decimal(18,4)` | 否 | — |  | 含税单价 |
| 9 | `tax_rate` | `decimal(6,4)` | 否 | `0.1300` |  | 税率，如 0.1300 表示 13% |
| 10 | `amount` | `decimal(18,2)` | 否 | `0.00` |  | 含税金额，数量乘单价，服务层计算 |
| 11 | `no_tax_amount` | `decimal(18,2)` | 否 | `0.00` |  | 不含税金额，含税金额除以一加税率，服务层计算 |
| 12 | `delivery_date` | `date` | 是 | — | IDX | 行交货期，默认取主表 |
| 13 | `delivery_place` | `varchar(200)` | 是 | — |  | 行交货地点 |
| 14 | `delivered_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 已交付数量，即已入库数量，仅由入库单确认回写 |
| 15 | `owner_type` | `tinyint(4)` | 否 | `1` | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 16 | `owner_id` | `bigint(20)` | 是 | — |  | 归属对象id |
| 17 | `owner_no` | `varchar(50)` | 是 | — |  | 归属对象编号，冗余 |
| 18 | `item_fulfill_status` | `tinyint(4)` | 否 | `1` |  | 行履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭 |
| 19 | `deviation_rate` | `decimal(9,4)` | 是 | — |  | 偏离率，(单价 − 参考价) / 参考价，服务层计算，不落前端 |
| 20 | `deviation_level` | `tinyint(4)` | 否 | `0` |  | 偏离等级：0 正常、1 关注、2 显著 |
| 21 | `remark` | `varchar(300)` | 是 | — |  | 行备注 |
| 22 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 23 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 24 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 25 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 26 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_order_item_no`（order_id,item_no）
- 普通 `idx_oi_delivery`（delivery_date,item_fulfill_status）
- 普通 `idx_oi_material`（material_id）
- 普通 `idx_oi_order`（order_id）
- 普通 `idx_oi_owner`（owner_type,owner_id）
- 普通 `idx_oi_source`（source_request_item_id）

### order_change_log　订单变更历史（M3-4）

订单变更历史。反审、作废、改价等动作逐条留痕，按自增 id 判先后——秒级时间戳分不清同秒的多次操作。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `order_id` | `bigint(20)` | 否 | — | IDX | 订单id |
| 3 | `order_no` | `varchar(50)` | 否 | — |  | 订单号，冗余 |
| 4 | `order_item_id` | `bigint(20)` | 是 | — | IDX | 订单明细行id，整单变更时为空 |
| 5 | `item_no` | `smallint(6)` | 是 | — |  | 行号，冗余 |
| 6 | `change_field` | `varchar(50)` | 否 | — |  | 变更字段，如 quantity、unit_price、delivery_date |
| 7 | `before_value` | `varchar(200)` | 是 | — |  | 变更前值 |
| 8 | `after_value` | `varchar(200)` | 是 | — |  | 变更后值 |
| 9 | `change_reason` | `varchar(500)` | 是 | — |  | 变更原因 |
| 10 | `operator_id` | `bigint(20)` | 否 | — |  | 操作人id |
| 11 | `change_at` | `datetime` | 否 | `current_timestamp()` |  | 变更时间 |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 14 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 15 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 16 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_change_item`（order_item_id）
- 普通 `idx_change_order`（order_id,change_at）

### delivery_progress　交付进度登记（M3-5-1）

交付进度登记。M3-5-1 的分批交付凭据，是订单反审与作废的禁止条件之一。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `order_id` | `bigint(20)` | 否 | — | IDX | 订单id |
| 3 | `order_no` | `varchar(50)` | 否 | — |  | 订单号，冗余 |
| 4 | `order_item_id` | `bigint(20)` | 否 | — | IDX | 订单明细行id |
| 5 | `item_no` | `smallint(6)` | 否 | — |  | 行号，冗余 |
| 6 | `material_id` | `bigint(20)` | 否 | — |  | 物料id，冗余，便于按物料筛选 |
| 7 | `delivery_qty` | `decimal(18,4)` | 否 | — |  | 本次登记交付数量，仅供跟单参考 |
| 8 | `delivery_date` | `date` | 否 | — |  | 本次交付日期 |
| 9 | `batch_no` | `varchar(50)` | 是 | — |  | 供应商批次说明，可选 |
| 10 | `remark` | `varchar(300)` | 是 | — |  | 备注 |
| 11 | `operator_id` | `bigint(20)` | 否 | — |  | 登记人id |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 14 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 15 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 16 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_dp_item`（order_item_id）
- 普通 `idx_dp_order`（order_id,delivery_date）

### arrival_inspection　到货验收单（M4）

到货验收单主表。status 1 草稿 / 2 已验收；inspection_result 由明细重算（全合格→1、有不合格未让步→2、有不合格且让步→3）。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `inspection_no` | `varchar(50)` | 否 | — | UK | 验收单号，全系统唯一 |
| 3 | `order_id` | `bigint(20)` | 否 | — | IDX | 来源订单id |
| 4 | `order_no` | `varchar(50)` | 否 | — | IDX | 来源订单号，冗余 |
| 5 | `supplier_id` | `bigint(20)` | 否 | — | IDX | 供应商id，冗余 |
| 6 | `arrival_date` | `date` | 否 | — | IDX | 到货日期 |
| 7 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 收货仓库id |
| 8 | `inspection_mode` | `tinyint(4)` | 否 | `2` |  | 验收方式：1 免检、2 抽样、3 全检；只登记，不做抽检规则计算（M4-2-1） |
| 9 | `inspection_result` | `tinyint(4)` | 是 | — |  | 验收结果：1 合格、2 不合格、3 让步接收（M4-2-2） |
| 10 | `qualified_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 合格数量合计 |
| 11 | `unqualified_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 不合格数量合计，仅登记，不生成入库单 |
| 12 | `concession_flag` | `tinyint(4)` | 否 | `0` |  | 让步接收标记：0 否、1 是（M4-2-4） |
| 13 | `concession_reason` | `varchar(500)` | 是 | — |  | 让步接收理由，标记为 1 时必填 |
| 14 | `inspector_id` | `bigint(20)` | 否 | — |  | 验收人id |
| 15 | `status` | `tinyint(4)` | 否 | `1` | IDX | 验收单状态：1 草稿、2 已验收（M4-2-5） |
| 16 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 17 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 18 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 19 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 20 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 21 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 22 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_inspection_no`（inspection_no）
- 普通 `idx_ai_arrival`（arrival_date）
- 普通 `idx_ai_order`（order_id）
- 普通 `idx_ai_order_no`（order_no）
- 普通 `idx_ai_status`（status）
- 普通 `idx_ai_supplier`（supplier_id）
- 普通 `idx_ai_warehouse`（warehouse_id）

### arrival_inspection_item　到货验收明细（M4-2）

到货验收明细。守恒约束的落地点：合格量 + 不合格量 = 到货量，登记与提交双校验。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `inspection_id` | `bigint(20)` | 否 | — | IDX | 验收单id |
| 3 | `order_item_id` | `bigint(20)` | 否 | — | IDX | 订单明细行id |
| 4 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id，冗余 |
| 5 | `unit_id` | `bigint(20)` | 否 | — |  | 计量单位，冗余 |
| 6 | `arrival_qty` | `decimal(18,4)` | 否 | — |  | 本次到货数量 |
| 7 | `qualified_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 合格数量 |
| 8 | `unqualified_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 不合格数量 |
| 9 | `batch_no` | `varchar(50)` | 是 | — |  | 预登记批次号（M5-2-1） |
| 10 | `owner_type` | `tinyint(4)` | 否 | `1` |  | 归属类型，继承订单行 |
| 11 | `owner_id` | `bigint(20)` | 是 | — |  | 归属对象id，继承订单行 |
| 12 | `owner_no` | `varchar(50)` | 是 | — |  | 归属对象编号，继承订单行 |
| 13 | `remark` | `varchar(300)` | 是 | — |  | 行备注 |
| 14 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 15 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 16 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 17 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 18 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_aii_inspection_item`（inspection_id,order_item_id）
- 普通 `idx_aii_material`（material_id）
- 普通 `idx_aii_order_item`（order_item_id）

### inbound_order　采购入库单（M5-1）

采购入库单主表。status 1 草稿 / 2 已确认 / 3 已作废；**只有确认动作会写库存**，inspection_id 指回验收单。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `inbound_no` | `varchar(50)` | 否 | — | UK | 入库单号，全系统唯一 |
| 3 | `inspection_id` | `bigint(20)` | 是 | — | IDX | 来源验收单id，手工建单时为空 |
| 4 | `inspection_no` | `varchar(50)` | 是 | — |  | 来源验收单号，冗余 |
| 5 | `order_id` | `bigint(20)` | 是 | — | IDX | 来源订单id |
| 6 | `order_no` | `varchar(50)` | 是 | — |  | 来源订单号，冗余 |
| 7 | `supplier_id` | `bigint(20)` | 是 | — |  | 供应商id，冗余 |
| 8 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 入库仓库id |
| 9 | `inbound_date` | `date` | 否 | — | IDX | 入库日期 |
| 10 | `status` | `tinyint(4)` | 否 | `1` | IDX | 入库单状态：1 草稿、2 已确认、3 已作废 |
| 11 | `confirm_time` | `datetime` | 是 | — |  | 确认时间（M5-1-3） |
| 12 | `confirm_by` | `bigint(20)` | 是 | — |  | 确认人id |
| 13 | `void_time` | `datetime` | 是 | — |  | 作废时间（M5-1-4） |
| 14 | `void_reason` | `varchar(200)` | 是 | — |  | 作废原因 |
| 15 | `operator_id` | `bigint(20)` | 否 | — |  | 制单人id |
| 16 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 17 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 18 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 19 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 20 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 21 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 22 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_inbound_no`（inbound_no）
- 普通 `idx_io_date`（inbound_date）
- 普通 `idx_io_inspection`（inspection_id）
- 普通 `idx_io_order`（order_id）
- 普通 `idx_io_status`（status）
- 普通 `idx_io_warehouse`（warehouse_id）

### inbound_item　采购入库明细（M5-1/M5-2）

采购入库明细。quantity 只含合格量（让步时含被放行的不合格量）；location_id 确认前必须补齐，batch_no 是库存批次号。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `inbound_id` | `bigint(20)` | 否 | — | IDX | 入库单id |
| 3 | `order_item_id` | `bigint(20)` | 是 | — | IDX | 订单明细行id，手工建单时为空 |
| 4 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 5 | `quantity` | `decimal(18,4)` | 否 | — |  | 入库数量，取验收合格数量 |
| 6 | `unit_id` | `bigint(20)` | 否 | — |  | 计量单位 |
| 7 | `batch_no` | `varchar(50)` | 否 | `''` | IDX | 批次号，无批次管理时为空串（M5-2-1） |
| 8 | `location_id` | `bigint(20)` | 是 | — |  | 库位id，必须属于入库单仓库（M5-2-2） |
| 9 | `owner_type` | `tinyint(4)` | 否 | `1` | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 10 | `owner_id` | `bigint(20)` | 是 | — |  | 归属对象id |
| 11 | `owner_no` | `varchar(50)` | 是 | — |  | 归属对象编号 |
| 12 | `remark` | `varchar(300)` | 是 | — |  | 行备注 |
| 13 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 14 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 15 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 16 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 17 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_ii_batch`（batch_no）
- 普通 `idx_ii_inbound`（inbound_id）
- 普通 `idx_ii_material`（material_id）
- 普通 `idx_ii_order_item`（order_item_id）
- 普通 `idx_ii_owner`（owner_type,owner_id）

---

## 四、库存域（2 张表 / 36 字段）

建表脚本 `db/03_schema_c_stock.sql`。

### stock_balance　库存余额（M5-3）

库存余额。唯一键 = 物料 + 仓库 + 批次 + 归属，是库存的当前状态；**可用量刻意不落库**（数量 − 冻结 − 预留现算），避免派生值失同步。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 3 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 仓库id |
| 4 | `batch_no` | `varchar(50)` | 否 | `''` |  | 批次号，无批次管理时为空串 |
| 5 | `owner_type` | `tinyint(4)` | 否 | `1` | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 6 | `owner_id` | `bigint(20)` | 否 | `0` |  | 归属对象id，公共库存固定 0 |
| 7 | `owner_no` | `varchar(50)` | 否 | `'PUBLIC'` |  | 归属对象编号，公共库存固定 PUBLIC |
| 8 | `quantity` | `decimal(18,4)` | 否 | `0.0000` |  | 库存数量，唯一由入库单确认维护 |
| 9 | `frozen_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 冻结量 |
| 10 | `reserved_qty` | `decimal(18,4)` | 否 | `0.0000` |  | 预留量 |
| 11 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 12 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号，并发入库冲突时重试（M5-5-4） |
| 13 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 14 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 15 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_stock_balance`（material_id,warehouse_id,batch_no,owner_type,owner_id）
- 普通 `idx_sb_material`（material_id）
- 普通 `idx_sb_owner`（owner_type,owner_id）
- 普通 `idx_sb_warehouse`（warehouse_id）

### stock_flow　库存变动流水（M5-4）

库存变动流水。记录 before_qty → after_qty 与来源单据，是「余额 = 流水累计」对账的依据；流水只增不删。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 3 | `warehouse_id` | `bigint(20)` | 否 | — | IDX | 仓库id |
| 4 | `batch_no` | `varchar(50)` | 否 | `''` | IDX | 批次号 |
| 5 | `owner_type` | `tinyint(4)` | 否 | `1` | IDX | 归属类型：1 公共、2 项目、3 工单 |
| 6 | `owner_id` | `bigint(20)` | 否 | `0` |  | 归属对象id，公共库存固定 0 |
| 7 | `owner_no` | `varchar(50)` | 否 | `'PUBLIC'` |  | 归属对象编号 |
| 8 | `change_type` | `tinyint(4)` | 否 | — |  | 变动类型：1 入库增加、2 作废冲减 |
| 9 | `change_qty` | `decimal(18,4)` | 否 | — |  | 变动数量，入库为正、作废冲减为负 |
| 10 | `before_qty` | `decimal(18,4)` | 否 | — |  | 变动前数量 |
| 11 | `after_qty` | `decimal(18,4)` | 否 | — |  | 变动后数量 |
| 12 | `source_type` | `tinyint(4)` | 否 | `1` | IDX | 来源单据类型：1 采购入库单 |
| 13 | `source_id` | `bigint(20)` | 否 | — |  | 来源单据id |
| 14 | `source_no` | `varchar(50)` | 否 | — |  | 来源单据号，冗余 |
| 15 | `operator_id` | `bigint(20)` | 否 | — |  | 操作人id |
| 16 | `flow_time` | `datetime` | 否 | `current_timestamp()` |  | 发生时间 |
| 17 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除；流水不做删除 |
| 18 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 19 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 20 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 21 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_sf_batch`（batch_no）
- 普通 `idx_sf_material_time`（material_id,flow_time）
- 普通 `idx_sf_owner`（owner_type,owner_id）
- 普通 `idx_sf_source`（source_type,source_id）
- 普通 `idx_sf_warehouse_time`（warehouse_id,flow_time）

---

## 五、审批域（2 张表 / 33 字段）

建表脚本 `db/04_schema_d_approval.sql`。

### approval_flow_node　审批流程节点配置（M11-5）

审批流程节点配置。bill_type + node_seq 定义几级审批，sign_type 区分或签（任一通过即推进）与会签（全部通过才推进）。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `bill_type` | `tinyint(4)` | 否 | — | IDX | 单据类型：1 采购申请、2 采购订单 |
| 3 | `node_seq` | `smallint(6)` | 否 | — |  | 节点序号，固定顺序，从 1 开始 |
| 4 | `node_name` | `varchar(50)` | 否 | — |  | 节点名称，如采购主管审批 |
| 5 | `approver_type` | `tinyint(4)` | 否 | — |  | 审批人指定方式：1 按角色、2 按具体用户 |
| 6 | `role_id` | `bigint(20)` | 是 | — | IDX | 角色id，approver_type=1 时必填 |
| 7 | `user_id` | `bigint(20)` | 是 | — | IDX | 用户id，approver_type=2 时必填 |
| 8 | `sign_type` | `tinyint(4)` | 否 | `1` |  | 会签方式：1 或签（任一通过即推进）、2 会签（全部通过才推进，任一驳回则整单驳回） |
| 9 | `status` | `tinyint(4)` | 否 | `1` |  | 配置状态：1 启用、2 停用 |
| 10 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 11 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 12 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 13 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 14 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 15 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 16 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_flow_node`（bill_type,node_seq,approver_type,role_id,user_id）
- 普通 `idx_flow_bill_seq`（bill_type,node_seq,status）
- 普通 `idx_flow_role`（role_id）
- 普通 `idx_flow_user`（user_id）

### approval_task　审批任务（M11-5）

审批任务。每个审批人一条任务，task_status 1 待处理 / 2 通过 / 3 驳回 / 4 失效；审批历史不另建表，按 node_seq 与 id 排序即得。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `task_no` | `varchar(50)` | 否 | — | UK | 任务编号，全系统唯一 |
| 3 | `bill_type` | `tinyint(4)` | 否 | — | IDX | 单据类型：1 采购申请、2 采购订单 |
| 4 | `bill_id` | `bigint(20)` | 否 | — |  | 单据id |
| 5 | `bill_no` | `varchar(50)` | 否 | — | IDX | 单据号，冗余 |
| 6 | `node_seq` | `smallint(6)` | 否 | — |  | 节点序号 |
| 7 | `node_name` | `varchar(50)` | 否 | — |  | 节点名称，冗余，单据归档后仍可读 |
| 8 | `approver_id` | `bigint(20)` | 否 | — | IDX | 审批人id |
| 9 | `sign_type` | `tinyint(4)` | 否 | `1` |  | 会签方式：1 或签、2 会签 |
| 10 | `task_status` | `tinyint(4)` | 否 | `1` | IDX | 任务状态：1 待处理、2 已通过、3 已驳回、4 已失效（撤回或整单驳回导致） |
| 11 | `opinion` | `varchar(500)` | 是 | — |  | 审批意见，驳回时必填 |
| 12 | `handle_time` | `datetime` | 是 | — |  | 处理时间 |
| 13 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 14 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 15 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 16 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 17 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_task_no`（task_no）
- 普通 `idx_task_approver`（approver_id,task_status）
- 普通 `idx_task_bill`（bill_type,bill_id,node_seq）
- 普通 `idx_task_bill_no`（bill_no）
- 普通 `idx_task_status`（task_status）

---

## 六、价格与参数域（3 张表 / 42 字段）

建表脚本 `db/05_schema_e_price.sql`。

### price_reference　物料价格参考（M6-1-1）

物料价格参考。同一物料 price_version 递增成版本链，旧版永久保留；status=1 且 effective_date≤基准日 才算生效。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 3 | `ref_price` | `decimal(18,4)` | 否 | — |  | 参考价，手工设定，不是当前供应商报价 |
| 4 | `effective_date` | `date` | 否 | — |  | 生效日期 |
| 5 | `price_version` | `int(11)` | 否 | `1` |  | 价格版本号，同一物料内递增，支持历史版本 |
| 6 | `source_type` | `tinyint(4)` | 否 | `1` |  | 来源：1 手工设定、2 批量导入 |
| 7 | `status` | `tinyint(4)` | 否 | `1` | IDX | 状态：1 生效、2 停用 |
| 8 | `remark` | `varchar(300)` | 是 | — |  | 备注 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_price_material_version`（material_id,price_version）
- 普通 `idx_price_material_eff`（material_id,effective_date）
- 普通 `idx_price_status`（status）

### price_stat_snapshot　成交价统计快照（M6-1-2/M6-1-3）

成交价统计快照。按「最近 N 笔 / 最近 N 天」窗口预计算，与参考价严格分离——历史成交不等于当前报价。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `material_id` | `bigint(20)` | 否 | — | IDX | 物料id |
| 3 | `supplier_id` | `bigint(20)` | 否 | `0` |  | 供应商id，0 表示全部供应商 |
| 4 | `stat_type` | `tinyint(4)` | 否 | — |  | 统计类型：1 历史采购均价、2 最近一次成交价；3 最高价、4 最低价为一版不做的预留值 |
| 5 | `stat_value` | `decimal(18,4)` | 否 | — |  | 统计值 |
| 6 | `stat_window` | `varchar(50)` | 否 | — |  | 统计窗口，如 LAST_5_DEALS、LAST_180_DAYS |
| 7 | `sample_count` | `int(11)` | 否 | `0` |  | 样本数量 |
| 8 | `source_bill_no` | `varchar(50)` | 是 | — |  | 最近成交价对应的单据号（M6-4-2 溯源） |
| 9 | `calc_time` | `datetime` | 否 | `current_timestamp()` | IDX | 快照计算时间 |
| 10 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 11 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 12 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_snapshot`（material_id,supplier_id,stat_type,stat_window）
- 普通 `idx_snapshot_calc`（calc_time）
- 普通 `idx_snapshot_material`（material_id,stat_type）

### sys_param　系统参数（M6/M11-4）

系统参数。param_group + param_key 唯一，价格偏离阈值、交付统计窗口等口径全部走这里，不散落在代码常量里。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `param_code` | `varchar(80)` | 否 | — | UK | 参数编码，全局唯一 |
| 3 | `param_name` | `varchar(100)` | 否 | — |  | 参数名称 |
| 4 | `param_value` | `varchar(500)` | 是 | — |  | 参数值，统一以字符串存放，按 param_type 解释 |
| 5 | `param_type` | `varchar(20)` | 否 | `'string'` |  | 值类型：string、int、decimal、boolean、json |
| 6 | `param_group` | `varchar(50)` | 否 | `'common'` | IDX | 参数分组：price、stock、bill_no、kb、ai、common |
| 7 | `editable` | `tinyint(4)` | 否 | `1` |  | 是否允许界面修改：0 否、1 是 |
| 8 | `remark` | `varchar(300)` | 是 | — |  | 说明，含口径定义 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_param_code`（param_code）
- 普通 `idx_param_group`（param_group）

---

## 七、平台权限域（8 张表 / 101 字段）

建表脚本 `db/06_schema_f_platform.sql`。

### sys_user　用户（M11）

用户。密码存哈希，状态区分启用停用；用户是权限与数据范围的授权主体。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `username` | `varchar(50)` | 否 | — | UK | 登录账号，全系统唯一 |
| 3 | `password_hash` | `varchar(100)` | 否 | — |  | 密码哈希，BCrypt，不存明文 |
| 4 | `real_name` | `varchar(50)` | 否 | — | IDX | 真实姓名 |
| 5 | `dept_id` | `bigint(20)` | 否 | — | IDX | 所属部门，关联 dict_item.id（字典类型 dept） |
| 6 | `phone` | `varchar(30)` | 是 | — |  | 联系电话 |
| 7 | `email` | `varchar(100)` | 是 | — |  | 电子邮箱 |
| 8 | `status` | `tinyint(4)` | 否 | `1` | IDX | 账号状态：1 启用、2 停用；停用后不可登录（M11-2-3） |
| 9 | `pwd_update_time` | `datetime` | 是 | — |  | 密码最后修改时间（M11-1-4） |
| 10 | `pwd_need_change` | `tinyint(4)` | 否 | `0` |  | 是否要求登录后修改密码：0 否、1 是，管理员重置后置 1（M11-2-4） |
| 11 | `last_login_time` | `datetime` | 是 | — |  | 最后登录时间 |
| 12 | `last_login_ip` | `varchar(50)` | 是 | — |  | 最后登录IP |
| 13 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 14 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 15 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 16 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 17 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 18 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 19 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_username`（username）
- 普通 `idx_user_dept`（dept_id）
- 普通 `idx_user_name`（real_name）
- 普通 `idx_user_status`（status）

### sys_role　角色（M11-2-5）

角色。采购员、采购主管、仓管员、系统管理员四类业务角色 + 基础角色。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `role_code` | `varchar(50)` | 否 | — | UK | 角色编码，全系统唯一，如 buyer、buyer_manager、warehouse_keeper、admin |
| 3 | `role_name` | `varchar(50)` | 否 | — |  | 角色名称 |
| 4 | `data_scope_all` | `tinyint(4)` | 否 | `0` |  | 是否全量数据范围：0 否、1 是；系统管理员为 1 |
| 5 | `is_system` | `tinyint(4)` | 否 | `0` |  | 是否内置角色：0 否、1 是；内置角色不可删除 |
| 6 | `status` | `tinyint(4)` | 否 | `1` |  | 状态：1 启用、2 停用 |
| 7 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 8 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 9 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 10 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 11 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 12 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 13 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_role_code`（role_code）

### sys_permission　权限点（M11-3-1）

权限点。perm_code 采用「模块:对象:动作」三段式（如 m5:inbound:confirm），接口上以 @RequirePermission 声明。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `perm_code` | `varchar(80)` | 否 | — | UK | 权限点编码，全系统唯一，格式 模块:对象:动作 |
| 3 | `perm_name` | `varchar(100)` | 否 | — |  | 权限点名称 |
| 4 | `module_code` | `varchar(20)` | 否 | — | IDX | 所属模块编号，如 M2、M11 |
| 5 | `action_type` | `varchar(20)` | 否 | — | IDX | 动作类型：list、view、create、update、delete、submit、approve、reject、withdraw、close、void、reaudit、export、import、config、query、audit |
| 6 | `sort_no` | `int(11)` | 否 | `0` |  | 排序号，授权界面展示用 |
| 7 | `remark` | `varchar(200)` | 是 | — |  | 说明 |
| 8 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 9 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 10 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 11 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 12 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_perm_code`（perm_code）
- 普通 `idx_perm_action`（action_type）
- 普通 `idx_perm_module`（module_code,sort_no）

### sys_role_permission　角色权限关联（M11-3-2）

角色权限关联。多对多中间表，决定角色能调哪些接口。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `role_id` | `bigint(20)` | 否 | — | IDX | 角色id |
| 3 | `perm_id` | `bigint(20)` | 否 | — | IDX | 权限点id |
| 4 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 5 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 6 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 7 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 8 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_role_perm`（role_id,perm_id）
- 普通 `idx_rp_perm`（perm_id）

### sys_user_role　用户角色关联（M11-2-6）

用户角色关联。多对多中间表，一个用户可兼多个角色，权限取并集。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `user_id` | `bigint(20)` | 否 | — | IDX | 用户id |
| 3 | `role_id` | `bigint(20)` | 否 | — | IDX | 角色id |
| 4 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 5 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 6 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 7 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 8 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_user_role`（user_id,role_id）
- 普通 `idx_ur_role`（role_id）

### sys_data_scope　数据范围授权（M11-3-3）

数据范围授权。按仓库/部门/归属对象三个维度限制可见数据；subject_type 区分授给用户还是角色。缺部门范围时**失败关闭**，缺仓库范围则不过滤。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `subject_type` | `tinyint(4)` | 否 | — | IDX | 授权主体：1 用户、2 角色 |
| 3 | `subject_id` | `bigint(20)` | 否 | — |  | 主体id |
| 4 | `scope_dimension` | `tinyint(4)` | 否 | — | IDX | 范围维度：1 仓库、2 部门、3 归属对象 |
| 5 | `scope_value` | `varchar(100)` | 否 | — |  | 范围值：维度为仓库存 warehouse.id，为部门存 dict_item.id，为归属对象存 owner_no |
| 6 | `remark` | `varchar(200)` | 是 | — |  | 备注 |
| 7 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 8 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 9 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 10 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 11 | `updated_by` | `bigint(20)` | 是 | — |  | 最后修改人id |
| 12 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_data_scope`（subject_type,subject_id,scope_dimension,scope_value）
- 普通 `idx_scope_dimension`（scope_dimension）
- 普通 `idx_scope_subject`（subject_type,subject_id）

### sys_operation_log　操作日志（M11-4-4）

操作日志。记录谁在何时对什么做了什么；全库唯一不做清理的表，审计追溯靠它。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `user_id` | `bigint(20)` | 是 | — | IDX | 操作人id |
| 3 | `username` | `varchar(50)` | 是 | — |  | 操作人账号，冗余 |
| 4 | `real_name` | `varchar(50)` | 是 | — |  | 操作人姓名，冗余 |
| 5 | `module_code` | `varchar(20)` | 否 | — | IDX | 模块编号，如 M2 |
| 6 | `action` | `varchar(50)` | 否 | — |  | 动作，如 create、approve、reaudit |
| 7 | `target_type` | `varchar(50)` | 是 | — | IDX | 对象类型，如 purchase_order |
| 8 | `target_id` | `bigint(20)` | 是 | — |  | 对象id |
| 9 | `target_no` | `varchar(50)` | 是 | — |  | 对象编号，如订单号 |
| 10 | `detail` | `text` | 是 | — |  | 详情，修改前后值等 |
| 11 | `result_code` | `varchar(30)` | 是 | — |  | 结果码 |
| 12 | `ip` | `varchar(50)` | 是 | — |  | 来源IP |
| 13 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 14 | `created_at` | `datetime` | 否 | `current_timestamp()` | IDX | 创建时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_log_module_time`（module_code,created_at）
- 普通 `idx_log_target`（target_type,target_id）
- 普通 `idx_log_time`（created_at）
- 普通 `idx_log_user_time`（user_id,created_at）

### sys_session　会话（M11-1）

登录会话。登录签发的 token 及其有效期，是鉴权拦截器的第一道查证。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `session_token` | `varchar(64)` | 否 | — | UK | 会话令牌，随机生成，全局唯一 |
| 3 | `user_id` | `bigint(20)` | 否 | — | IDX | 用户id |
| 4 | `login_ip` | `varchar(50)` | 是 | — |  | 登录IP |
| 5 | `user_agent` | `varchar(300)` | 是 | — |  | 客户端标识 |
| 6 | `login_at` | `datetime` | 否 | `current_timestamp()` |  | 登录时间 |
| 7 | `last_active_at` | `datetime` | 否 | `current_timestamp()` |  | 最后活跃时间 |
| 8 | `expire_at` | `datetime` | 否 | — | IDX | 过期时间，超时后要求重新登录 |
| 9 | `logout_at` | `datetime` | 是 | — |  | 退出时间 |
| 10 | `logout_type` | `tinyint(4)` | 是 | — |  | 退出方式：1 主动退出、2 超时失效、3 管理员强制下线 |
| 11 | `status` | `tinyint(4)` | 否 | `1` |  | 会话状态：1 有效、2 已退出、3 已过期 |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 14 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 15 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_session_token`（session_token）
- 普通 `idx_session_expire`（expire_at,status）
- 普通 `idx_session_user`（user_id,status）

---

## 八、知识库域（5 张表 / 64 字段）

建表脚本 `db/07_schema_g_kb.sql`。

### kb_document　知识文档（M10-1）

知识文档。parse_status 记录解析链路状态（待解析/解析中/成功/失败），status 停用与失效不参与默认召回；删除走物理删除并清索引。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `doc_name` | `varchar(200)` | 否 | — | IDX | 文档名称 |
| 3 | `doc_type` | `tinyint(4)` | 否 | — | IDX | 文档类型：1 制度、2 规范、3 操作手册、4 流程说明 |
| 4 | `version` | `varchar(20)` | 否 | `'1.0'` |  | 文档版本号 |
| 5 | `effective_date` | `date` | 是 | — | IDX | 生效日期，版本优先召回依据 |
| 6 | `expire_date` | `date` | 是 | — |  | 失效日期 |
| 7 | `status` | `tinyint(4)` | 否 | `1` | IDX | 状态：1 启用、2 停用、3 失效；停用与失效不参与默认召回（M10-4-3、M8-4-3） |
| 8 | `file_path` | `varchar(300)` | 是 | — |  | 文件存储路径 |
| 9 | `file_type` | `varchar(20)` | 是 | — |  | 文件类型：pdf、docx、md |
| 10 | `file_size` | `bigint(20)` | 否 | `0` |  | 文件大小，单位字节 |
| 11 | `parse_status` | `tinyint(4)` | 否 | `1` |  | 解析状态：1 待解析、2 解析中、3 解析成功、4 解析失败 |
| 12 | `chunk_count` | `int(11)` | 否 | `0` |  | 切块数量 |
| 13 | `tags` | `varchar(200)` | 是 | — |  | 标签，逗号分隔 |
| 14 | `upload_by` | `bigint(20)` | 是 | — |  | 上传人id |
| 15 | `upload_at` | `datetime` | 是 | — |  | 上传时间 |
| 16 | `remark` | `varchar(500)` | 是 | — |  | 备注 |
| 17 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除；文档删除走物理删除并清理索引 |
| 18 | `version_lock` | `int(11)` | 否 | `0` |  | 乐观锁版本号，命名为 version_lock 以免与文档版本 version 冲突 |
| 19 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 20 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_doc_effective`（effective_date,expire_date）
- 普通 `idx_doc_name`（doc_name）
- 普通 `idx_doc_status`（status）
- 普通 `idx_doc_type`（doc_type）

### kb_chunk　文档切块（M10-2）

文档切块。切块尊重条款边界（第X条、3.2.1、一、），条款型问句才能原文直引；chunk_no 保序，chunk_text 是检索与引用的最小单位。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `doc_id` | `bigint(20)` | 否 | — | IDX | 所属文档id |
| 3 | `chunk_index` | `smallint(6)` | 否 | — |  | 切块序号，文档内从 1 开始 |
| 4 | `chunk_text` | `mediumtext` | 否 | — |  | 切块文本 |
| 5 | `section_no` | `varchar(50)` | 是 | — |  | 章节号，如 3.2.1 |
| 6 | `section_title` | `varchar(200)` | 是 | — |  | 章节标题 |
| 7 | `char_count` | `int(11)` | 否 | `0` |  | 字符数 |
| 8 | `embedding` | `mediumblob` | 是 | — |  | 向量，float32 数组序列化存放 |
| 9 | `vector_model` | `varchar(50)` | 是 | — |  | 向量化模型标识 |
| 10 | `vector_dim` | `smallint(6)` | 否 | `0` |  | 向量维度 |
| 11 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 12 | `version_lock` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_chunk_index`（doc_id,chunk_index）
- 普通 `idx_chunk_doc`（doc_id）

### kb_sparse_index　稀疏检索倒排索引（M10-3-2）

稀疏检索倒排索引。二元组词元 → 文档列表，**派生数据**，可随时从切块全量重建。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `chunk_id` | `bigint(20)` | 否 | — | IDX | 切块id |
| 3 | `token` | `varchar(20)` | 否 | — | IDX | 词元，中文双字词 |
| 4 | `tf` | `smallint(6)` | 否 | `1` |  | 词频 |
| 5 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 6 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 7 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 8 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_sparse_chunk_token`（chunk_id,token）
- 普通 `idx_sparse_chunk`（chunk_id）
- 普通 `idx_sparse_token`（token）

### kb_reject_log　拒答记录（M8-5-3）

拒答记录。记录被拒的问题与判定依据，用于度量拒答率；纯事件记录，无查询侧需求，故不建实体类。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `question` | `varchar(500)` | 否 | — |  | 原始问句 |
| 3 | `max_score` | `decimal(9,4)` | 否 | `0.0000` |  | 最高相似度得分 |
| 4 | `threshold` | `decimal(9,4)` | 否 | `0.0000` |  | 当时的拒答阈值 |
| 5 | `user_id` | `bigint(20)` | 是 | — |  | 提问用户id |
| 6 | `session_id` | `bigint(20)` | 是 | — |  | 会话id |
| 7 | `handled` | `tinyint(4)` | 否 | `0` | IDX | 是否已处理：0 未处理、1 已补充知识库、2 判定无需补充 |
| 8 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 9 | `created_at` | `datetime` | 否 | `current_timestamp()` | IDX | 创建时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_reject_handled`（handled）
- 普通 `idx_reject_time`（created_at）

### kb_retrieval_test　检索测试记录（M10-5）

检索测试记录。人工发起检索测试的留档，含查询词、命中切块与排序，用于调参与效果取证。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `question` | `varchar(500)` | 否 | — |  | 测试问句 |
| 3 | `expect_doc_id` | `bigint(20)` | 是 | — |  | 期望命中的文档id |
| 4 | `expect_chunk_id` | `bigint(20)` | 是 | — |  | 期望命中的切块id |
| 5 | `bm25_topk` | `text` | 是 | — |  | 稀疏检索 Top-K 结果，JSON 数组 |
| 6 | `vector_topk` | `text` | 是 | — |  | 向量检索 Top-K 结果，JSON 数组 |
| 7 | `hit_flag` | `tinyint(4)` | 否 | `0` | IDX | 是否命中：0 未命中、1 命中 |
| 8 | `hit_rank` | `smallint(6)` | 是 | — |  | 命中位置，未命中为空 |
| 9 | `hit_route` | `tinyint(4)` | 是 | — |  | 命中的检索路：1 稀疏、2 向量、3 两路均命中 |
| 10 | `test_batch` | `varchar(50)` | 是 | — | IDX | 测试批次标识，用于分组统计 |
| 11 | `operator_id` | `bigint(20)` | 是 | — |  | 测试人id |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` | IDX | 创建时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_test_batch`（test_batch）
- 普通 `idx_test_hit`（hit_flag）
- 普通 `idx_test_time`（created_at）

---

## 九、智能助手域（6 张表 / 78 字段）

建表脚本 `db/08_schema_h_ai.sql`。

### ai_session　智能会话（M7-1-2）

智能会话。多轮对话的容器，一条会话下挂多条消息。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `user_id` | `bigint(20)` | 否 | — | IDX | 用户id |
| 3 | `title` | `varchar(100)` | 否 | `'新会话'` |  | 会话标题，取首条问句前若干字 |
| 4 | `message_count` | `int(11)` | 否 | `0` |  | 消息条数 |
| 5 | `last_intent_code` | `varchar(50)` | 是 | — |  | 上一轮意图编码 |
| 6 | `context_json` | `varchar(1000)` | 是 | — |  | 上一轮实体与参数，JSON，用于多轮指代 |
| 7 | `last_time` | `datetime` | 否 | `current_timestamp()` |  | 最后活跃时间 |
| 8 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 9 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 10 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 11 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_ai_session_user`（user_id,last_time）

### ai_message　会话消息（M7-4）

会话消息。role 区分用户与助手，msg_type 区分知识类/教程类/数据类/澄清/拒答/纯文本，result_code 存七类结果码。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `session_id` | `bigint(20)` | 否 | — | IDX | 会话id |
| 3 | `role` | `tinyint(4)` | 否 | — |  | 角色：1 用户、2 助手 |
| 4 | `content` | `mediumtext` | 否 | — |  | 消息正文 |
| 5 | `msg_type` | `tinyint(4)` | 否 | `6` | IDX | 消息类型：1 知识类、2 教程类、3 数据类、4 澄清、5 拒答、6 纯文本 |
| 6 | `intent_code` | `varchar(50)` | 是 | — |  | 意图编码 |
| 7 | `params_json` | `varchar(1000)` | 是 | — |  | 提取并校验通过的参数，JSON |
| 8 | `result_json` | `mediumtext` | 是 | — |  | 结构化结果，JSON，表格数据存于此 |
| 9 | `ref_doc_ids` | `varchar(500)` | 是 | — |  | 引用文档id，逗号分隔 |
| 10 | `result_code` | `varchar(30)` | 是 | — | IDX | 结果码：Success、NotFound、Ambiguous、NoPermission、NoData、Unsupported、InvalidParameter |
| 11 | `elapsed_ms` | `int(11)` | 否 | `0` |  | 处理耗时，单位毫秒 |
| 12 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_msg_code`（result_code）
- 普通 `idx_msg_session`（session_id,id）
- 普通 `idx_msg_type`（msg_type）

### ai_intent　意图白名单（M9-1-3）

意图白名单。受控查询只认这里启用的五类意图（订单状态/交付进度/历史价格/库存余量/供应商交付），不在白名单的问题进不来。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `intent_code` | `varchar(50)` | 否 | — | UK | 意图编码，全局唯一 |
| 3 | `intent_name` | `varchar(50)` | 否 | — |  | 意图名称 |
| 4 | `service_code` | `varchar(50)` | 否 | — |  | 映射的后端查询服务标识，白名单内固定值 |
| 5 | `question_samples` | `varchar(500)` | 是 | — |  | 示例问句，多个以竖线分隔 |
| 6 | `enabled` | `tinyint(4)` | 否 | `1` | IDX | 是否启用：0 否、1 是 |
| 7 | `sort_no` | `int(11)` | 否 | `0` |  | 排序号 |
| 8 | `remark` | `varchar(300)` | 是 | — |  | 说明 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 12 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_intent_code`（intent_code）
- 普通 `idx_intent_enabled`（enabled,sort_no）

### ai_intent_param　意图参数规格（M9-2-4）

意图参数规格。每个意图允许哪些参数、什么类型、允许哪些操作符与枚举值——这是四道闸里「参数校验」的判定表。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `intent_id` | `bigint(20)` | 否 | — | IDX | 所属意图id |
| 3 | `param_code` | `varchar(50)` | 否 | — |  | 参数编码，如 order_no、material_id |
| 4 | `param_name` | `varchar(50)` | 否 | — |  | 参数名称 |
| 5 | `data_type` | `varchar(20)` | 否 | — |  | 数据类型：string、long、decimal、date、boolean |
| 6 | `operator_set` | `varchar(100)` | 否 | `'eq'` |  | 允许的操作符集合，逗号分隔，如 eq、like、in、gte、lte、between |
| 7 | `required` | `tinyint(4)` | 否 | `0` |  | 是否必填：0 否、1 是；必填缺失返回澄清而非默认值 |
| 8 | `enum_values` | `varchar(500)` | 是 | — |  | 枚举值域，逗号分隔，为 NULL 表示不做枚举限制 |
| 9 | `sort_no` | `int(11)` | 否 | `0` |  | 排序号 |
| 10 | `remark` | `varchar(300)` | 是 | — |  | 说明 |
| 11 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 12 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 13 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 14 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_intent_param`（intent_id,param_code）
- 普通 `idx_param_intent`（intent_id,sort_no）

### ai_entity_alias　实体别名映射（M9-3-1）

实体别名映射。把「一号仓」「华东五金」这类口语说法映射到真实 id，实体归一与多候选消歧靠它。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `entity_type` | `tinyint(4)` | 否 | — | IDX | 实体类型：1 物料、2 供应商、3 仓库 |
| 3 | `entity_id` | `bigint(20)` | 否 | — |  | 实体id |
| 4 | `entity_no` | `varchar(50)` | 否 | — |  | 实体编号，冗余 |
| 5 | `alias_name` | `varchar(100)` | 否 | — |  | 别名，口语化名称 |
| 6 | `source` | `tinyint(4)` | 否 | `1` |  | 来源：1 手工维护、2 拒答反馈补充 |
| 7 | `hit_count` | `int(11)` | 否 | `0` |  | 命中次数，用于排序候选 |
| 8 | `remark` | `varchar(200)` | 是 | — |  | 备注，如标注一词多物 |
| 9 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 10 | `version` | `int(11)` | 否 | `0` |  | 乐观锁版本号 |
| 11 | `created_by` | `bigint(20)` | 是 | — |  | 创建人id |
| 12 | `created_at` | `datetime` | 否 | `current_timestamp()` |  | 创建时间 |
| 13 | `updated_at` | `datetime` | 否 | `current_timestamp()` |  | 更新时间 |

索引：

- 唯一 `PRIMARY`（id）
- 唯一 `uk_alias`（entity_type,alias_name）
- 普通 `idx_alias_entity`（entity_type,entity_id）

### ai_query_audit　智能查询审计（M9-5-5）

智能查询审计。一次问答一行，params_json 记提取到的参数、rejected_params_json 记被拒参数、data_scope_json 记注入的数据范围，是「受控」的可验证凭据。

| # | 字段 | 类型 | 空 | 默认 | 键 | 说明 |
|---:|---|---|:--:|---|:--:|---|
| 1 | `id` | `bigint(20)` | 否 | — | PK AI | 主键 |
| 2 | `session_id` | `bigint(20)` | 是 | — |  | 会话id |
| 3 | `user_id` | `bigint(20)` | 否 | — | IDX | 用户id |
| 4 | `username` | `varchar(50)` | 是 | — |  | 用户账号，冗余 |
| 5 | `question` | `varchar(500)` | 否 | — |  | 原始问句 |
| 6 | `intent_code` | `varchar(50)` | 是 | — | IDX | 解析出的意图编码 |
| 7 | `params_json` | `varchar(1000)` | 是 | — |  | 提取并通过校验的参数，JSON |
| 8 | `rejected_params_json` | `varchar(500)` | 是 | — |  | 被拒绝的参数及原因，JSON |
| 9 | `data_scope_json` | `varchar(500)` | 是 | — |  | 本次注入的数据范围条件，JSON，用于证明过滤已生效 |
| 10 | `result_code` | `varchar(30)` | 否 | — | IDX | 结果码 |
| 11 | `row_count` | `int(11)` | 否 | `0` |  | 返回行数 |
| 12 | `elapsed_ms` | `int(11)` | 否 | `0` |  | 耗时，单位毫秒 |
| 13 | `ip` | `varchar(50)` | 是 | — |  | 来源IP |
| 14 | `is_deleted` | `tinyint(4)` | 否 | `0` |  | 逻辑删除：0 未删除、1 已删除 |
| 15 | `created_at` | `datetime` | 否 | `current_timestamp()` | IDX | 创建时间 |

索引：

- 唯一 `PRIMARY`（id）
- 普通 `idx_audit_code`（result_code）
- 普通 `idx_audit_intent`（intent_code）
- 普通 `idx_audit_time`（created_at）
- 普通 `idx_audit_user_time`（user_id,created_at）

---

## 十、全局约定与状态码总表

### 10.1 通用字段

通用字段是**按表裁剪**的，不是每张表都照抄一套。实测分布：

| 字段 | 类型 | 含义 | 覆盖表数 |
|---|---|---|---:|
| `id` | bigint | 自增主键。跨表关联一律用 id，不用业务编号 | 49 / 49 |
| `created_at` | datetime | 创建时间，默认 `current_timestamp()` | 49 / 49 |
| `is_deleted` | tinyint | 逻辑删除标记：0 未删、1 已删 | 49 / 49 |
| `updated_at` | datetime | 最后修改时间 | 44 / 49 |
| `version` | int | 乐观锁版本号，并发更新时用 | 43 / 49 |
| `created_by` | bigint | 创建人 id | 37 / 49 |
| `updated_by` | bigint | 最后修改人 id | 10 / 49 |
| `remark` | varchar | 业务备注，冒烟脚本用它打 `smk` 前缀以便清理 | 若干 |

缺 `updated_at` 的正好是五张**只增不改**的表：`ai_message`、`ai_query_audit`、
`kb_reject_log`、`kb_retrieval_test`、`sys_operation_log`。消息写出去就不改、审计与日志
写一次就定型——给这类表加 `updated_at` 只会得到一个恒等于 `created_at` 的字段，
反而让人误以为它会被更新。同理，它们也不带 `version`：没有并发更新就不需要乐观锁。

还有三处是刻意的不对称，读表时容易误解，先说清楚：**库存流水的 `is_deleted` 恒为 0**
（流水只增不删，作废靠写反向冲减流水）；**操作日志不提供删除入口**；
**`kb_*` 删除走物理删除**并同步清索引，因为切块与倒排索引是派生数据，
留逻辑删除的壳只会让索引与库不一致。

### 10.2 单据状态码

采购申请与采购订单**不用单状态机**，而是三个互不推导的字段并行表达三件事
（「审核通过、部分交付、后被关闭」用单状态装不下）：

| 字段 | 含义 | 取值 |
|---|---|---|
| `lifecycle_status` | 单据生命周期 | 1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 |
| `audit_status` | 审核结果 | 1 未审核、2 通过、3 驳回 |
| `fulfill_status` | 履约进度（仅订单） | 1 待交付、2 部分交付、3 全部交付、4 已关闭 |

驳回时 `lifecycle_status` 回落为 1（草稿），由 `audit_status=3` 表达驳回——
不新增第 6 个生命周期值，否则「草稿」与「被驳回的草稿」会在列表里分成两种状态、两套筛选。

### 10.3 各单据状态码汇总

| 对象 | 字段 | 取值 |
|---|---|---|
| 申请明细行 | `item_status` | 1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭 |
| 订单明细行 | `item_fulfill_status` | 1 待交付、2 部分交付、3 全部交付、4 已关闭 |
| 订单明细行 | `deviation_level` | 0 正常、1 关注、2 显著（按偏离**绝对值**比 sys_param 阈值） |
| 到货验收单 | `status` / `inspection_result` | status：1 草稿、2 已验收；result：1 合格、2 不合格、3 让步接收 |
| 入库单 | `status` | 1 草稿、2 已确认、3 已作废 |
| 审批任务 | `task_status` | 1 待处理、2 已通过、3 已驳回、4 已失效 |
| 审批节点 | `sign_type` | 1 或签（任一通过即推进）、2 会签（全部通过才推进） |
| 知识文档 | `parse_status` | 1 待解析、2 解析中、3 解析成功、4 解析失败 |
| 知识文档 | `status` | 1 启用、2 停用、3 失效（停用与失效不参与默认召回） |
| 库存流水 | `change_type` | 1 入库增加、2 作废冲减 |
| 价格参考 | `status` | 1 生效、2 停用 |
| 助手消息 | `msg_type` | 1 知识类、2 教程类、3 数据类、4 澄清、5 拒答、6 纯文本 |

### 10.4 七类结果码

接口统一返回 `{code, message, data}`，`code` 取值集合固定为七个，
**`NotFound`（对象不存在）与 `NoData`（对象存在但没有数据）必须分开**，
混用会让前端无法区分「查错了」与「确实没有」：

| 结果码 | 含义 | 典型场景 |
|---|---|---|
| `Success` | 成功 | 正常返回 |
| `InvalidParameter` | 参数非法 | 必填缺失、类型不符、跨字段矛盾 |
| `NotFound` | 对象不存在 | 按 id 查不到单据 |
| `NoData` | 无数据 | 查询成立但结果为空（如无可入库明细） |
| `NoPermission` | 无权限 | 缺权限点，或数据范围不含该对象 |
| `Ambiguous` | 存在歧义 | 实体归一命中多个候选，需用户确认 |
| `Unsupported` | 不支持 | 落到意图白名单之外的问题 |

### 10.5 唯一键与逻辑删除的配合

唯一键都不含 `is_deleted`，于是「删了再建同名」会撞键。三种情形三种处理，
这是本项目最容易踩的坑之一：

| 情形 | 处理 |
|---|---|
| 主数据（物料、供应商） | 查重时**连带已删记录**一起查，提示改用停用而非删除 |
| 关联子表（供货范围、联系用） | **物理删除后重建**，不留逻辑删除的壳 |
| 无唯一键的从表 | 从表走逻辑删除即可 |

另需注意：全局配置了 `logic-delete-field` 之后，MyBatis-Plus 的 `delete` 对任何含
`is_deleted` 的实体都会降级成 UPDATE，物理删除必须显式写原生 `@Delete`。

---

## 十一、生成与校验

```bash
# 重新生成（表结构变更后跑一次）
python scripts/gen_data_dict.py

# 只做覆盖性校验，不写文件（可进 CI）
python scripts/gen_data_dict.py --check
```

校验会检查四件事：库里的表是否全部归域、归域的表是否都在库里、每张表是否都有用途说明、
字段数是否与表清单吻合。任一项不通过即退出码 1。
