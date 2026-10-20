-- ==============================================================
-- 文件：99_verify.sql
-- 作用：初始化结果的自动校验
-- 用法：全部脚本执行完毕后运行，逐项核对结构、数据量与口径
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- 1 表数量，应为 49
-- --------------------------------------------------------------
SELECT '1 表数量' AS check_item,
       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'procurement_db') AS actual,
       49 AS expected;

-- --------------------------------------------------------------
-- 2 各域表数量，应为 A13 B10 C2 D2 E3 F8 G5 H6
-- --------------------------------------------------------------
SELECT CASE
         WHEN table_name = 'sys_param' THEN 'E'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) IN ('material', 'supplier', 'warehouse', 'dict', 'owner') THEN 'A'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) IN ('purchase', 'order', 'delivery', 'arrival', 'inbound') THEN 'B'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'stock' THEN 'C'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'approval' THEN 'D'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'price' THEN 'E'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'sys' THEN 'F'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'kb' THEN 'G'
         WHEN SUBSTRING_INDEX(table_name, '_', 1) = 'ai' THEN 'H'
         ELSE 'UNKNOWN' END AS domain,
       COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'procurement_db'
GROUP BY domain
ORDER BY domain;

-- --------------------------------------------------------------
-- 3 权限点数量与按模块分布
-- --------------------------------------------------------------
SELECT '3 权限点总数' AS check_item, COUNT(*) AS actual, 156 AS expected FROM sys_permission;

SELECT module_code, COUNT(*) AS perm_count
FROM sys_permission GROUP BY module_code ORDER BY module_code;

-- --------------------------------------------------------------
-- 4 智能模块只读校验，应返回 0 行
-- --------------------------------------------------------------
SELECT '4 智能接口只读校验' AS check_item, COUNT(*) AS violation_count
FROM sys_permission
WHERE module_code IN ('M7', 'M8', 'M9')
  AND action_type NOT IN ('list', 'view', 'query', 'audit');

-- --------------------------------------------------------------
-- 5 角色授权数量
-- --------------------------------------------------------------
SELECT r.role_name, COUNT(rp.perm_id) AS granted_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
GROUP BY r.id, r.role_name ORDER BY r.id;

-- --------------------------------------------------------------
-- 6 三状态非法组合校验，应返回 0 行
--    草稿却审批通过、已审核却未审核、作废却有履约进度
-- --------------------------------------------------------------
SELECT '6 申请单状态组合' AS check_item, COUNT(*) AS violation_count
FROM purchase_request
WHERE (lifecycle_status = 1 AND audit_status = 2)
   OR (lifecycle_status = 3 AND audit_status = 1);

SELECT '6 订单状态组合' AS check_item, COUNT(*) AS violation_count
FROM purchase_order
WHERE (lifecycle_status = 1 AND audit_status = 2)
   OR (lifecycle_status = 3 AND audit_status = 1)
   OR (lifecycle_status = 5 AND fulfill_status IN (2, 3));

-- --------------------------------------------------------------
-- 7 验收数量守恒校验，应返回 0 行（R12）
-- --------------------------------------------------------------
SELECT '7 验收数量守恒' AS check_item, COUNT(*) AS violation_count
FROM arrival_inspection_item
WHERE arrival_qty <> qualified_qty + unqualified_qty;

-- --------------------------------------------------------------
-- 8 订单金额一致性校验，应返回 0 行
-- --------------------------------------------------------------
SELECT '8 订单金额合计' AS check_item, COUNT(*) AS violation_count
FROM purchase_order o
JOIN (SELECT order_id,
             SUM(amount) AS amount_sum,
             SUM(no_tax_amount) AS no_tax_sum,
             SUM(quantity) AS qty_sum
      FROM purchase_order_item
      WHERE is_deleted = 0
      GROUP BY order_id) i ON i.order_id = o.id
WHERE ABS(o.total_amount - i.amount_sum) > 0.01
   OR ABS(o.no_tax_amount - i.no_tax_sum) > 0.01
   OR ABS(o.total_qty - i.qty_sum) > 0.0001;

-- --------------------------------------------------------------
-- 9 库存余额与流水累计对账，应返回 0 行（M5-4-4）
-- --------------------------------------------------------------
SELECT '9 余额与流水对账' AS check_item, COUNT(*) AS violation_count
FROM stock_balance b
LEFT JOIN (SELECT material_id, warehouse_id, batch_no, owner_type, owner_id,
                  SUM(change_qty) AS flow_sum
           FROM stock_flow
           WHERE is_deleted = 0
           GROUP BY material_id, warehouse_id, batch_no, owner_type, owner_id) f
       ON f.material_id = b.material_id AND f.warehouse_id = b.warehouse_id
      AND f.batch_no = b.batch_no AND f.owner_type = b.owner_type AND f.owner_id = b.owner_id
WHERE b.is_deleted = 0
  AND ABS(b.quantity - COALESCE(f.flow_sum, 0)) > 0.0001;

-- --------------------------------------------------------------
-- 10 关键表数据量
-- --------------------------------------------------------------
SELECT '10 关键表数据量' AS check_item;
SELECT 'material' AS t, COUNT(*) AS c FROM material
UNION ALL SELECT 'supplier', COUNT(*) FROM supplier
UNION ALL SELECT 'warehouse', COUNT(*) FROM warehouse
UNION ALL SELECT 'purchase_request', COUNT(*) FROM purchase_request
UNION ALL SELECT 'purchase_request_item', COUNT(*) FROM purchase_request_item
UNION ALL SELECT 'purchase_order', COUNT(*) FROM purchase_order
UNION ALL SELECT 'purchase_order_item', COUNT(*) FROM purchase_order_item
UNION ALL SELECT 'arrival_inspection', COUNT(*) FROM arrival_inspection
UNION ALL SELECT 'inbound_order', COUNT(*) FROM inbound_order
UNION ALL SELECT 'stock_balance', COUNT(*) FROM stock_balance
UNION ALL SELECT 'stock_flow', COUNT(*) FROM stock_flow
UNION ALL SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'sys_data_scope', COUNT(*) FROM sys_data_scope
UNION ALL SELECT 'approval_flow_node', COUNT(*) FROM approval_flow_node
UNION ALL SELECT 'approval_task', COUNT(*) FROM approval_task
UNION ALL SELECT 'ai_intent', COUNT(*) FROM ai_intent
UNION ALL SELECT 'ai_intent_param', COUNT(*) FROM ai_intent_param
UNION ALL SELECT 'ai_entity_alias', COUNT(*) FROM ai_entity_alias
UNION ALL SELECT 'sys_param', COUNT(*) FROM sys_param
UNION ALL SELECT 'kb_document', COUNT(*) FROM kb_document
UNION ALL SELECT 'kb_chunk', COUNT(*) FROM kb_chunk;

-- --------------------------------------------------------------
-- 11 审批节点配置核对，应看到申请单或签 1 条、订单会签 1 条
-- --------------------------------------------------------------
SELECT bill_type, node_seq, node_name, approver_type, role_id, sign_type, status
FROM approval_flow_node ORDER BY bill_type, node_seq;

-- --------------------------------------------------------------
-- 12 三状态组合样本，用于论文举例取证
-- --------------------------------------------------------------
SELECT order_no,
       lifecycle_status AS 生命周期,
       audit_status     AS 审批结果,
       fulfill_status   AS 履约状态
FROM purchase_order ORDER BY id;
