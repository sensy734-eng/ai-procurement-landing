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
-- 10 交付量不超订单量，应返回 0 行
--    M3 交付登记 + M5 确认回写两处都写 delivered_qty，
--    任何一处漏校验都会让它越过 quantity
-- --------------------------------------------------------------
SELECT '10 交付量不超订单量' AS check_item, COUNT(*) AS violation_count
FROM purchase_order_item
WHERE is_deleted = 0
  AND (delivered_qty < 0 OR delivered_qty > quantity + 0.0001);

-- --------------------------------------------------------------
-- 11 入库累计不超订单量，应返回 0 行（M4/M5 双配额）
--    已作废的入库单不占配额，所以按 status <> 3 汇总
-- --------------------------------------------------------------
SELECT '11 入库累计不超订单量' AS check_item, COUNT(*) AS violation_count
FROM (SELECT i.order_item_id, SUM(i.quantity) AS inbound_sum
      FROM inbound_item i
      JOIN inbound_order o ON o.id = i.inbound_id
      WHERE i.is_deleted = 0 AND o.is_deleted = 0 AND o.status <> 3
        AND i.order_item_id IS NOT NULL
      GROUP BY i.order_item_id) t
JOIN purchase_order_item oi ON oi.id = t.order_item_id
WHERE t.inbound_sum > oi.quantity + 0.0001;

-- --------------------------------------------------------------
-- 12 冻结量与预留量不超库存量，应返回 0 行
--    可用量 = 数量 − 冻结 − 预留（M5 现算口径），三者关系颠倒会让可用量为负
-- --------------------------------------------------------------
SELECT '12 冻结预留不超库存量' AS check_item, COUNT(*) AS violation_count
FROM stock_balance
WHERE is_deleted = 0
  AND frozen_qty + reserved_qty > quantity + 0.0001;

-- --------------------------------------------------------------
-- 13 参考价生效版本唯一，应返回 0 行（M6 版本链）
--    同一物料在「今天」最多只能有一个已生效版本：
--    立即生效的新版本会停用旧生效版，未来预售版与旧版并存（生效日未到，不计入）
-- --------------------------------------------------------------
SELECT '13 参考价生效版本唯一' AS check_item, COUNT(*) AS violation_count
FROM (SELECT material_id
      FROM price_reference
      WHERE is_deleted = 0 AND status = 1 AND effective_date <= CURDATE()
      GROUP BY material_id
      HAVING COUNT(*) > 1) t;

-- --------------------------------------------------------------
-- 14 关键表数据量
-- --------------------------------------------------------------
SELECT '14 关键表数据量' AS check_item;
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
-- 15 审批节点配置核对，应看到申请单或签 1 条、订单会签 1 条
-- --------------------------------------------------------------
SELECT bill_type, node_seq, node_name, approver_type, role_id, sign_type, status
FROM approval_flow_node ORDER BY bill_type, node_seq;

-- --------------------------------------------------------------
-- 16 三状态组合样本 + 五个状态桶分布（M12-3-1 图表口径的取证样本）
-- --------------------------------------------------------------
SELECT order_no,
       lifecycle_status AS 生命周期,
       audit_status     AS 审批结果,
       fulfill_status   AS 履约状态
FROM purchase_order ORDER BY id;

-- --------------------------------------------------------------
-- 17 状态分布五桶计数（与 M12-3-1 图表逐桶核对）
--    五桶口径：待审批 / 待交付 / 部分交付 / 已完成 / 已关闭
--    lifecycle IN (3,4) 且 audit = 2 才算已成交，草稿与已作废不计入
-- --------------------------------------------------------------
SELECT CASE
         WHEN lifecycle_status = 2 THEN '1 待审批'
         WHEN lifecycle_status IN (3, 4) AND audit_status = 2 THEN
           -- 履约状态口径（02_schema_b_bill.sql 注释）：1 待交付、2 部分交付、
           -- 3 全部交付、4 已关闭；已关闭优先按生命周期判定，避免与履约 4 混淆
           CASE
             WHEN lifecycle_status = 4 OR fulfill_status = 4 THEN '5 已关闭'
             WHEN fulfill_status = 3 THEN '4 已完成'
             WHEN fulfill_status = 2 THEN '3 部分交付'
             ELSE '2 待交付'
           END
         ELSE '0 不计入（草稿/已作废/其他）'
       END AS bucket,
       COUNT(*) AS order_count,
       SUM(total_amount) AS amount
FROM purchase_order
WHERE is_deleted = 0
GROUP BY bucket
ORDER BY bucket;

-- --------------------------------------------------------------
-- 18 库存不足物料清单（与 M12-3-4 图表逐行核对）
--    不足 = 可用量 < 安全库存下限 且 下限 > 0；缺口 = 下限 − 可用量
-- --------------------------------------------------------------
SELECT m.material_no,
       m.material_name,
       SUM(b.quantity)                                   AS quantity,
       SUM(b.quantity - b.frozen_qty - b.reserved_qty)    AS available_qty,
       m.safety_stock,
       SUM(b.quantity - b.frozen_qty - b.reserved_qty) * -1 + m.safety_stock AS gap_qty
FROM stock_balance b
JOIN material m ON m.id = b.material_id
WHERE b.is_deleted = 0 AND m.is_deleted = 0 AND m.status = 1 AND m.safety_stock > 0
GROUP BY m.id, m.material_no, m.material_name, m.safety_stock
HAVING available_qty < m.safety_stock
ORDER BY gap_qty DESC;
