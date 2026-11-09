# 接口清单

> 本文件由 `scripts/gen_api_list.py` 从后端 Controller 源码生成，请勿手工修改。
> 重新生成：`python scripts/gen_api_list.py`；校验文档与代码是否一致：`--check`。

全部接口共 **189** 个，按模块分组。`权限点` 列为空表示只要求登录态（如登录、验证码、首页概览），其余接口都过 `@RequirePermission` 动作级校验。

| 模块 | 接口数 |
|---|---|
| 认证与会话 | 4 |
| M1 基础数据管理 | 41 |
| M2 采购申请与审批 | 18 |
| M3 采购订单管理 | 24 |
| M4 到货验收管理 | 10 |
| M5 采购入库与库存 | 19 |
| M6 价格参考与审批辅助 | 11 |
| M7 智能助手统一入口 | 10 |
| M8 制度与操作教程问答 | 2 |
| M10 知识库管理 | 17 |
| M11 平台与权限管理 | 25 |
| M12 管理后台与数据可视化 | 8 |

> M9（受控自然语言数据查询）没有独立 REST 端点：它以 `AiDataQueryPort` 的形式挂在 M7 的对话链路上，外部只经 `POST /api/assistant/chat` 进入，因此接口清单里看不到单独的 M9 路径。

## 认证与会话（4 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| POST | `/api/auth/login` | — | — |
| POST | `/api/auth/logout` | — | — |
| GET | `/api/auth/me` | — | — |
| POST | `/api/auth/password` | — | — |

## M1 基础数据管理（41 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| POST | `/api/m1/dicts/items` | m1:dict:create | M1-4-1、M1-4-2 新增字典项 |
| PUT | `/api/m1/dicts/items/{id}` | m1:dict:update | M1-4-1、M1-4-2 编辑字典项 |
| GET | `/api/m1/dicts/types` | m1:dict:list | M1-4-1 字典类型列表，含项数 |
| POST | `/api/m1/dicts/types` | m1:dict:create | M1-4-1 新增字典类型 |
| PUT | `/api/m1/dicts/types/{id}` | m1:dict:update | M1-4-1 编辑字典类型 |
| GET | `/api/m1/dicts/types/{typeCode}/items` | m1:dict:list | M1-4-1 管理端全量字典项，含停用项 |
| GET | `/api/m1/dicts/{typeCode}/items` | m1:dict:list | 字典接口 |
| POST | `/api/m1/material-categories` | m1:material_category:create | M1-1-7 新增分类 |
| GET | `/api/m1/material-categories/tree` | m1:material_category:list | 物料分类接口，功能点 M1-1-7 |
| PUT | `/api/m1/material-categories/{id}` | m1:material_category:update | M1-1-7 编辑分类，含启停与调整父节点 |
| POST | `/api/m1/materials` | m1:material:create | M1-1-2 新增 |
| GET | `/api/m1/materials/export` | m1:material:export | M1-1-5 按当前筛选导出 CSV |
| POST | `/api/m1/materials/import` | m1:material:import | M1-1-5 批量导入，逐行返回成功失败明细 |
| GET | `/api/m1/materials/import-template` | m1:material:import | M1-1-5 导入模板下载 |
| GET | `/api/m1/materials/options` | m1:material:list | M1-1-1 可选物料，仅启用，供新单据下拉 |
| GET | `/api/m1/materials/page` | m1:material:list | 物料接口 |
| GET | `/api/m1/materials/{id}` | m1:material:view | M1-1-1 详情，含扩展属性 |
| PUT | `/api/m1/materials/{id}` | m1:material:update | M1-1-3 编辑 |
| POST | `/api/m1/suppliers` | m1:supplier:create | M1-2-2 新增 |
| DELETE | `/api/m1/suppliers/attachments/{attachmentId}` | m1:supplier_attachment:delete | M1-2-5 删除附件记录，文件保留作审计依据 |
| GET | `/api/m1/suppliers/attachments/{attachmentId}/download` | m1:supplier:view | M1-2-5 下载资质附件 |
| GET | `/api/m1/suppliers/options` | m1:supplier:list | M1-2-1 可选供应商，仅启用，供新订单下拉 |
| GET | `/api/m1/suppliers/page` | m1:supplier:list | 供应商接口 |
| GET | `/api/m1/suppliers/{id}` | m1:supplier:view | M1-2-1 详情 |
| PUT | `/api/m1/suppliers/{id}` | m1:supplier:update | M1-2-2 编辑 |
| GET | `/api/m1/suppliers/{id}/attachments` | m1:supplier:view | M1-2-5 资质附件列表 |
| POST | `/api/m1/suppliers/{id}/attachments` | m1:supplier_attachment:upload | M1-2-5 上传资质附件，校验类型与大小后落盘 |
| GET | `/api/m1/suppliers/{id}/contacts` | m1:supplier:view | M1-2-4 联系人列表 |
| PUT | `/api/m1/suppliers/{id}/contacts` | m1:supplier_contact:manage | M1-2-4 联系人整体保存 |
| GET | `/api/m1/suppliers/{id}/scopes` | m1:supplier:view | M1-2-6 供货范围列表 |
| PUT | `/api/m1/suppliers/{id}/scopes` | m1:supplier_scope:manage | M1-2-6 供货范围整体保存 |
| POST | `/api/m1/warehouses` | m1:warehouse:create | M1-3-1 新增仓库 |
| PUT | `/api/m1/warehouses/locations/{locationId}` | m1:location:update | M1-3-2 编辑库位 |
| GET | `/api/m1/warehouses/options` | m1:warehouse:list | M1-3-1 可选仓库，仅启用 |
| GET | `/api/m1/warehouses/page` | m1:warehouse:list | 仓库接口 |
| GET | `/api/m1/warehouses/{id}` | m1:warehouse:list | M1-3-1 详情 |
| PUT | `/api/m1/warehouses/{id}` | m1:warehouse:update | M1-3-1 编辑仓库 |
| GET | `/api/m1/warehouses/{id}/locations` | m1:location:list | M1-3-2 库位列表 |
| POST | `/api/m1/warehouses/{id}/locations` | m1:location:create | M1-3-2 新增库位 |
| GET | `/api/m1/warehouses/{id}/users` | m1:warehouse:list | M1-3-3 仓库使用人列表 |
| PUT | `/api/m1/warehouses/{id}/users` | m1:warehouse_user:manage | M1-3-3 仓库使用人整体保存 |

