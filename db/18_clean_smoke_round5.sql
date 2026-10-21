-- ==============================================================
-- 文件：18_clean_smoke_round5.sql
-- 作用：清理轮次 5 冒烟测试产生的 M3 数据，并把演示数据复原到初始状态。
-- 轮次 5 冒烟（smoke_round5.py）之外，轮次 4 之后调试期间的临时数据一并清除。
--
-- 清理口径：
--   1. 先删子表（变更历史、交付流水、审批任务、订单明细），再删主表（订单、申请单）；
--   2. 演示数据固定为申请单 1~3、申请明细 1~5、订单 1~2、订单明细 1~3、
--      审批任务 1~4、交付流水 1 条，一律按 id 边界删除，不用备注匹配；
--   3. 审批流程配置按 11_init_platform.sql 种子复原（轮次 4 后被手工改为
--      4 节点，恢复为：采购申请=单节点或签、采购订单=单节点会签两名主管）；
--   4. 轮次 3/4 遗留的 smk 前缀物料与供应商一并清除，演示规模回到
--      物料 8、供应商 4（13_init_demo.sql 初始值，SUP-001~004）；
--   5. 操作日志按审计要求保留，不清理（与轮次 2/3/4 同一原则）。
--
-- 注意：跑本脚本前确认没有想保留的手工数据——按 id 边界删除会清掉
--       演示单之外的全部订单与申请单。
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- 一、审批流程配置复原（种子两行：申请或签、订单会签）
DELETE FROM approval_flow_node;
INSERT INTO approval_flow_node (bill_type, node_seq, node_name, approver_type, role_id, user_id, sign_type, status, remark) VALUES
  (1, 1, '采购主管审批', 1, 2, NULL, 1, 1, '采购申请：或签'),
  (2, 1, '采购主管会签', 1, 2, NULL, 2, 1, '采购订单：会签');

-- 二、M3 冒烟数据（演示订单 1、2 与交付流水 1 条之外全部清除）
DELETE FROM order_change_log
WHERE order_id > 2;
DELETE FROM delivery_progress
WHERE order_id > 1;
DELETE FROM approval_task
WHERE id > 4;
DELETE FROM purchase_order_item
WHERE order_id > 2;
DELETE FROM purchase_order
WHERE id > 2;

-- 三、M2 冒烟数据（演示申请单 1~3、明细 1~5 之外全部清除）
DELETE FROM purchase_request_item
WHERE request_id > 3;
DELETE FROM purchase_request
WHERE id > 3;

-- 四、演示申请单 2、3 状态复原（13_init_demo.sql 初始值，同 17_clean 口径）
UPDATE purchase_request
SET lifecycle_status = 1, audit_status = 1,
    require_date = '2026-10-25', total_amount = 7398.00,
    close_reason = NULL,
    remark = '演示数据：草稿待提交',
    updated_by = 3, updated_at = CURRENT_TIMESTAMP
WHERE id = 2;

UPDATE purchase_request
SET lifecycle_status = 1, audit_status = 3,
    require_date = '2026-10-22', total_amount = 1600.00,
    close_reason = NULL,
    remark = '演示数据：被驳回，生命周期回落草稿、审批结果驳回',
    updated_by = 2, updated_at = CURRENT_TIMESTAMP
WHERE id = 3;

-- 五、申请单 3 的明细行复原（已转数量清零、行状态回待转）
UPDATE purchase_request_item
SET transferred_qty = 0.0000, item_status = 1
WHERE id = 5;

-- 六、M1 冒烟残留（轮次 3/4/5 的 smk 前缀物料与供应商）
DELETE FROM material_ext_attr
WHERE material_id IN (SELECT id FROM material WHERE material_no LIKE 'smk%');
DELETE FROM material
WHERE material_no LIKE 'smk%';
DELETE FROM supplier_scope
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk%');
DELETE FROM supplier_contact
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk%');
DELETE FROM supplier_attachment
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk%');
DELETE FROM supplier
WHERE supplier_no LIKE 'smk%';

-- 七、验证：应回到演示规模（3 申请单 / 5 申请行 / 2 订单 / 3 订单行 / 4 任务 /
--      1 条交付流水 / 0 条变更历史 / 8 物料 / 4 供应商 / 2 审批节点）
SELECT 'purchase_request' AS tbl, COUNT(*) AS cnt FROM purchase_request
UNION ALL SELECT 'purchase_request_item', COUNT(*) FROM purchase_request_item
UNION ALL SELECT 'purchase_order', COUNT(*) FROM purchase_order
UNION ALL SELECT 'purchase_order_item', COUNT(*) FROM purchase_order_item
UNION ALL SELECT 'approval_task', COUNT(*) FROM approval_task
UNION ALL SELECT 'approval_flow_node', COUNT(*) FROM approval_flow_node
UNION ALL SELECT 'delivery_progress', COUNT(*) FROM delivery_progress
UNION ALL SELECT 'order_change_log', COUNT(*) FROM order_change_log
UNION ALL SELECT 'material', COUNT(*) FROM material
UNION ALL SELECT 'supplier', COUNT(*) FROM supplier;
