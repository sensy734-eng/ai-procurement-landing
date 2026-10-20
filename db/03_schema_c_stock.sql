-- ==============================================================
-- 文件：03_schema_c_stock.sql
-- 域：C 库存（2 张表）  对应模块：M5
-- 纪律：库存余额只能由入库单确认写入（R2）；
--       可用量不落库，由后端按数量减冻结量减预留量实时计算（R3）
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- C-1 库存余额 stock_balance（M5-3）
-- 公共库存的 owner_id 存 0、owner_no 存 PUBLIC，保证唯一键有效
-- --------------------------------------------------------------
DROP TABLE IF EXISTS stock_balance;
CREATE TABLE stock_balance (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id  BIGINT        NOT NULL                COMMENT '物料id',
  warehouse_id BIGINT        NOT NULL                COMMENT '仓库id',
  batch_no     VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '批次号，无批次管理时为空串',
  owner_type   TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_id     BIGINT        NOT NULL DEFAULT 0      COMMENT '归属对象id，公共库存固定 0',
  owner_no     VARCHAR(50)   NOT NULL DEFAULT 'PUBLIC' COMMENT '归属对象编号，公共库存固定 PUBLIC',
  quantity     DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '库存数量，唯一由入库单确认维护',
  frozen_qty   DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '冻结量',
  reserved_qty DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '预留量',
  is_deleted   TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号，并发入库冲突时重试（M5-5-4）',
  created_by   BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_balance (material_id, warehouse_id, batch_no, owner_type, owner_id),
  KEY idx_sb_warehouse (warehouse_id),
  KEY idx_sb_material (material_id),
  KEY idx_sb_owner (owner_type, owner_id),
  CONSTRAINT ck_sb_qty CHECK (quantity >= 0 AND frozen_qty >= 0 AND reserved_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存余额 M5-3';

-- --------------------------------------------------------------
-- C-2 库存变动流水 stock_flow（M5-4）
-- 只增不改；作废入库单写入冲减记录（change_qty 为负），不删除原记录
-- --------------------------------------------------------------
DROP TABLE IF EXISTS stock_flow;
CREATE TABLE stock_flow (
  id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id  BIGINT        NOT NULL                COMMENT '物料id',
  warehouse_id BIGINT        NOT NULL                COMMENT '仓库id',
  batch_no     VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '批次号',
  owner_type   TINYINT       NOT NULL DEFAULT 1      COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_id     BIGINT        NOT NULL DEFAULT 0      COMMENT '归属对象id，公共库存固定 0',
  owner_no     VARCHAR(50)   NOT NULL DEFAULT 'PUBLIC' COMMENT '归属对象编号',
  change_type  TINYINT       NOT NULL                COMMENT '变动类型：1 入库增加、2 作废冲减',
  change_qty   DECIMAL(18,4) NOT NULL                COMMENT '变动数量，入库为正、作废冲减为负',
  before_qty   DECIMAL(18,4) NOT NULL                COMMENT '变动前数量',
  after_qty    DECIMAL(18,4) NOT NULL                COMMENT '变动后数量',
  source_type  TINYINT       NOT NULL DEFAULT 1      COMMENT '来源单据类型：1 采购入库单',
  source_id    BIGINT        NOT NULL                COMMENT '来源单据id',
  source_no    VARCHAR(50)   NOT NULL                COMMENT '来源单据号，冗余',
  operator_id  BIGINT        NOT NULL                COMMENT '操作人id',
  flow_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  is_deleted   TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除；流水不做删除',
  version      INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by   BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_sf_material_time (material_id, flow_time),
  KEY idx_sf_warehouse_time (warehouse_id, flow_time),
  KEY idx_sf_source (source_type, source_id),
  KEY idx_sf_batch (batch_no),
  KEY idx_sf_owner (owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存变动流水 M5-4';