## M2 采购申请与审批（18 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| POST | `/api/m2/requests` | m2:request:create | — |
| GET | `/api/m2/requests/owner-options` | m2:request_item:owner | M2-2-5 归属对象下拉数据源 |
| GET | `/api/m2/requests/todo` | m2:request:todo | — |
| POST | `/api/m2/requests/transfer` | m2:request:transfer | — |
| DELETE | `/api/m2/requests/{id}` | m2:request:delete | — |
| GET | `/api/m2/requests/{id}` | m2:request:view | 采购申请接口 |
| PUT | `/api/m2/requests/{id}` | m2:request:update | — |
| POST | `/api/m2/requests/{id}/approve` | m2:request:approve | — |
| POST | `/api/m2/requests/{id}/close` | m2:request:close | — |
| POST | `/api/m2/requests/{id}/copy` | m2:request:copy | — |
| GET | `/api/m2/requests/{id}/export` | m2:request:export | M2-1-6 打印 / 导出：CSV 平表视图，一行主表加多行明细 |
| POST | `/api/m2/requests/{id}/items` | m2:request_item:create | — |
| DELETE | `/api/m2/requests/{id}/items/{itemId}` | m2:request_item:delete | — |
| PUT | `/api/m2/requests/{id}/items/{itemId}` | m2:request_item:update | — |
| POST | `/api/m2/requests/{id}/reject` | m2:request:reject | — |
| POST | `/api/m2/requests/{id}/return-items` | m2:request_item:return | M2-2-4 按行退回：单据拆分为通过部分与退回部分 |
| POST | `/api/m2/requests/{id}/submit` | m2:request:submit | — |
| POST | `/api/m2/requests/{id}/withdraw` | m2:request:withdraw | — |

