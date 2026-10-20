-- ==============================================================
-- 融合 AI 助手的制造企业采购管理系统  数据库脚本
-- 文件：01_schema_a_base.sql
-- 域：A 基础数据（13 张表）  对应模块：M1 基础数据管理
-- 引擎：InnoDB    字符集：utf8mb4    排序：utf8mb4_general_ci
-- 公共字段：id / is_deleted / version / created_by / created_at / updated_at
-- 执行顺序：00 -> 01 ~ 08 -> 10 ~ 13 -> 99
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- A-1 物料分类 material_category（M1-1-7）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS material_category;
CREATE TABLE material_category (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  category_no   VARCHAR(50)  NOT NULL                COMMENT '分类编码，全系统唯一',
  category_name VARCHAR(100) NOT NULL                COMMENT '分类名称',
  parent_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '上级分类id，0 表示根节点',
  level_no      TINYINT      NOT NULL DEFAULT 1      COMMENT '层级序号，从 1 开始',
  sort_no       INT          NOT NULL DEFAULT 0      COMMENT '同级排序号，升序',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  is_deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_no (category_no),
  KEY idx_category_parent (parent_id),
  KEY idx_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料分类 M1-1-7';

-- --------------------------------------------------------------
-- A-2 物料主表 material（M1-1-1 ~ M1-1-5）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS material;
CREATE TABLE material (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_no   VARCHAR(50)   NOT NULL                COMMENT '物料编码，全系统唯一，逻辑删除后不复用',
  material_name VARCHAR(100)  NOT NULL                COMMENT '物料名称',
  category_id   BIGINT        NOT NULL                COMMENT '物料分类，关联 material_category.id',
  spec          VARCHAR(200)  DEFAULT NULL            COMMENT '规格型号',
  brand         VARCHAR(100)  DEFAULT NULL            COMMENT '品牌',
  unit_id       BIGINT        NOT NULL                COMMENT '计量单位，关联 dict_item.id（字典类型 unit）',
  material_type TINYINT       NOT NULL DEFAULT 1      COMMENT '物料类型：1 原材料、2 辅料、3 外购件、4 备件',
  safety_stock  DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '预警下限，M5-5-1 库存不足判定基准',
  status        TINYINT       NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用；停用后不可在新单据选择（M1-1-4）',
  remark        VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
  is_deleted    TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_no (material_no),
  KEY idx_material_name (material_name),
  KEY idx_material_category (category_id),
  KEY idx_material_status (status),
  CONSTRAINT ck_material_safety_stock CHECK (safety_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料主表 M1-1';

-- --------------------------------------------------------------
-- A-3 物料扩展属性 material_ext_attr（M1-1-6）
-- 行业专属字段以属性名值对存放，避免主表字段膨胀
-- --------------------------------------------------------------
DROP TABLE IF EXISTS material_ext_attr;
CREATE TABLE material_ext_attr (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id BIGINT       NOT NULL                COMMENT '物料id',
  attr_name   VARCHAR(50)  NOT NULL                COMMENT '属性名',
  attr_value  VARCHAR(500) DEFAULT NULL            COMMENT '属性值',
  sort_no     INT          NOT NULL DEFAULT 0      COMMENT '排序号',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ext_material_attr (material_id, attr_name),
  KEY idx_ext_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='物料扩展属性 M1-1-6';

-- --------------------------------------------------------------
-- A-4 供应商主表 supplier（M1-2-1 ~ M1-2-3）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS supplier;
CREATE TABLE supplier (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  supplier_no  VARCHAR(50)  NOT NULL                COMMENT '供应商编码，全系统唯一',
  supplier_name VARCHAR(100) NOT NULL               COMMENT '供应商名称',
  supplier_type TINYINT     NOT NULL DEFAULT 2      COMMENT '供应商类型：1 生产厂家、2 经销商、3 服务商',
  tax_no       VARCHAR(50)  DEFAULT NULL            COMMENT '统一社会信用代码',
  address      VARCHAR(200) DEFAULT NULL            COMMENT '注册地址',
  phone        VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
  email        VARCHAR(100) DEFAULT NULL            COMMENT '电子邮箱',
  bank_name    VARCHAR(100) DEFAULT NULL            COMMENT '开户银行',
  bank_account VARCHAR(50)  DEFAULT NULL            COMMENT '银行账号',
  status       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用；停用后不可用于新订单（M1-2-3）',
  remark       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  is_deleted   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by   BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_no (supplier_no),
  KEY idx_supplier_name (supplier_name),
  KEY idx_supplier_type (supplier_type),
  KEY idx_supplier_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商主表 M1-2';

-- --------------------------------------------------------------
-- A-5 供应商联系人 supplier_contact（M1-2-4）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS supplier_contact;
CREATE TABLE supplier_contact (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  supplier_id  BIGINT       NOT NULL                COMMENT '供应商id',
  contact_name VARCHAR(50)  NOT NULL                COMMENT '联系人姓名',
  position     VARCHAR(50)  DEFAULT NULL            COMMENT '职务',
  phone        VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
  email        VARCHAR(100) DEFAULT NULL            COMMENT '电子邮箱',
  is_default   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否主联系人：0 否、1 是',
  status       TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark       VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by   BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_contact_supplier (supplier_id),
  KEY idx_contact_default (supplier_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商联系人 M1-2-4';

-- --------------------------------------------------------------
-- A-6 供应商资质附件 supplier_attachment（M1-2-5）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS supplier_attachment;
CREATE TABLE supplier_attachment (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  supplier_id BIGINT       NOT NULL                COMMENT '供应商id',
  file_name   VARCHAR(200) NOT NULL                COMMENT '文件原始名称',
  file_type   VARCHAR(20)  NOT NULL                COMMENT '文件类型：pdf、jpg、png、docx 等',
  file_size   BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小，单位字节',
  file_path   VARCHAR(300) NOT NULL                COMMENT '存储路径，相对路径，不含主机信息',
  expire_date DATE         DEFAULT NULL            COMMENT '资质有效期，到期由前端提示',
  remark      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '上传人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_attach_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商资质附件 M1-2-5';

-- --------------------------------------------------------------
-- A-7 供应商供货范围 supplier_scope（M1-2-6）
-- 订单选择物料时据此提示，不做强制拦截
-- --------------------------------------------------------------
DROP TABLE IF EXISTS supplier_scope;
CREATE TABLE supplier_scope (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  supplier_id BIGINT      NOT NULL                COMMENT '供应商id',
  scope_type  TINYINT     NOT NULL                COMMENT '范围类型：1 按物料、2 按物料分类',
  scope_id    BIGINT      NOT NULL                COMMENT '范围对象id，scope_type=1 时为 material.id，=2 时为 material_category.id',
  status      TINYINT     NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark      VARCHAR(200) DEFAULT NULL           COMMENT '备注',
  is_deleted  TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT      DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope (supplier_id, scope_type, scope_id),
  KEY idx_scope_target (scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商供货范围 M1-2-6';

-- --------------------------------------------------------------
-- A-8 仓库档案 warehouse（M1-3-1）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS warehouse;
CREATE TABLE warehouse (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  warehouse_no   VARCHAR(50)  NOT NULL                COMMENT '仓库编码，全系统唯一',
  warehouse_name VARCHAR(100) NOT NULL                COMMENT '仓库名称',
  manager_id     BIGINT       DEFAULT NULL            COMMENT '仓库负责人，关联 sys_user.id',
  address        VARCHAR(200) DEFAULT NULL            COMMENT '仓库地址',
  phone          VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouse_no (warehouse_no),
  KEY idx_warehouse_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库档案 M1-3-1';

-- --------------------------------------------------------------
-- A-9 库位 warehouse_location（M1-3-2）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS warehouse_location;
CREATE TABLE warehouse_location (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  warehouse_id  BIGINT       NOT NULL                COMMENT '所属仓库id',
  location_no   VARCHAR(50)  NOT NULL                COMMENT '库位编码，仓库内唯一',
  location_name VARCHAR(100) DEFAULT NULL            COMMENT '库位名称',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark        VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version       INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by    BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_location_no (warehouse_id, location_no),
  KEY idx_location_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库位 M1-3-2';

-- --------------------------------------------------------------
-- A-10 仓库使用人关系 warehouse_user（M1-3-3）
-- 作为数据范围授权依据，与 sys_data_scope 的仓库维度配合使用
-- --------------------------------------------------------------
DROP TABLE IF EXISTS warehouse_user;
CREATE TABLE warehouse_user (
  id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  warehouse_id BIGINT   NOT NULL                COMMENT '仓库id',
  user_id      BIGINT   NOT NULL                COMMENT '用户id',
  is_default   TINYINT  NOT NULL DEFAULT 0      COMMENT '是否默认仓库：0 否、1 是',
  is_deleted   TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by   BIGINT   DEFAULT NULL            COMMENT '创建人id',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouse_user (warehouse_id, user_id),
  KEY idx_wu_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库使用人关系 M1-3-3';

-- --------------------------------------------------------------
-- A-11 字典类型 dict_type（M1-4-1、M1-4-2）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS dict_type;
CREATE TABLE dict_type (
  id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_code VARCHAR(50)  NOT NULL                COMMENT '字典类型编码，全局唯一，如 unit、dept、settlement_type',
  type_name VARCHAR(100) NOT NULL                COMMENT '字典类型名称',
  status    TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark    VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version   INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT      DEFAULT NULL            COMMENT '创建人id',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型 M1-4-1';

-- --------------------------------------------------------------
-- A-12 字典项 dict_item（M1-4-1）
-- 部门也以字典项表达（type_code=dept），parent_id 形成部门树，
-- 以此守住 49 张表的表数口径，不另建 sys_dept 表
-- --------------------------------------------------------------
DROP TABLE IF EXISTS dict_item;
CREATE TABLE dict_item (
  id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_id   BIGINT       NOT NULL                COMMENT '字典类型id',
  item_code VARCHAR(50)  NOT NULL                COMMENT '字典项编码，同类型内唯一',
  item_name VARCHAR(100) NOT NULL                COMMENT '字典项名称',
  parent_id BIGINT       NOT NULL DEFAULT 0      COMMENT '上级字典项id，0 表示根节点，部门树使用',
  sort_no   INT          NOT NULL DEFAULT 0      COMMENT '排序号，升序',
  status    TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  ext_value VARCHAR(200) DEFAULT NULL            COMMENT '扩展值，如单据编号前缀',
  remark    VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version   INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT      DEFAULT NULL            COMMENT '创建人id',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_item_code (type_id, item_code),
  KEY idx_dict_item_type (type_id, sort_no),
  KEY idx_dict_item_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项 M1-4-1';

-- --------------------------------------------------------------
-- A-13 归属对象 owner_object（M1 / M2-2-5）
-- 归属不建外键，仅在业务层校验合法性（R11）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS owner_object;
CREATE TABLE owner_object (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_type TINYINT      NOT NULL                COMMENT '归属类型：1 公共、2 项目、3 工单',
  owner_no   VARCHAR(50)  NOT NULL                COMMENT '归属对象编号，如项目号、工单号',
  owner_name VARCHAR(100) NOT NULL                COMMENT '归属对象名称',
  status     TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态：1 启用、2 停用',
  remark     VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version    INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_owner (owner_type, owner_no),
  KEY idx_owner_type (owner_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='归属对象 M1/M2-2-5';
