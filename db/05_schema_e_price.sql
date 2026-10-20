-- ==============================================================
-- 文件：05_schema_e_price.sql
-- 域：E 价格（3 张表）  对应模块：M6、M11-4
-- 约束：历史成交价不等于当前供应商报价（R6）；
--       统计口径由 sys_param 固化，快照由任务刷新，不在查询链路实时计算
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- E-1 物料价格参考 price_reference（M6-1-1）
-- 注意：公共字段 version 是乐观锁，价格历史版本另用 price_version 表达，
--       避免与规划书 5.3.10 中的「version 版本号」同名冲突
-- --------------------------------------------------------------
DROP TABLE IF EXISTS price_reference;
CREATE TABLE price_reference (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id    BIGINT        NOT NULL                COMMENT '物料id',
  ref_price      DECIMAL(18,4) NOT NULL                COMMENT '参考价，手工设定，不是当前供应商报价',
  effective_date DATE          NOT NULL                COMMENT '生效日期',
  price_version  INT           NOT NULL DEFAULT 1      COMMENT '价格版本号，同一物料内递增，支持历史版本',
  source_type    TINYINT       NOT NULL DEFAULT 1      COMMENT '来源：1 手工设定、2 批量导入',
  status         TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 生效、2 停用',
  remark         VARCHAR(300)  DEFAULT NULL            COMMENT '备注',
  is_deleted     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT        DEFAULT NULL            COMMENT '最后修改人id',
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_price_material_version (material_id, price_version),
  KEY idx_price_material_eff (material_id, effective_date),
  KEY idx_price_status (status),
  CONSTRAINT ck_price_ref CHECK (ref_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料价格参考 M6-1-1';

-- --------------------------------------------------------------
-- E-2 成交价统计快照 price_stat_snapshot（M6-1-2、M6-1-3）
-- supplier_id=0 表示全部供应商口径；按供应商统计时存具体供应商id
-- --------------------------------------------------------------
DROP TABLE IF EXISTS price_stat_snapshot;
CREATE TABLE price_stat_snapshot (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id    BIGINT        NOT NULL                COMMENT '物料id',
  supplier_id    BIGINT        NOT NULL DEFAULT 0      COMMENT '供应商id，0 表示全部供应商',
  stat_type      TINYINT       NOT NULL                COMMENT '统计类型：1 历史采购均价、2 最近一次成交价；3 最高价、4 最低价为一版不做的预留值',
  stat_value     DECIMAL(18,4) NOT NULL                COMMENT '统计值',
  stat_window    VARCHAR(50)   NOT NULL                COMMENT '统计窗口，如 LAST_5_DEALS、LAST_180_DAYS',
  sample_count   INT           NOT NULL DEFAULT 0      COMMENT '样本数量',
  source_bill_no VARCHAR(50)   DEFAULT NULL            COMMENT '最近成交价对应的单据号（M6-4-2 溯源）',
  calc_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照计算时间',
  is_deleted     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot (material_id, supplier_id, stat_type, stat_window),
  KEY idx_snapshot_material (material_id, stat_type),
  KEY idx_snapshot_calc (calc_time),
  CONSTRAINT ck_snapshot_value CHECK (stat_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成交价统计快照 M6-1-2/M6-1-3';

-- --------------------------------------------------------------
-- E-3 系统参数 sys_param（M6-2-3、M6-1-5、M11-4-1 ~ M11-4-3）
-- 口径固化于此，一旦配置不得随查询变化
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_param;
CREATE TABLE sys_param (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  param_code  VARCHAR(80)  NOT NULL                COMMENT '参数编码，全局唯一',
  param_name  VARCHAR(100) NOT NULL                COMMENT '参数名称',
  param_value VARCHAR(500) DEFAULT NULL            COMMENT '参数值，统一以字符串存放，按 param_type 解释',
  param_type  VARCHAR(20)  NOT NULL DEFAULT 'string' COMMENT '值类型：string、int、decimal、boolean、json',
  param_group VARCHAR(50)  NOT NULL DEFAULT 'common' COMMENT '参数分组：price、stock、bill_no、kb、ai、common',
  editable    TINYINT      NOT NULL DEFAULT 1      COMMENT '是否允许界面修改：0 否、1 是',
  remark      VARCHAR(300) DEFAULT NULL            COMMENT '说明，含口径定义',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by  BIGINT       DEFAULT NULL            COMMENT '最后修改人id',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_param_code (param_code),
  KEY idx_param_group (param_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统参数 M6/M11-4';
