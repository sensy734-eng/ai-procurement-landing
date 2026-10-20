-- ==============================================================
-- 文件：04_schema_d_approval.sql
-- 域：D 审批（2 张表）  对应模块：M11-5，被 M2-3、M3-3 调用
-- 采购专用简化审批：按单据类型配置固定节点，不做金额区间分支，
-- 不做转办与发起人自选；同一节点多人支持或签与会签
-- 审批历史由 approval_task 按 node_seq 呈现，不另建历史表
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- D-1 审批流程节点配置 approval_flow_node（M11-5-1、M11-5-2）
-- 同一 bill_type 加 node_seq 为一个节点，该节点下可配置多行审批人
-- （一行一个角色或一个具体用户），多行即多人
-- --------------------------------------------------------------
DROP TABLE IF EXISTS approval_flow_node;
CREATE TABLE approval_flow_node (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  bill_type      TINYINT      NOT NULL                COMMENT '单据类型：1 采购申请、2 采购订单',
  node_seq       SMALLINT     NOT NULL                COMMENT '节点序号，固定顺序，从 1 开始',
  node_name      VARCHAR(50)  NOT NULL                COMMENT '节点名称，如采购主管审批',
  approver_type  TINYINT      NOT NULL                COMMENT '审批人指定方式：1 按角色、2 按具体用户',
  role_id        BIGINT       DEFAULT NULL            COMMENT '角色id，approver_type=1 时必填',
  user_id        BIGINT       DEFAULT NULL            COMMENT '用户id，approver_type=2 时必填',
  sign_type      TINYINT      NOT NULL DEFAULT 1      COMMENT '会签方式：1 或签（任一通过即推进）、2 会签（全部通过才推进，任一驳回则整单驳回）',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '配置状态：1 启用、2 停用',
  remark         VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version        INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by     BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by     BIGINT       DEFAULT NULL            COMMENT '最后修改人id',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_node (bill_type, node_seq, approver_type, role_id, user_id),
  KEY idx_flow_bill_seq (bill_type, node_seq, status),
  KEY idx_flow_role (role_id),
  KEY idx_flow_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流程节点配置 M11-5';

-- --------------------------------------------------------------
-- D-2 审批任务 approval_task（M11-5-4、M11-5-5、M2-3-7、M3-3-7）
-- 一个节点按审批人配置生成一条或多条任务
-- 处理动作在同一事务内完成：任务状态更新、节点推进判断、
-- 单据审批结果回写、操作日志写入
-- --------------------------------------------------------------
DROP TABLE IF EXISTS approval_task;
CREATE TABLE approval_task (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_no     VARCHAR(50)  NOT NULL                COMMENT '任务编号，全系统唯一',
  bill_type   TINYINT      NOT NULL                COMMENT '单据类型：1 采购申请、2 采购订单',
  bill_id     BIGINT       NOT NULL                COMMENT '单据id',
  bill_no     VARCHAR(50)  NOT NULL                COMMENT '单据号，冗余',
  node_seq    SMALLINT     NOT NULL                COMMENT '节点序号',
  node_name   VARCHAR(50)  NOT NULL                COMMENT '节点名称，冗余，单据归档后仍可读',
  approver_id BIGINT       NOT NULL                COMMENT '审批人id',
  sign_type   TINYINT      NOT NULL DEFAULT 1      COMMENT '会签方式：1 或签、2 会签',
  task_status TINYINT      NOT NULL DEFAULT 1      COMMENT '任务状态：1 待处理、2 已通过、3 已驳回、4 已失效（撤回或整单驳回导致）',
  opinion     VARCHAR(500) DEFAULT NULL            COMMENT '审批意见，驳回时必填',
  handle_time DATETIME     DEFAULT NULL            COMMENT '处理时间',
  is_deleted  TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_by  BIGINT       DEFAULT NULL            COMMENT '创建人id',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_task_bill (bill_type, bill_id, node_seq),
  KEY idx_task_approver (approver_id, task_status),
  KEY idx_task_bill_no (bill_no),
  KEY idx_task_status (task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批任务 M11-5';
