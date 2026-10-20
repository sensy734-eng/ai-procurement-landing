-- ==============================================================
-- 文件：06_schema_f_platform.sql
-- 域：F 平台权限（8 张表，含 sys_session）  对应模块：M11
-- 权限点按动作拆而不按菜单拆，编码规范 模块:对象:动作
-- 智能相关接口全部只读，权限点表中不存在智能接口的写操作（M9-5-4）
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- F-1 用户 sys_user（M11-1、M11-2）
-- 部门以 dict_item 表达（type_code=dept），不另建部门表
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username         VARCHAR(50)  NOT NULL                COMMENT '登录账号，全系统唯一',
  password_hash    VARCHAR(100) NOT NULL                COMMENT '密码哈希，BCrypt，不存明文',
  real_name        VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
  dept_id          BIGINT       NOT NULL                COMMENT '所属部门，关联 dict_item.id（字典类型 dept）',
  phone            VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
  email            VARCHAR(100) DEFAULT NULL            COMMENT '电子邮箱',
  status           TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态：1 启用、2 停用；停用后不可登录（M11-2-3）',
  pwd_update_time  DATETIME     DEFAULT NULL            COMMENT '密码最后修改时间（M11-1-4）',
  pwd_need_change  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否要求登录后修改密码：0 否、1 是，管理员重置后置 1（M11-2-4）',
  last_login_time  DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  last_login_ip    VARCHAR(50)  DEFAULT NULL            COMMENT '最后登录IP',
  remark           VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version          INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by       BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by       BIGINT       DEFAULT NULL            COMMENT '最后修改人id',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_user_dept (dept_id),
  KEY idx_user_status (status),
  KEY idx_user_name (real_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户 M11';

-- --------------------------------------------------------------
-- F-2 角色 sys_role（M11-2-5）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code  VARCHAR(50)  NOT NULL                COMMENT '角色编码，全系统唯一，如 buyer、buyer_manager、warehouse_keeper、admin',
  role_name  VARCHAR(50)  NOT NULL                COMMENT '角色名称',
  data_scope_all TINYINT   NOT NULL DEFAULT 0      COMMENT '是否全量数据范围：0 否、1 是；系统管理员为 1',
  is_system  TINYINT       NOT NULL DEFAULT 0      COMMENT '是否内置角色：0 否、1 是；内置角色不可删除',
  status     TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用、2 停用',
  remark     VARCHAR(200)  DEFAULT NULL            COMMENT '备注',
  is_deleted TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version    INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT        DEFAULT NULL            COMMENT '创建人id',
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT        DEFAULT NULL            COMMENT '最后修改人id',
  updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色 M11-2-5';

-- --------------------------------------------------------------
-- F-3 权限点 sys_permission（M11-3-1）
-- action_type 取值：list、view、create、update、delete、submit、approve、
-- reject、withdraw、close、void、reaudit、export、import、config、query、audit
-- 智能模块只允许 list、view、query、audit 四类只读动作
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  perm_code   VARCHAR(80)  NOT NULL                COMMENT '权限点编码，全系统唯一，格式 模块:对象:动作',
  perm_name   VARCHAR(100) NOT NULL                COMMENT '权限点名称',
  module_code VARCHAR(20)  NOT NULL                COMMENT '所属模块编号，如 M2、M11',
  action_type VARCHAR(20)  NOT NULL                COMMENT '动作类型：list、view、create、update、delete、submit、approve、reject、withdraw、close、void、reaudit、export、import、config、query、audit',
  sort_no     INT          NOT NULL DEFAULT 0      COMMENT '排序号，授权界面展示用',
  remark      VARCHAR(200) DEFAULT NULL            COMMENT '说明',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (perm_code),
  KEY idx_perm_module (module_code, sort_no),
  KEY idx_perm_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限点 M11-3-1';

-- --------------------------------------------------------------
-- F-4 角色权限关联 sys_role_permission（M11-3-2）
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
  id      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id BIGINT   NOT NULL                COMMENT '角色id',
  perm_id BIGINT   NOT NULL                COMMENT '权限点id',
  is_deleted TINYINT NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 未删除、1 已删除',
  version INT      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT DEFAULT NULL           COMMENT '创建人id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, perm_id),
  KEY idx_rp_perm (perm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色权限关联 M11-3-2';

-- --------------------------------------------------------------
-- F-5 用户角色关联 sys_user_role（M11-2-6）
-- 支持一个用户多角色
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT   NOT NULL                COMMENT '用户id',
  role_id BIGINT   NOT NULL                COMMENT '角色id',
  is_deleted TINYINT NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 未删除、1 已删除',
  version INT      NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by BIGINT DEFAULT NULL           COMMENT '创建人id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_ur_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联 M11-2-6';

-- --------------------------------------------------------------
-- F-6 数据范围授权 sys_data_scope（M11-3-3）
-- 三个维度可组合；scope_value 按维度存放 warehouse_id、dept_id 或 owner_no
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_data_scope;
CREATE TABLE sys_data_scope (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  subject_type   TINYINT      NOT NULL                COMMENT '授权主体：1 用户、2 角色',
  subject_id     BIGINT       NOT NULL                COMMENT '主体id',
  scope_dimension TINYINT     NOT NULL                COMMENT '范围维度：1 仓库、2 部门、3 归属对象',
  scope_value    VARCHAR(100) NOT NULL                COMMENT '范围值：维度为仓库存 warehouse.id，为部门存 dict_item.id，为归属对象存 owner_no',
  remark         VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT       DEFAULT NULL            COMMENT '最后修改人id',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_data_scope (subject_type, subject_id, scope_dimension, scope_value),
  KEY idx_scope_subject (subject_type, subject_id),
  KEY idx_scope_dimension (scope_dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据范围授权 M11-3-3';

-- --------------------------------------------------------------
-- F-7 操作日志 sys_operation_log（M11-4-4）
-- 用户名冗余存放，用户改名或删除后日志仍可读
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT       DEFAULT NULL            COMMENT '操作人id',
  username    VARCHAR(50)  DEFAULT NULL            COMMENT '操作人账号，冗余',
  real_name   VARCHAR(50)  DEFAULT NULL            COMMENT '操作人姓名，冗余',
  module_code VARCHAR(20)  NOT NULL                COMMENT '模块编号，如 M2',
  action      VARCHAR(50)  NOT NULL                COMMENT '动作，如 create、approve、reaudit',
  target_type VARCHAR(50)  DEFAULT NULL            COMMENT '对象类型，如 purchase_order',
  target_id   BIGINT       DEFAULT NULL            COMMENT '对象id',
  target_no   VARCHAR(50)  DEFAULT NULL            COMMENT '对象编号，如订单号',
  detail      TEXT         DEFAULT NULL            COMMENT '详情，修改前后值等',
  result_code VARCHAR(30)  DEFAULT NULL            COMMENT '结果码',
  ip          VARCHAR(50)  DEFAULT NULL            COMMENT '来源IP',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_log_user_time (user_id, created_at),
  KEY idx_log_module_time (module_code, created_at),
  KEY idx_log_target (target_type, target_id),
  KEY idx_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志 M11-4-4';

-- --------------------------------------------------------------
-- F-8 会话 sys_session（M11-1-1、M11-1-2、M11-1-3）
-- 不引 Redis，会话落库，重启不丢，可管可查
-- 本表为规划书 5.2 表清单漏列的补齐项，使总表数达到 49
-- --------------------------------------------------------------
DROP TABLE IF EXISTS sys_session;
CREATE TABLE sys_session (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  session_token  VARCHAR(64)  NOT NULL                COMMENT '会话令牌，随机生成，全局唯一',
  user_id        BIGINT       NOT NULL                COMMENT '用户id',
  login_ip       VARCHAR(50)  DEFAULT NULL            COMMENT '登录IP',
  user_agent     VARCHAR(300) DEFAULT NULL            COMMENT '客户端标识',
  login_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  last_active_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  expire_at      DATETIME     NOT NULL                COMMENT '过期时间，超时后要求重新登录',
  logout_at      DATETIME     DEFAULT NULL            COMMENT '退出时间',
  logout_type    TINYINT      DEFAULT NULL            COMMENT '退出方式：1 主动退出、2 超时失效、3 管理员强制下线',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '会话状态：1 有效、2 已退出、3 已过期',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_token (session_token),
  KEY idx_session_user (user_id, status),
  KEY idx_session_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话 M11-1';
