-- ============================================================
-- 轮次 11 冒烟后清理（M12 管理后台与数据可视化）
--
-- 与前面几轮不同：M12 的 8 个端点全部只读，冒烟不写任何业务表，
-- 所以这里没有"删测试单据"的动作——删了反而会把演示数据删掉。
--
-- 本脚本做三件事：
-- 1. 复原演示待审批单的会签任务（冒烟只读，但如果演示中点了通过/驳回，任务会变成已处理）；
-- 2. 复原演示订单的三状态（防止演示时误操作改变了状态分布）；
-- 3. 输出基线核对表，全部应为 0 或预期值，供跑完冒烟后一眼确认。
--
-- 如需完全重造运营演示数据，重跑 db/24_init_ops_demo.sql（脚本幂等，先删后插）。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 演示待审批单的两条会签任务复原为待处理
UPDATE approval_task
SET task_status = 1, opinion = NULL, handle_time = NULL, updated_at = NOW()
WHERE bill_no = 'CG-DD-20261027-0001' AND is_deleted = 0;

-- 2) 演示订单三状态复原（只针对本脚本造的演示单，不碰其他单据）
UPDATE purchase_order
SET lifecycle_status = 4, audit_status = 2, fulfill_status = 4, close_reason = '合同执行完毕，余量关闭'
WHERE order_no = 'CG-DD-20260415-0001' AND remark = '运营演示数据';

UPDATE purchase_order
SET lifecycle_status = 3, audit_status = 2, fulfill_status = 3
WHERE order_no IN ('CG-DD-20260520-0001', 'CG-DD-20260618-0001', 'CG-DD-20260722-0001')
  AND remark = '运营演示数据';

UPDATE purchase_order
SET lifecycle_status = 3, audit_status = 2, fulfill_status = 1
WHERE order_no = 'CG-DD-20260819-0001' AND remark = '运营演示数据';

UPDATE purchase_order
SET lifecycle_status = 3, audit_status = 2, fulfill_status = 2
WHERE order_no = 'CG-DD-20260910-0001' AND remark = '运营演示数据';

UPDATE purchase_order
SET lifecycle_status = 2, audit_status = 1, fulfill_status = 1
WHERE order_no = 'CG-DD-20261027-0001' AND remark = '运营演示数据';

-- 3) 基线核对：以下四项全部应为 0
SELECT '演示单主单金额与明细不一致' AS check_item, COUNT(*) AS violations
FROM purchase_order o
WHERE o.remark = '运营演示数据' AND o.is_deleted = 0
  AND ABS(o.total_amount - (SELECT COALESCE(SUM(i.amount), 0) FROM purchase_order_item i
                            WHERE i.order_id = o.id AND i.is_deleted = 0)) > 0.01
UNION ALL
SELECT '演示库存余额与流水累计不一致', COUNT(*)
FROM stock_balance b
WHERE b.batch_no IN ('B20260401001', 'B20260401002', 'B20260401003', 'B20260401004',
                     'B20260401005', 'B20260501001', 'B20260501002', 'B20260501003', 'B20260501004')
  AND b.is_deleted = 0
  AND b.quantity <> (SELECT COALESCE(SUM(f.change_qty), 0) FROM stock_flow f
                     WHERE f.material_id = b.material_id AND f.warehouse_id = b.warehouse_id
                       AND f.batch_no = b.batch_no AND f.owner_no = b.owner_no AND f.is_deleted = 0)
UNION ALL
SELECT '演示订单三状态非法组合', COUNT(*)
FROM purchase_order o
WHERE o.remark = '运营演示数据' AND o.is_deleted = 0
  AND (o.lifecycle_status = 1 AND o.audit_status = 2)
UNION ALL
SELECT '演示待审批单缺少待处理任务', COUNT(*)
FROM purchase_order o
WHERE o.order_no = 'CG-DD-20261027-0001' AND o.is_deleted = 0
  AND (SELECT COUNT(*) FROM approval_task t
       WHERE t.bill_no = o.order_no AND t.task_status = 1 AND t.is_deleted = 0) <> 2;

-- 4) 基线规模（供人工核对，不是断言）
SELECT '演示订单数' AS item, COUNT(*) AS value FROM purchase_order
WHERE is_deleted = 0
UNION ALL
SELECT '未结清库存行数', COUNT(*) FROM stock_balance WHERE is_deleted = 0
UNION ALL
SELECT '库存不足物料数', COUNT(*)
FROM (SELECT m.id FROM material m
      LEFT JOIN stock_balance b ON b.material_id = m.id AND b.is_deleted = 0
      WHERE m.is_deleted = 0 AND m.status = 1 AND m.safety_stock > 0
      GROUP BY m.id, m.safety_stock
      HAVING COALESCE(SUM(b.quantity), 0) - COALESCE(SUM(b.frozen_qty), 0)
             - COALESCE(SUM(b.reserved_qty), 0) < m.safety_stock) t;
