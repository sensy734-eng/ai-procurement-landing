# 功能与数据表映射

> 口径说明：本文件是**功能 ↔ 数据表**的双向映射，供论文"数据库设计"章节与答辩追问使用。
> 所有"引用面"数字来自对 `server/src/main/java` 下 46 个 Service / 执行器 / 解析器的静态扫描（按 `XxxMapper` 引用计数），不是凭表名推测。

## 一、功能全景（12 个模块）

| 模块 | 功能组 | 核心功能 | 主要数据表 |
|---|---|---|---|
| **M0 认证底座**（跨模块） | 登录与会话 | 账号密码登录、会话 token、强制下线、数据范围解析 | `sys_user` `sys_session` `sys_user_role` `sys_data_scope` |
| **M1 基础数据**（18 点） | 主数据维护 | 物料/分类/扩展属性、供应商/联系人/资质/供货范围、仓库/库位/使用人、字典、货权归属 | `material` `material_category` `material_ext_attr` `supplier` `supplier_contact` `supplier_attachment` `supplier_scope` `warehouse` `warehouse_location` `warehouse_user` `dict_type` `dict_item` `owner_object` |
| **M2 采购申请**（20 点） | 申请与转单 | 申请单 CRUD、明细行、提交/通过/驳回/撤回/关闭、按行退回、转采购订单 | `purchase_request` `purchase_request_item` `approval_task` `purchase_order` `supplier_scope` |
| **M3 采购订单**（25 点） | 订单履约 | 订单 CRUD、变更留痕、交付登记、履约重算、关闭、反审、超期计算、来源追溯、价格偏离列 | `purchase_order` `purchase_order_item` `order_change_log` `delivery_progress` `price_reference` `price_stat_snapshot` |
| **M4 到货验收**（15 点） | 验收判定 | 验收登记、一单多次到货、合格/不合格拆分、让步接收、生成入库单 | `arrival_inspection` `arrival_inspection_item` `inbound_order` `inbound_item` |
| **M5 采购入库与库存**（17 点） | 记账与查询 | 入库单生成/确认/作废、批次与库位、余额与流水、可用量、缺料预警、对账 | `inbound_order` `inbound_item` `stock_balance` `stock_flow` `warehouse_location` `owner_object` |
| **M6 价格辅助**（7 点） | 价格参考 | 参考价版本链、历史成交统计快照、偏离率分级、证据面板 | `price_reference` `price_stat_snapshot` `purchase_order` `purchase_order_item` `sys_param` |
| **M7 助手入口**（合 53 点） | 统一入口 | 会话管理、意图路由、消息落库、审计留痕 | `ai_session` `ai_message` `ai_query_audit` `sys_param` |
| **M8 制度问答** | 检索问答 | 双路召回（BM25 + 向量）、RRF 融合、条款直引、教程拆步、依据不足拒答 | `kb_document` `kb_chunk` `kb_sparse_index` `ai_message` |
| **M9 受控查询** | 数据问答 | 五意图白名单、实体归一、参数提取、四道闸校验、只读查询、模板转述 | `ai_intent` `ai_intent_param` `ai_entity_alias` `purchase_order` `delivery_progress` `ai_query_audit` |
| **M10 知识库**（17 点） | 文档与索引 | 文档上传解析、条款切块、倒排索引、双路检索测试、拒答统计 | `kb_document` `kb_chunk` `kb_sparse_index` `kb_retrieval_test` `kb_reject_log`※ |
| **M11 平台与权限**（22 点） | 权限审批 | 用户/角色/权限点树、数据范围、会话、操作日志、审批流配置 | `sys_user` `sys_role` `sys_permission` `sys_role_permission` `sys_user_role` `sys_data_scope` `sys_session` `sys_operation_log` `approval_flow_node` `approval_task` |
| **M12 管理后台**（11 点） | 看板 | 首页指标、采购/库存/出库三组汇总、四类图表 | `purchase_order` `purchase_order_item` `stock_balance` `warehouse` `dict_item` |

※ `kb_reject_log` 建表但**未接代码**，见 4.4。

## 二、功能组 → 数据表（正向）

