-- ==============================================================
-- 文件：08_schema_h_ai.sql
-- 域：H 智能助手（6 张表）  对应模块：M7、M8、M9
-- 受控边界：模型不访问业务数据库、不执行生成语句；
--           智能接口全部只读；每次请求写一条审计（含被拒参数）
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- H-1 智能会话 ai_session（M7-1-2、M7-1-3）
-- context_json 保存上一轮意图与实体，支撑「它的供应商是谁」这类指代
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_session;
CREATE TABLE ai_session (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id          BIGINT       NOT NULL                COMMENT '用户id',
  title            VARCHAR(100) NOT NULL DEFAULT '新会话' COMMENT '会话标题，取首条问句前若干字',
  message_count    INT          NOT NULL DEFAULT 0      COMMENT '消息条数',
  last_intent_code VARCHAR(50)  DEFAULT NULL            COMMENT '上一轮意图编码',
  context_json     VARCHAR(1000) DEFAULT NULL           COMMENT '上一轮实体与参数，JSON，用于多轮指代',
  last_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  is_deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version          INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ai_session_user (user_id, last_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='智能会话 M7-1-2';

-- --------------------------------------------------------------
-- H-2 会话消息 ai_message（M7-1、M7-4）
-- 一条用户问句与一条助手回答各存一行；
-- 结构化结果、引用文档、意图参数一并留存，便于复盘与审计
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_id  BIGINT       NOT NULL                COMMENT '会话id',
  role        TINYINT      NOT NULL                COMMENT '角色：1 用户、2 助手',
  content     MEDIUMTEXT   NOT NULL                COMMENT '消息正文',
  msg_type    TINYINT      NOT NULL DEFAULT 6      COMMENT '消息类型：1 知识类、2 教程类、3 数据类、4 澄清、5 拒答、6 纯文本',
  intent_code VARCHAR(50)  DEFAULT NULL            COMMENT '意图编码',
  params_json VARCHAR(1000) DEFAULT NULL           COMMENT '提取并校验通过的参数，JSON',
  result_json MEDIUMTEXT   DEFAULT NULL            COMMENT '结构化结果，JSON，表格数据存于此',
  ref_doc_ids VARCHAR(500) DEFAULT NULL            COMMENT '引用文档id，逗号分隔',
  result_code VARCHAR(30)  DEFAULT NULL            COMMENT '结果码：Success、NotFound、Ambiguous、NoPermission、NoData、Unsupported、InvalidParameter',
  elapsed_ms  INT          NOT NULL DEFAULT 0      COMMENT '处理耗时，单位毫秒',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_msg_session (session_id, id),
  KEY idx_msg_type (msg_type),
  KEY idx_msg_code (result_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话消息 M7-4';

-- --------------------------------------------------------------
-- H-3 意图白名单 ai_intent（M9-1-3）
-- 一版只做五类预设意图，每类映射到一个确定的后端查询服务，
-- 模型不参与选择表与字段
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_intent;
CREATE TABLE ai_intent (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  intent_code      VARCHAR(50)  NOT NULL                COMMENT '意图编码，全局唯一',
  intent_name      VARCHAR(50)  NOT NULL                COMMENT '意图名称',
  service_code     VARCHAR(50)  NOT NULL                COMMENT '映射的后端查询服务标识，白名单内固定值',
  question_samples VARCHAR(500) DEFAULT NULL            COMMENT '示例问句，多个以竖线分隔',
  enabled          TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用：0 否、1 是',
  sort_no          INT          NOT NULL DEFAULT 0      COMMENT '排序号',
  remark           VARCHAR(300) DEFAULT NULL            COMMENT '说明',
  is_deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version          INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_intent_code (intent_code),
  KEY idx_intent_enabled (enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='意图白名单 M9-1-3';

-- --------------------------------------------------------------
-- H-4 意图参数规格 ai_intent_param（M9-2-4）
-- 白名单校验依据：模型提取的参数不在 operator_set 与 enum_values
-- 范围内直接返回 InvalidParameter，不进入后续流程
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_intent_param;
CREATE TABLE ai_intent_param (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  intent_id    BIGINT       NOT NULL                COMMENT '所属意图id',
  param_code   VARCHAR(50)  NOT NULL                COMMENT '参数编码，如 order_no、material_id',
  param_name   VARCHAR(50)  NOT NULL                COMMENT '参数名称',
  data_type    VARCHAR(20)  NOT NULL                COMMENT '数据类型：string、long、decimal、date、boolean',
  operator_set VARCHAR(100) NOT NULL DEFAULT 'eq'   COMMENT '允许的操作符集合，逗号分隔，如 eq、like、in、gte、lte、between',
  required     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否必填：0 否、1 是；必填缺失返回澄清而非默认值',
  enum_values  VARCHAR(500) DEFAULT NULL            COMMENT '枚举值域，逗号分隔，为 NULL 表示不做枚举限制',
  sort_no      INT          NOT NULL DEFAULT 0      COMMENT '排序号',
  remark       VARCHAR(300) DEFAULT NULL            COMMENT '说明',
  is_deleted   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_intent_param (intent_id, param_code),
  KEY idx_param_intent (intent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='意图参数规格 M9-2-4';

-- --------------------------------------------------------------
-- H-5 实体别名映射 ai_entity_alias（M9-3-1）
-- 支撑口语化名称到业务主键的归一；匹配到多个候选返回 Ambiguous
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_entity_alias;
CREATE TABLE ai_entity_alias (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  entity_type TINYINT      NOT NULL                COMMENT '实体类型：1 物料、2 供应商、3 仓库',
  entity_id   BIGINT       NOT NULL                COMMENT '实体id',
  entity_no   VARCHAR(50)  NOT NULL                COMMENT '实体编号，冗余',
  alias_name  VARCHAR(100) NOT NULL                COMMENT '别名，口语化名称',
  source      TINYINT      NOT NULL DEFAULT 1      COMMENT '来源：1 手工维护、2 拒答反馈补充',
  hit_count   INT          NOT NULL DEFAULT 0      COMMENT '命中次数，用于排序候选',
  remark      VARCHAR(200) DEFAULT NULL            COMMENT '备注，如标注一词多物',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_alias (entity_type, alias_name),
  KEY idx_alias_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实体别名映射 M9-3-1';

-- --------------------------------------------------------------
-- H-6 智能查询审计 ai_query_audit（M9-5-5、M11-4-5）
-- 每次请求无论成功、拒绝还是拒答都写一条；
-- 被拒参数单独存放，这是受控二字的可验证凭据（R9）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS ai_query_audit;
CREATE TABLE ai_query_audit (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_id           BIGINT       DEFAULT NULL            COMMENT '会话id',
  user_id              BIGINT       NOT NULL                COMMENT '用户id',
  username             VARCHAR(50)  DEFAULT NULL            COMMENT '用户账号，冗余',
  question             VARCHAR(500) NOT NULL                COMMENT '原始问句',
  intent_code          VARCHAR(50)  DEFAULT NULL            COMMENT '解析出的意图编码',
  params_json          VARCHAR(1000) DEFAULT NULL           COMMENT '提取并通过校验的参数，JSON',
  rejected_params_json VARCHAR(500) DEFAULT NULL            COMMENT '被拒绝的参数及原因，JSON',
  data_scope_json      VARCHAR(500) DEFAULT NULL            COMMENT '本次注入的数据范围条件，JSON，用于证明过滤已生效',
  result_code          VARCHAR(30)  NOT NULL                COMMENT '结果码',
  row_count            INT          NOT NULL DEFAULT 0      COMMENT '返回行数',
  elapsed_ms           INT          NOT NULL DEFAULT 0      COMMENT '耗时，单位毫秒',
  ip                   VARCHAR(50)  DEFAULT NULL            COMMENT '来源IP',
  is_deleted           TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_audit_user_time (user_id, created_at),
  KEY idx_audit_intent (intent_code),
  KEY idx_audit_code (result_code),
  KEY idx_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='智能查询审计 M9-5-5';
