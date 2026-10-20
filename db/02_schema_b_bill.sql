-- ==============================================================
-- 文件：02_schema_b_bill.sql
-- 域：B 采购单据（10 张表）  对应模块：M2、M3、M4、M5
-- 三状态约定（R1）：
--   lifecycle_status 单据生命周期状态 1 草稿 2 已提交 3 已审核 4 已关闭 5 已作废
--   audit_status     审批结果状态     1 未审核 2 通过   3 驳回
--   fulfill_status   业务履约状态     1 待交付 2 部分交付 3 全部交付 4 已关闭
-- 三个字段互不推导；驳回时 lifecycle_status 回落为 1，由 audit_status=3 表达"已驳回"
-- 采购申请单只设前两个字段，不产生履约状态（规划书 5.3.1）
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- B-1 采购申请单 purchase_request（M2-1、M2-3）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS purchase_request;
CREATE TABLE purchase_request (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  request_no       VARCHAR(50)   NOT NULL                COMMENT '申请单号，全系统唯一，按编号规则生成',
  request_date     DATE          NOT NULL                COMMENT '申请日期',
  dept_id          BIGINT        NOT NULL                COMMENT '申请部门，关联 dict_item.id（字典类型 dept）',
  applicant_id     BIGINT        NOT NULL                COMMENT '申请人，关联 sys_user.id',
  require_date     DATE          DEFAULT NULL            COMMENT '需求日期，默认取明细行最早需求日期',
  total_amount     DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '预估金额合计，明细行 est_amount 汇总',
  lifecycle_status TINYINT       NOT NULL DEFAULT 1      COMMENT '单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废',
  audit_status     TINYINT       NOT NULL DEFAULT 1      COMMENT '审批结果状态：1 未审核、2 通过、3 驳回',
  close_reason     VARCHAR(200)  DEFAULT NULL            COMMENT '关闭原因（M2-3-5 整单关闭）',
  remark           VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
  is_deleted       TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by       BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by       BIGINT        DEFAULT NULL            COMMENT '最后修改人id',
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_no (request_no),
  KEY idx_request_lifecycle (lifecycle_status),
  KEY idx_request_audit (audit_status),
  KEY idx_request_applicant (applicant_id),
  KEY idx_request_dept_date (dept_id, request_date),
  KEY idx_request_date (request_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请单 M2';

-- --------------------------------------------------------------
-- B-2 采购申请明细行 purchase_request_item（M2-2、M2-4-3）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS purchase_request_item;
CREATE TABLE purchase_request_item (
  id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  request_id          BIGINT        NOT NULL                COMMENT '主单id',
  item_no             SMALLINT      NOT NULL                COMMENT '行号，单内唯一',
  material_id         BIGINT        NOT NULL                COMMENT '物料id',
  quantity            DECIMAL(18,4) NOT NULL                COMMENT '申请数量',
  unit_id             BIGINT        NOT NULL                COMMENT '计量单位，关联 dict_item.id',
  est_price           DECIMAL(18,4) DEFAULT NULL            COMMENT '预估单价，可空；取参考价或人工填写，仅用于预估金额',
  est_amount          DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '预估金额，由数量乘预估单价得出，服务层计算',
  suggest_supplier_id BIGINT        DEFAULT NULL            COMMENT '建议供应商，可空（M2-2-3）',
  require_date        DATE          NOT NULL                COMMENT '行需求日期',
  owner_type          TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_id            BIGINT        DEFAULT NULL            COMMENT '归属对象id，owner_type=1 时可空',
  owner_no            VARCHAR(50)   DEFAULT NULL            COMMENT '归属对象编号，冗余，便于不联表查询',
  transferred_qty     DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已转订单数量，由转订单动作回写（M2-4-3）',
  item_status         TINYINT       NOT NULL DEFAULT 1      COMMENT '行状态：1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭',
  remark              VARCHAR(300)  DEFAULT NULL            COMMENT '行备注',
  is_deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version             INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by          BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_item_no (request_id, item_no),
  KEY idx_ri_request (request_id),
  KEY idx_ri_material (material_id),
  KEY idx_ri_owner (owner_type, owner_id),
  CONSTRAINT ck_ri_qty CHECK (quantity > 0),
  CONSTRAINT ck_ri_transferred CHECK (transferred_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请明细行 M2-2';

-- --------------------------------------------------------------
-- B-3 采购订单 purchase_order（M3-1、M3-3）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS purchase_order;
CREATE TABLE purchase_order (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no          VARCHAR(50)   NOT NULL                COMMENT '订单号，全系统唯一，按编号规则生成',
  source_request_id BIGINT        DEFAULT NULL            COMMENT '来源申请单id，手工建单时为空',
  source_request_no VARCHAR(50)   DEFAULT NULL            COMMENT '来源申请单号，冗余',
  supplier_id       BIGINT        NOT NULL                COMMENT '供应商id',
  buyer_id          BIGINT        NOT NULL                COMMENT '采购员，关联 sys_user.id，数据范围按此人与部门过滤',
  dept_id           BIGINT        NOT NULL                COMMENT '采购部门，关联 dict_item.id，数据范围按部门过滤',
  order_date        DATE          NOT NULL                COMMENT '下单日期',
  delivery_date     DATE          DEFAULT NULL            COMMENT '整单交货期，明细行未设置时取此值',
  delivery_place    VARCHAR(200)  DEFAULT NULL            COMMENT '整单交货地点',
  settlement_type   TINYINT       NOT NULL DEFAULT 1      COMMENT '结算方式：1 月结、2 货到付款、3 预付款',
  lifecycle_status  TINYINT       NOT NULL DEFAULT 1      COMMENT '单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废',
  audit_status      TINYINT       NOT NULL DEFAULT 1      COMMENT '审批结果状态：1 未审核、2 通过、3 驳回',
  fulfill_status    TINYINT       NOT NULL DEFAULT 1      COMMENT '业务履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭；由明细行重算，不由人工选择',
  total_qty         DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '订单数量合计，服务层汇总',
  total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '含税金额合计，服务层汇总',
  no_tax_amount     DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '不含税金额合计，服务层汇总',
  tax_amount        DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '税额合计，服务层汇总',
  deviation_flag    TINYINT       NOT NULL DEFAULT 0      COMMENT '整单偏离标记：0 无、1 关注、2 显著，取明细行最高等级',
  close_reason      VARCHAR(200)  DEFAULT NULL            COMMENT '关闭原因（M3-3-6）',
  remark            VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
  is_deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version           INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by        BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by        BIGINT        DEFAULT NULL            COMMENT '最后修改人id',
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_supplier (supplier_id),
  KEY idx_order_buyer (buyer_id),
  KEY idx_order_dept (dept_id),
  KEY idx_order_lifecycle (lifecycle_status),
  KEY idx_order_audit (audit_status),
  KEY idx_order_fulfill (fulfill_status),
  KEY idx_order_date (order_date),
  KEY idx_order_delivery (delivery_date),
  KEY idx_order_source (source_request_id),
  CONSTRAINT ck_order_total_qty CHECK (total_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单 M3';

-- --------------------------------------------------------------
-- B-4 采购订单明细行 purchase_order_item（M3-2、M3-5、M3-6）
-- delivered_qty 的唯一写入点是入库单确认（与 R2 同构），
-- 交付进度登记只写 delivery_progress，不改此字段
-- --------------------------------------------------------------
DROP TABLE IF EXISTS purchase_order_item;
CREATE TABLE purchase_order_item (
  id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id              BIGINT        NOT NULL                COMMENT '主单id',
  item_no               SMALLINT      NOT NULL                COMMENT '行号，单内唯一',
  material_id           BIGINT        NOT NULL                COMMENT '物料id',
  source_request_item_id BIGINT       DEFAULT NULL            COMMENT '来源申请明细行id，支撑按行转订单的多对多追溯（M2-4-1）',
  quantity              DECIMAL(18,4) NOT NULL                COMMENT '订单数量',
  unit_id               BIGINT        NOT NULL                COMMENT '计量单位',
  unit_price            DECIMAL(18,4) NOT NULL                COMMENT '含税单价',
  tax_rate              DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率，如 0.1300 表示 13%',
  amount                DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '含税金额，数量乘单价，服务层计算',
  no_tax_amount         DECIMAL(18,2) NOT NULL DEFAULT 0.00   COMMENT '不含税金额，含税金额除以一加税率，服务层计算',
  delivery_date         DATE          DEFAULT NULL            COMMENT '行交货期，默认取主表',
  delivery_place        VARCHAR(200)  DEFAULT NULL            COMMENT '行交货地点',
  delivered_qty         DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已交付数量，即已入库数量，仅由入库单确认回写',
  owner_type            TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_id              BIGINT        DEFAULT NULL            COMMENT '归属对象id',
  owner_no              VARCHAR(50)   DEFAULT NULL            COMMENT '归属对象编号，冗余',
  item_fulfill_status   TINYINT       NOT NULL DEFAULT 1      COMMENT '行履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭',
  deviation_rate        DECIMAL(9,4)  DEFAULT NULL            COMMENT '偏离率，(单价 − 参考价) / 参考价，服务层计算，不落前端',
  deviation_level       TINYINT       NOT NULL DEFAULT 0      COMMENT '偏离等级：0 正常、1 关注、2 显著',
  remark                VARCHAR(300)  DEFAULT NULL            COMMENT '行备注',
  is_deleted            TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version               INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by            BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_item_no (order_id, item_no),
  KEY idx_oi_order (order_id),
  KEY idx_oi_material (material_id),
  KEY idx_oi_source (source_request_item_id),
  KEY idx_oi_owner (owner_type, owner_id),
  KEY idx_oi_delivery (delivery_date, item_fulfill_status),
  CONSTRAINT ck_oi_qty CHECK (quantity > 0),
  CONSTRAINT ck_oi_delivered CHECK (delivered_qty >= 0),
  CONSTRAINT ck_oi_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单明细行 M3-2';

-- --------------------------------------------------------------
-- B-5 订单变更历史 order_change_log（M3-4）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS order_change_log;
CREATE TABLE order_change_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id     BIGINT       NOT NULL                COMMENT '订单id',
  order_no     VARCHAR(50)  NOT NULL                COMMENT '订单号，冗余',
  order_item_id BIGINT      DEFAULT NULL            COMMENT '订单明细行id，整单变更时为空',
  item_no      SMALLINT     DEFAULT NULL            COMMENT '行号，冗余',
  change_field VARCHAR(50)  NOT NULL                COMMENT '变更字段，如 quantity、unit_price、delivery_date',
  before_value VARCHAR(200) DEFAULT NULL            COMMENT '变更前值',
  after_value  VARCHAR(200) DEFAULT NULL            COMMENT '变更后值',
  change_reason VARCHAR(500) DEFAULT NULL           COMMENT '变更原因',
  operator_id  BIGINT       NOT NULL                COMMENT '操作人id',
  change_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  is_deleted   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by   BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_change_order (order_id, change_at),
  KEY idx_change_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单变更历史 M3-4';

-- --------------------------------------------------------------
-- B-6 交付进度登记 delivery_progress（M3-5-1）
-- 过程记录，用于跟单与超期提醒；不参与已交付数量与履约状态判定
-- --------------------------------------------------------------
DROP TABLE IF EXISTS delivery_progress;
CREATE TABLE delivery_progress (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id      BIGINT        NOT NULL                COMMENT '订单id',
  order_no      VARCHAR(50)   NOT NULL                COMMENT '订单号，冗余',
  order_item_id BIGINT        NOT NULL                COMMENT '订单明细行id',
  item_no       SMALLINT      NOT NULL                COMMENT '行号，冗余',
  material_id   BIGINT        NOT NULL                COMMENT '物料id，冗余，便于按物料筛选',
  delivery_qty  DECIMAL(18,4) NOT NULL                COMMENT '本次登记交付数量，仅供跟单参考',
  delivery_date DATE          NOT NULL                COMMENT '本次交付日期',
  batch_no      VARCHAR(50)   DEFAULT NULL            COMMENT '供应商批次说明，可选',
  remark        VARCHAR(300)  DEFAULT NULL            COMMENT '备注',
  operator_id   BIGINT        NOT NULL                COMMENT '登记人id',
  is_deleted    TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_dp_order (order_id, delivery_date),
  KEY idx_dp_item (order_item_id),
  CONSTRAINT ck_dp_qty CHECK (delivery_qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='交付进度登记 M3-5-1';

-- --------------------------------------------------------------
-- B-7 到货验收单 arrival_inspection（M4-1、M4-2）
-- 本项目无独立质检单，本表同时承担到货登记与合格判定
-- --------------------------------------------------------------
DROP TABLE IF EXISTS arrival_inspection;
CREATE TABLE arrival_inspection (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspection_no     VARCHAR(50)   NOT NULL                COMMENT '验收单号，全系统唯一',
  order_id          BIGINT        NOT NULL                COMMENT '来源订单id',
  order_no          VARCHAR(50)   NOT NULL                COMMENT '来源订单号，冗余',
  supplier_id       BIGINT        NOT NULL                COMMENT '供应商id，冗余',
  arrival_date      DATE          NOT NULL                COMMENT '到货日期',
  warehouse_id      BIGINT        NOT NULL                COMMENT '收货仓库id',
  inspection_mode   TINYINT       NOT NULL DEFAULT 2      COMMENT '验收方式：1 免检、2 抽样、3 全检；只登记，不做抽检规则计算（M4-2-1）',
  inspection_result TINYINT       DEFAULT NULL            COMMENT '验收结果：1 合格、2 不合格、3 让步接收（M4-2-2）',
  qualified_qty     DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '合格数量合计',
  unqualified_qty   DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不合格数量合计，仅登记，不生成入库单',
  concession_flag   TINYINT       NOT NULL DEFAULT 0      COMMENT '让步接收标记：0 否、1 是（M4-2-4）',
  concession_reason VARCHAR(500)  DEFAULT NULL            COMMENT '让步接收理由，标记为 1 时必填',
  inspector_id      BIGINT        NOT NULL                COMMENT '验收人id',
  status            TINYINT       NOT NULL DEFAULT 1      COMMENT '验收单状态：1 草稿、2 已验收（M4-2-5）',
  remark            VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
  is_deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version           INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by        BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by        BIGINT        DEFAULT NULL            COMMENT '最后修改人id',
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_no (inspection_no),
  KEY idx_ai_order (order_id),
  KEY idx_ai_order_no (order_no),
  KEY idx_ai_supplier (supplier_id),
  KEY idx_ai_warehouse (warehouse_id),
  KEY idx_ai_arrival (arrival_date),
  KEY idx_ai_status (status),
  CONSTRAINT ck_ai_qty CHECK (qualified_qty >= 0 AND unqualified_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='到货验收单 M4';

-- --------------------------------------------------------------
-- B-8 到货验收明细 arrival_inspection_item（M4-2-3、M5-2）
-- 合格数加不合格数必须等于到货数（R12），由服务层校验
-- --------------------------------------------------------------
DROP TABLE IF EXISTS arrival_inspection_item;
CREATE TABLE arrival_inspection_item (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspection_id  BIGINT        NOT NULL                COMMENT '验收单id',
  order_item_id  BIGINT        NOT NULL                COMMENT '订单明细行id',
  material_id    BIGINT        NOT NULL                COMMENT '物料id，冗余',
  unit_id        BIGINT        NOT NULL                COMMENT '计量单位，冗余',
  arrival_qty    DECIMAL(18,4) NOT NULL                COMMENT '本次到货数量',
  qualified_qty  DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '合格数量',
  unqualified_qty DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '不合格数量',
  batch_no       VARCHAR(50)   DEFAULT NULL            COMMENT '预登记批次号（M5-2-1）',
  owner_type     TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型，继承订单行',
  owner_id       BIGINT        DEFAULT NULL            COMMENT '归属对象id，继承订单行',
  owner_no       VARCHAR(50)   DEFAULT NULL            COMMENT '归属对象编号，继承订单行',
  remark         VARCHAR(300)  DEFAULT NULL            COMMENT '行备注',
  is_deleted     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_aii_inspection_item (inspection_id, order_item_id),
  KEY idx_aii_order_item (order_item_id),
  KEY idx_aii_material (material_id),
  CONSTRAINT ck_aii_qty CHECK (arrival_qty > 0 AND qualified_qty >= 0 AND unqualified_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='到货验收明细 M4-2';

-- --------------------------------------------------------------
-- B-9 采购入库单 inbound_order（M5-1）
-- 确认动作是本系统库存数量的唯一写入点（R2）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS inbound_order;
CREATE TABLE inbound_order (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  inbound_no    VARCHAR(50)  NOT NULL                COMMENT '入库单号，全系统唯一',
  inspection_id BIGINT       DEFAULT NULL            COMMENT '来源验收单id，手工建单时为空',
  inspection_no VARCHAR(50)  DEFAULT NULL            COMMENT '来源验收单号，冗余',
  order_id      BIGINT       DEFAULT NULL            COMMENT '来源订单id',
  order_no      VARCHAR(50)  DEFAULT NULL            COMMENT '来源订单号，冗余',
  supplier_id   BIGINT       DEFAULT NULL            COMMENT '供应商id，冗余',
  warehouse_id  BIGINT       NOT NULL                COMMENT '入库仓库id',
  inbound_date  DATE         NOT NULL                COMMENT '入库日期',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '入库单状态：1 草稿、2 已确认、3 已作废',
  confirm_time  DATETIME     DEFAULT NULL            COMMENT '确认时间（M5-1-3）',
  confirm_by    BIGINT       DEFAULT NULL            COMMENT '确认人id',
  void_time     DATETIME     DEFAULT NULL            COMMENT '作废时间（M5-1-4）',
  void_reason   VARCHAR(200) DEFAULT NULL            COMMENT '作废原因',
  operator_id   BIGINT       NOT NULL                COMMENT '制单人id',
  remark        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  is_deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL            COMMENT '最后修改人id',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbound_no (inbound_no),
  KEY idx_io_inspection (inspection_id),
  KEY idx_io_order (order_id),
  KEY idx_io_warehouse (warehouse_id),
  KEY idx_io_date (inbound_date),
  KEY idx_io_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购入库单 M5-1';

-- --------------------------------------------------------------
-- B-10 采购入库明细 inbound_item（M5-1、M5-2）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS inbound_item;
CREATE TABLE inbound_item (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  inbound_id    BIGINT        NOT NULL                COMMENT '入库单id',
  order_item_id BIGINT        DEFAULT NULL            COMMENT '订单明细行id，手工建单时为空',
  material_id   BIGINT        NOT NULL                COMMENT '物料id',
  quantity      DECIMAL(18,4) NOT NULL                COMMENT '入库数量，取验收合格数量',
  unit_id       BIGINT        NOT NULL                COMMENT '计量单位',
  batch_no      VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '批次号，无批次管理时为空串（M5-2-1）',
  location_id   BIGINT        DEFAULT NULL            COMMENT '库位id，必须属于入库单仓库（M5-2-2）',
  owner_type    TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_id      BIGINT        DEFAULT NULL            COMMENT '归属对象id',
  owner_no      VARCHAR(50)   DEFAULT NULL            COMMENT '归属对象编号',
  remark        VARCHAR(300)  DEFAULT NULL            COMMENT '行备注',
  is_deleted    TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ii_inbound (inbound_id),
  KEY idx_ii_order_item (order_item_id),
  KEY idx_ii_material (material_id),
  KEY idx_ii_batch (batch_no),
  KEY idx_ii_owner (owner_type, owner_id),
  CONSTRAINT ck_ii_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购入库明细 M5-1/M5-2';