### M2 采购申请
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 申请单 CRUD | `purchase_request` `purchase_request_item` | 主表 + 明细行 |
| 选物料 | `material` `material_category` `dict_item` | 停用物料不可选 |
| 选建议供应商 | `supplier` `supplier_scope` | 供货范围过滤 + 名称快照 |
| 提交审批 | `approval_task` `approval_flow_node` `sys_user` | 按 node_seq 生成任务 |
| 转订单 | `purchase_request` `purchase_request_item` `purchase_order` `purchase_order_item` `supplier_scope` | `transferred_qty` 回写；供货范围硬校验 |
| 关键动作留痕 | `sys_operation_log` | 提交/撤回/驳回 |

### M3 采购订单
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 订单 CRUD | `purchase_order` `purchase_order_item` | 三状态字段同表 |
| 变更留痕 | `order_change_log` | 字段级前后值 |
| 交付登记 | `purchase_order_item` `delivery_progress` | 累计交付量与履约状态 |
| 价格偏离列 | `price_reference` `purchase_order_item` `sys_param` | 偏离率与等级由后端算 |
| 证据面板 | `price_reference` `price_stat_snapshot` | 按订单创建日还原当时参考价 |
| 关闭/反审 | `purchase_order` `arrival_inspection` `inbound_order` | 有验收或入库则禁止 |
| 来源追溯 | `purchase_request` | 申请 → 订单链 |

### M4 到货验收
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 选单与收货仓库 | `purchase_order` `purchase_order_item` `warehouse` | 双配额校验 |
| 验收登记 | `arrival_inspection` `arrival_inspection_item` | 到货 = 合格 + 不合格 |
| 让步接收 | `arrival_inspection` | 整单动作，非行级 |
| 生成入库单 | `inbound_order` `inbound_item` | 草稿态，库位置空 |

### M5 入库与库存
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 入库单确认 | `inbound_order` `inbound_item` `stock_balance` `stock_flow` `purchase_order_item` | 单事务四步；库存唯一写入点 |
| 批次与库位 | `inbound_item` `warehouse_location` | 确认前必须补全库位 |
| 作废冲减 | `inbound_order` `stock_flow` `purchase_order_item` | 反向流水 + 交付量回退 |
| 库存台账 | `stock_balance` `material` `warehouse` | 可用量现算 |
| 缺料预警 | `stock_balance` `material` | 缺口现算不落库 |
| 对账 | `stock_balance` `stock_flow` | 四维键比对 |

### M6 价格参考
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 参考价维护 | `price_reference` | 同物料 `price_version` 递增 |
| 历史成交 | `purchase_order` `purchase_order_item` `price_stat_snapshot` | 只算已审核已成交 |
| 偏离分级 | `price_reference` `sys_param` | 阈值 0.05 / 0.15 走参数 |
| 证据面板 | `price_reference` `price_stat_snapshot` | 双语义取价 |

### M8 / M9 / M10 智能能力
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 文档上传解析 | `kb_document` | 解析状态码 1~4 |
| 条款切块 | `kb_chunk` `kb_sparse_index` | 切块与倒排同步重建 |
| 检索测试 | `kb_retrieval_test` | 记录两路 Top5 与耗时 |
| 制度问答 | `kb_document` `kb_chunk` `kb_sparse_index` `ai_message` | 拒答走 msg_type=5 |
| 意图路由 | `ai_intent` `ai_intent_param` `ai_entity_alias` | 配置驱动，非硬编码 |
| 受控查询 | `purchase_order` `purchase_order_item` `delivery_progress` `material` `supplier` `warehouse` | 强制注入数据范围 |
| 查询审计 | `ai_query_audit` `ai_message` | 被拒参数与数据范围落 JSON |

### M11 平台权限
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 用户管理 | `sys_user` `sys_user_role` | 停用即时失效 |
| 角色授权 | `sys_role` `sys_role_permission` `sys_permission` | 内置角色不可删 |
| 数据范围 | `sys_data_scope` `warehouse` `owner_object` `sys_user` `sys_role` | 部门 / 仓库 / 货权三类 |
| 审批流配置 | `approval_flow_node` `approval_task` `sys_user` `sys_role` | 或签 / 会签 |
| 操作日志 | `sys_operation_log` | 不清理 |

### M12 看板
| 功能 | 读写的表 | 说明 |
|---|---|---|
| 采购汇总 | `purchase_order` `purchase_order_item` | 已成交口径 lifecycle∈{3,4} 且 audit=2 |
| 库存汇总 | `stock_balance` `warehouse` | 复用 M5 缺口算法 |
| 图表 | `purchase_order` `stock_balance` `dict_item` | 供应商排名、状态分布、趋势 |