## M3 采购订单管理（24 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| POST | `/api/m3/orders` | m3:order:create | — |
| GET | `/api/m3/orders/delivery-summary` | m3:delivery:summary | M3-5-5 交付汇总视图：按供应商汇总已交付/未交付数量与金额 |
| POST | `/api/m3/orders/from-request` | m3:order:create | M3-1-3 由申请单生成订单：带入已审核申请单的明细与归属对象 |
| GET | `/api/m3/orders/owner-options` | m3:order_item:owner | M3-2-5 归属对象下拉数据源，与 M2 共用同一份档案 |
| GET | `/api/m3/orders/todo` | m3:order:todo | — |
| GET | `/api/m3/orders/{id}` | m3:order:view | 采购订单接口 |
| PUT | `/api/m3/orders/{id}` | m3:order:update | — |
| POST | `/api/m3/orders/{id}/approve` | m3:order:approve | — |
| POST | `/api/m3/orders/{id}/change` | m3:order_change:create | M3-4-1 变更登记：已审核订单的数量/单价/交期/地点/结算方式变更留痕 |
| GET | `/api/m3/orders/{id}/change-logs` | m3:order_change:list | M3-4-2 变更历史查看：时间线 |
| POST | `/api/m3/orders/{id}/close` | m3:order:close | — |
| GET | `/api/m3/orders/{id}/deliveries` | m3:delivery:list | M3-5-1 交付流水查询 |
| POST | `/api/m3/orders/{id}/deliveries` | m3:delivery:register | M3-5-1 交付进度登记 |
| GET | `/api/m3/orders/{id}/export` | m3:order:export | M3-1-6 打印 / 导出：CSV 平表视图，一行主表加多行明细 |
| POST | `/api/m3/orders/{id}/items` | m3:order_item:create | — |
| POST | `/api/m3/orders/{id}/items/close` | m3:order_item:close | M3-2-4 按行关闭：剩余数量不再要求交付 |
| DELETE | `/api/m3/orders/{id}/items/{itemId}` | m3:order_item:delete | — |
| PUT | `/api/m3/orders/{id}/items/{itemId}` | m3:order_item:update | — |
| POST | `/api/m3/orders/{id}/reaudit` | m3:order:reaudit | M3-3-5 反审：仅系统管理员、已审核且未到货 |
| POST | `/api/m3/orders/{id}/reject` | m3:order:reject | — |
| POST | `/api/m3/orders/{id}/submit` | m3:order:submit | — |
| GET | `/api/m3/orders/{id}/trace` | m3:order:trace | 来源申请追溯：返回来源申请单详情，手工创建订单返回 NoData |
| POST | `/api/m3/orders/{id}/void` | m3:order:void | M3-1-5 作废订单：未发生到货的订单可作废 |
| POST | `/api/m3/orders/{id}/withdraw` | m3:order:withdraw | — |

## M4 到货验收管理（10 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| POST | `/api/m4/inspections` | m4:inspection:create | M4-1-1 至 M4-1-3 新建到货登记（草稿） |
| GET | `/api/m4/inspections/arrivable-orders` | m4:inspection:create | M4-1-1 选单下拉：可登记的已审核订单（含未到货明细与剩余到货量） |
| GET | `/api/m4/inspections/arrivable-orders/{orderId}` | m4:inspection:create | M4-1-1 单个订单的未到货明细 |
| GET | `/api/m4/inspections/stat` | m4:inspection:stat | — |
| DELETE | `/api/m4/inspections/{id}` | m4:inspection:create | 删除草稿验收单（挂 create 权限：放弃未提交登记） |
| GET | `/api/m4/inspections/{id}` | m4:inspection:view | 到货验收接口 |
| PUT | `/api/m4/inspections/{id}` | m4:inspection:update | M4-1 编辑到货登记（仅草稿） |
| POST | `/api/m4/inspections/{id}/concession` | m4:inspection:concession | M4-2-4 让步接收标记（整单动作，归采购主管） |
| POST | `/api/m4/inspections/{id}/inbound` | m4:inbound:generate | M4-3-1 至 M4-3-4 由已验收单生成入库单草稿，可多次生成（部分入库/多批次） |
| POST | `/api/m4/inspections/{id}/submit` | m4:inspection:submit | M4-2-5 验收单提交：守恒校验后进入已验收态 |