## 三、数据表 → 功能（反向，49 张全量）

「引用面」= 引用该 Mapper 的组件数（Service / 执行器 / 解析器），括号内为涉及的模块数。

### A 域 基础数据（13 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `material_category` | 4（3 模块） | M1 分类树维护、物料挂分类；M5 库存按分类汇总；M1 供货范围按分类选 |
| `material` | **12（7 模块）** | M1 维护；M2 申请行选料；M3 订单行；M4 验收行；M5 入库与库存；M6 参考价/偏离/证据；M9 实体归一 |
| `material_ext_attr` | 1 | M1 物料扩展属性（属性随品类而异，故不并入主表） |
| `supplier` | **12（7 模块）** | M1 维护；M2 选源；M3 下单；M4 验收；M5 入库；M6 成交统计；M9 查询 |
| `supplier_contact` | 1 | M1 供应商联系人（一对多） |
| `supplier_attachment` | 1 | M1 资质附件（营业执照、体系证书） |
| `supplier_scope` | 3 | M1 供货范围登记；M2 选源推荐；M3 下单"这家能不能供这个"硬校验 |
| `warehouse` | **8（7 模块）** | M1 维护；M4 收货仓库；M5 库存维度；M11 数据范围；M12 看板；M7 用户画像；M9 查询 |
| `warehouse_location` | 2 | M1 库位维护；M5 入库确认前补库位 |
| `warehouse_user` | 1 | M1 仓库使用人（仓管员授权，决定可见仓范围） |
| `dict_type` | 1 | M1 字典类型 |
| `dict_item` | **14（9 模块）** | M1 字典项维护；全系统枚举翻译（单据状态、结果码、类型、单位） |
| `owner_object` | 5（4 模块） | M1 货权归属；M2 申请指定归属；M3 订单归属；M5 库存归属维度；M11 数据范围 |

### B 域 单据链（10 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `purchase_request` | 3 | M2 申请单主体；M2 转订单来源；M5 入库追溯来源 |
| `purchase_request_item` | 2 | M2 申请明细行（含 `transferred_qty` 已转量） |
| `purchase_order` | **8（6 模块）** | M3 订单主体；M4 验收依据；M5 入库依据；M6 成交统计；M9 查询；M2 转单落库 |
| `purchase_order_item` | **7（5 模块）** | 订单明细：数量、单价、`delivered_qty` 交付量、偏离快照 |
| `order_change_log` | 1 | M3 订单变更留痕（字段级前后值） |
| `delivery_progress` | 2 | M3 交付进度汇总（免去每次聚合明细）；M9 交付查询 |
| `arrival_inspection` | 3 | M4 验收单主体；M5 生成入库来源；M3 反审前置校验 |
| `arrival_inspection_item` | 1 | M4 验收明细：到货量 / 合格量 / 不合格量三段 |
| `inbound_order` | 4 | M5 入库单主体；M4 生成；M3 反审校验；M5 库存写入 |
| `inbound_item` | 3 | M5 入库明细：数量 + 批次 + 库位 |

### C 域 库存（2 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `stock_balance` | 2 | M5 库存余额（**唯一写入点**）、可用量、缺料预警、对账基线 |
| `stock_flow` | 2 | M5 库存流水（入库增量 / 作废冲减）、对账比对 |

### D 域 审批（2 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `approval_flow_node` | 2 | M11 审批流配置：申请单 node_seq1 主管或签；订单 node_seq1 主管或签 + node_seq2 财务会签 |
| `approval_task` | 1 | 审批任务实例：M2 申请审批、M3 订单会签、交办与历史呈现 |

### E 域 价格与参数（3 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `price_reference` | 4 | M6 参考价版本链；M3 订单行偏离计算（`selectEffective` / `selectEffectiveAsOf` 双语义） |
| `price_stat_snapshot` | 2 | M6 历史成交快照；M3 证据面板还原 |
| `sys_param` | 7 | 编号规则（`BillNoGenerator`）、偏离阈值、成交窗口、检索阈值、意图路由开关 |