## M5 采购入库与库存（19 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/m5/flows/page` | m5:flow:list | 库存流水接口 |
| GET | `/api/m5/flows/reconcile` | m5:flow:reconcile | M5-4-4 余额与流水对账：指定时间点前流水累计 vs 当前余额， 逐键（物料+仓库+批次+归属）比对，双端差集都算差异 |
| GET | `/api/m5/flows/{id}/source` | m5:flow:trace | M5-4-3 来源单据回溯：流水分录 → 对应入库单详情 |
| POST | `/api/m5/inbounds` | m5:inbound:create | M5-1-1 手工新建入库单（草稿）：全部带订单行=关联单，全部不带=手工单 |
| GET | `/api/m5/inbounds/next-batch-no` | m5:inbound:update | M5-2-1 批次号规则生成：B + yyyyMMdd + 3 位当日流水 |
| GET | `/api/m5/inbounds/page` | m5:inbound:list | 采购入库单接口 |
| DELETE | `/api/m5/inbounds/{id}` | m5:inbound:update | 删除入库草稿：复用草稿维护权限，事务内锁定状态 |
| GET | `/api/m5/inbounds/{id}` | m5:inbound:view | M5-1-2 详情：主表 + 明细 + 来源链 + 库存流水摘要 |
| PUT | `/api/m5/inbounds/{id}` | m5:inbound:update | M5-1-1 编辑入库单：仅草稿可编辑，明细整体替换，批次/库位在此登记 |
| POST | `/api/m5/inbounds/{id}/confirm` | m5:inbound:confirm | M5-1-3 确认入库：库存唯一写入点 |
| GET | `/api/m5/inbounds/{id}/trace` | m5:inbound:trace | M5-1-5 入库单溯源：入库单 → 验收单 → 订单 → 申请单 |
| POST | `/api/m5/inbounds/{id}/void` | m5:inbound:void | M5-1-4 作废入库单：归采购主管，需二次确认与原因 |
| GET | `/api/m5/stock/alert` | m5:stock:alert | M5-5-1 库存不足清单：可用量低于物料预警下限的物料与缺口 |
| GET | `/api/m5/stock/alert-stat` | m5:stock:alert | M5-5-2 短缺物料统计：groupBy=warehouse 按仓库、category 按分类汇总缺口 |
| GET | `/api/m5/stock/available` | m5:stock:available | M5-3-2 可用量查询：物料 + 仓库，可用量 = 数量 − 冻结 − 预留（R3） |
| GET | `/api/m5/stock/balance` | m5:stock:list | 库存查询接口 |
| GET | `/api/m5/stock/batch-trace` | m5:batch:trace | M5-2-3 批次追溯查询：批次号 → 入库明细 → 入库单 → 订单 → 供应商 |
| GET | `/api/m5/stock/export` | m5:stock:export | M5-3-4 库存余额导出：CSV（UTF-8 BOM），与各模块导出口径一致 |
| GET | `/api/m5/stock/owner-group` | m5:stock:owner_group | M5-3-3 按归属对象分组查询：公共库存与项目/工单库存分开呈现 |

## M6 价格参考与审批辅助（11 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/m6/deviations` | m6:stat:view | 价格偏离关注接口（M3-6 / M6-3） |
| POST | `/api/m6/deviations/recalc` | m6:price:update | 重算偏离：参考价变更后同步订单判定（挂 price:update，与参考价维护同角色） |
| GET | `/api/m6/deviations/summary` | m6:stat:view | 偏离汇总：关注/显著单数、按等级分布（P16 页面统计卡） |
| GET | `/api/m6/evidence/order/{orderId}` | m6:evidence:view | 审批证据面板接口（M6-4-1 / M6-4-2） |
| GET | `/api/m6/references` | m6:price:list | 价格参考接口（M6-1-1） |
| POST | `/api/m6/references` | m6:price:create | 新建参考价（生成新版本） |
| PUT | `/api/m6/references` | m6:price:update | 更新：改价/改生效日生成新版本，改备注/停用原地更新 |
| GET | `/api/m6/references/versions/{materialId}` | m6:price:view | 某物料的版本历史 |
| GET | `/api/m6/references/{id}` | m6:price:view | 详情（含版本历史） |
| GET | `/api/m6/stats` | m6:stat:view | 成交价统计接口（M6-1-2、M6-1-3） |
| POST | `/api/m6/stats/refresh` | m6:param:config | 刷新统计快照：按 sys_param 窗口口径重算并覆盖快照（无样本物料跳过） |