### F 域 平台（8 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `sys_user` | **12（6 模块）** | M11 用户管理；登录认证；M2~M5 经办人与审批人；M1 仓库使用人 |
| `sys_role` | 4 | M11 角色；审批人按角色解析；数据范围按角色 |
| `sys_permission` | 2 | M11 权限点树；`@RequirePermission` 校验 |
| `sys_role_permission` | 1 | M11 角色授权关系 |
| `sys_user_role` | 2 | M11 用户分配角色 |
| `sys_data_scope` | 2 | M11 数据范围配置；运行时注入（部门 / 仓库 / 货权） |
| `sys_operation_log` | 3 | M11 操作日志查询；M2/M3 关键动作留痕 |
| `sys_session` | 1 | 登录会话与 token 校验（M11 会话管理页未做，见路线图轮次 2 差异说明） |

### G 域 知识库（5 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `kb_document` | 7（2 模块） | M10 文档管理（上传/解析/生效日期/版本）；M8 检索与引用标注 |
| `kb_chunk` | 7（2 模块） | M10 条款切块；M8 召回与直引 |
| `kb_sparse_index` | 5 | M10 倒排索引重建；M8 BM25 一路召回 |
| `kb_retrieval_test` | 1 | M10 检索测试记录（两路 Top5、耗时、命中） |
| `kb_reject_log` | **0** | 设计意图为 M8-5-3 拒答明细，**尚未接线**（见 4.4） |

### H 域 AI（6 张）

| 表 | 引用面 | 服务的功能 |
|---|---|---|
| `ai_session` | 1 | M7 会话（用户 + 助手多轮上下文） |
| `ai_message` | 3 | M7/M8 消息落库；`msg_type` 1~6 区分路由与拒答（5 = 拒答） |
| `ai_intent` | 1 | M9 五意图白名单（配置驱动） |
| `ai_intent_param` | 1 | M9 参数定义、`operator_set` 与 `enum_values` 值域 |
| `ai_entity_alias` | 1 | M9 实体别名归一（"螺丝"→物料编码） |
| `ai_query_audit` | 2 | M7 审计写入；M11 查询审计页（含被拒参数、数据范围、拒答率） |

## 四、结构特征

### 4.1 枢纽表（跨 ≥4 个模块）

| 表 | 模块数 | 被谁共用 |
|---|---|---|
| `dict_item` | 9 | 几乎所有列表页都要把编码翻成名称 |
| `material` / `supplier` / `warehouse` | 7 | 三大主数据，全流程引用 |
| `sys_user` / `purchase_order` | 6 | 人 + 核心单据 |
| `purchase_order_item` / `sys_param` | 5 | 明细行 + 全局参数 |
| `owner_object` | 4 | 货权归属是库存与申请的共同维度 |

**工程含义**：改这 9 张表的字段或语义，必须跑全量回归（12 支冒烟 884 断言），因为它们被多个模块同时依赖。

### 4.2 内聚表（只服务 1 个组件）

`material_ext_attr`、`supplier_contact`、`supplier_attachment`、`warehouse_user`、`dict_type`、`order_change_log`、`arrival_inspection_item`、`sys_role_permission`、`sys_session`、`approval_task`、`ai_session`、`ai_intent`、`ai_intent_param`、`ai_entity_alias`、`kb_retrieval_test` —— 共 15 张。

**工程含义**：这些表改动影响面可控，边界清晰，符合"高内聚"。

### 4.3 反向依赖（值得在论文里说的一句）

`m6/price` 包里的 `PriceDeviationService` 会读 `purchase_order_item`，`m8/qa` 会读 `kb_chunk`，`m12/dashboard` 会读 `purchase_order` —— 说明**模块包边界 ≠ 数据边界**：业务能力按模块切，数据按域共享。这是刻意的（看板与偏离都要跨模块取数），代价是改单据表要跑全量回归。

### 4.4 一处如实说明：`kb_reject_log` 未接线

- DDL 已建表（`07_schema_g_kb.sql`），注释写"拒答记录同时是知识库补充的输入"。
- 但代码里 `grep kb_reject_log` 与 `grep KbReject` 均为 **0 命中**，表长期 0 行。
- 现有替代：拒答时把消息以 `msg_type=5` 写入 `ai_message`，`CitationStatsService` 据此统计拒答数，冒烟脚本 `M8-5-3 拒答单独计数` 断言的就是这条路径。
- 结论：**M8-5-3 的"统计"已落地，"拒答明细单独留痕"未落地**。论文里应写成"设计预留"，或补一段写入逻辑使其名副其实。