## M7 智能助手统一入口（10 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/assistant/audits/stats` | m9:audit:list | — |
| POST | `/api/assistant/chat` | m7:chat:query | 智能助手统一入口（M7-1、M7-2、M7-3） |
| POST | `/api/assistant/feedback` | m7:chat:query | M7-3-3 解析错误反馈：记录为测试集候选 |
| GET | `/api/assistant/llm/health` | m9:audit:list | — |
| GET | `/api/assistant/profile` | m7:chat:query | M7-1-4 入口提示：本角色能问什么、数据范围到哪 |
| GET | `/api/assistant/sessions` | m7:session:list | M7-1-2 对话历史 |
| GET | `/api/assistant/sessions/{id}/messages` | m7:session:list | M7-1-2 单个会话的消息列表 |
| POST | `/api/assistant/tasks/orders/{id}/review` | m7:chat:query + m9:data:query + m3:order:view | Explicit read-only AI tasks. No endpoint creates, saves, submits or approves a business document. */ public class AiTaskController { private final OrderReviewService reviews; private final RequestDraftService drafts; public AiTaskController(OrderReviewService reviews, RequestDraftService drafts) { this.reviews=reviews;this.drafts=drafts; } /** 单张订单审查：受控证据、规则检查和可降级的 AI 解读 |
| POST | `/api/assistant/tasks/request-draft` | m7:chat:query + m2:request:create + m2:request_item:create + m1:material:list | 采购需求起草：只提取候选字段，不创建业务单据 |
| POST | `/api/assistant/tasks/request-draft/validate` | m7:chat:query + m2:request:create + m2:request_item:create + m1:material:list | 人工确认候选字段后重新核对权限与档案，不保存采购申请 |

## M8 制度与操作教程问答（2 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/assistant/citations/coverage` | m9:audit:list | — |
| POST | `/api/assistant/citations/verify` | m8:kb:cite | — |

## M10 知识库管理（17 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/m10/documents` | m10:document:list | 知识文档接口（M10-1 上传解析、M10-4 文档与切块管理） |
| POST | `/api/m10/documents` | m10:document:upload | M10-1-1 文档上传（md/docx/pdf |
| POST | `/api/m10/documents/{docId}/chunks/merge` | m10:chunk:edit | 切块调整接口（M10-2-3 切块预览与调整的手工合并部分） |
| POST | `/api/m10/documents/{docId}/index/rebuild` | m10:index:rebuild | 索引接口（M10-3-3 单文档增量重建、M10-3-4 一键全量重建） |
| DELETE | `/api/m10/documents/{id}` | m10:document:delete | M10-4-5 删除并清理索引（物理删除） |
| GET | `/api/m10/documents/{id}` | m10:document:list | 文档详情 |
| PUT | `/api/m10/documents/{id}` | m10:document:update | M10-4-2 元数据维护（版本与生效日期） |
| GET | `/api/m10/documents/{id}/chunks` | m10:chunk:list | M10-4-4 切块查看 |
| GET | `/api/m10/documents/{id}/download` | m10:document:list | 下载上传文件（正文补录创建的文档无文件） |
| POST | `/api/m10/documents/{id}/parse` | m10:document:parse | M10-1-2 重新解析（从上传文件） |
| PUT | `/api/m10/documents/{id}/text` | m10:document:parse | M10-1-3 解析结果校对：正文补录/修正后重建切块与索引 |
| POST | `/api/m10/documents/{id}/toggle` | m10:document:toggle | M10-4-3 启用/停用（停用后不参与默认召回） |
| POST | `/api/m10/documents/{id}/versions` | m10:document:upload | M10-4-2 新版本：同文档名新建一行，带文件则解析新文件，否则复制来源版本切块 |
| POST | `/api/m10/index/rebuild` | m10:index:rebuild | 索引一键重建（全量，记录耗时） |
| POST | `/api/m10/retrieval-tests` | m10:test:run | M10-5-1 跑一次测试问句：稀疏与向量两路 Top-K 并列展示并做命中判定 |
| GET | `/api/m10/retrieval-tests/manifest` | m10:test:run | 检索测试接口（M10-5-1 检索测试台、M10-5-2 命中率统计） |
| GET | `/api/m10/retrieval-tests/stats` | m10:test:stat | M10-5-2 命中率统计：按测试批次或时间范围汇总两路命中率 |

## M11 平台与权限管理（25 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/m11/approval-flows/options` | m11:approval_node:list | M11-5-2 审批人候选，含角色与用户 |
| GET | `/api/m11/approval-flows/{billType}` | m11:approval_node:list | 审批流程配置接口，功能点 M11-5-1 流程配置与 M11-5-2 审批人指定 |
| PUT | `/api/m11/approval-flows/{billType}` | m11:approval_node:update | M11-5-1、M11-5-2 整体保存节点配置 |
| GET | `/api/m11/data-scopes` | m11:scope:list | 数据范围授权接口，功能点 M11-3-3 |
| PUT | `/api/m11/data-scopes` | m11:scope:grant | 保存某主体的数据范围，整体替换 |
| GET | `/api/m11/data-scopes/options` | m11:scope:list | 三个维度的候选值 |
| GET | `/api/m11/operation-logs` | m11:log:list | — |
| GET | `/api/m11/operation-logs/export` | m11:log:export | — |
| GET | `/api/m11/params` | m11:param:list | — |
| PUT | `/api/m11/params/{paramCode}` | m11:param:update | — |
| GET | `/api/m11/permissions` | m11:permission:list | — |
| GET | `/api/m11/query-audits` | m11:log:list | — |
| GET | `/api/m11/query-audits/export` | m11:log:export | — |
| GET | `/api/m11/query-audits/{id}` | m11:log:list | — |
| GET | `/api/m11/roles` | m11:role:list | 角色与角色授权接口 |
| POST | `/api/m11/roles` | m11:role:create | M11-2-5 新增角色 |
| PUT | `/api/m11/roles/{id}` | m11:role:update | M11-2-5 编辑角色 |
| GET | `/api/m11/roles/{id}/permissions` | m11:role:list | M11-3-2 查询角色已持有的权限点，供授权页回填 |
| PUT | `/api/m11/roles/{id}/permissions` | m11:role:grant | M11-3-2 角色授权，整体替换 |
| GET | `/api/m11/users` | m11:user:list | 用户管理接口 |
| POST | `/api/m11/users` | m11:user:create | M11-2-2 新增用户，响应返回一次性初始密码 |
| GET | `/api/m11/users/{id}` | m11:user:list | M11-2-1 用户详情，供编辑表单回填 |
| PUT | `/api/m11/users/{id}` | m11:user:update | M11-2-2 编辑用户 |
| POST | `/api/m11/users/{id}/reset-password` | m11:user:resetpwd | M11-2-4 重置密码，响应返回一次性初始密码 |
| PUT | `/api/m11/users/{id}/roles` | m11:user:update | M11-2-6 用户角色分配，整体替换 |

## M12 管理后台与数据可视化（8 个）

| 方法 | 路径 | 权限点 | 说明 |
|---|---|---|---|
| GET | `/api/m12/charts/status` | m12:chart:status | M12-3-1 采购订单状态分布（五个业务桶 + 不计入的草稿与作废） |
| GET | `/api/m12/charts/stock-alert` | m12:chart:stockalert | M12-3-4 库存不足物料统计（口径与 M5-5-1 库存预警一致） |
| GET | `/api/m12/charts/supplier` | m12:chart:supplier | M12-3-3 供应商采购金额排名（Top-N，占比分母是区间总金额） |
| GET | `/api/m12/charts/trend` | m12:chart:trend | M12-3-2 采购金额月度趋势（近 12 个月，缺月补零） |
| GET | `/api/m12/overview` | — | 管理后台与数据可视化接口（M12，8 个端点） |
| GET | `/api/m12/summary/export` | m12:summary:export | M12-2-3 汇总结果导出：type=purchase 采购单据，type=stock 库存 |
| GET | `/api/m12/summary/purchase` | m12:summary:purchase | M12-2-1 采购单据汇总：groupBy=status/supplier/material/month |
| GET | `/api/m12/summary/stock` | m12:summary:stock | M12-2-2 库存汇总：groupBy=warehouse/category/owner |
