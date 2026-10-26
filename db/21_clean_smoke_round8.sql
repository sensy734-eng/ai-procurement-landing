-- ==============================================================
-- 文件：21_clean_smoke_round8.sql
-- 用途：轮次 8（M6 价格参考与偏离关注）冒烟后清理，复原演示规模
-- 结构：沿用 20_clean 的入库/库存/验收/审批复原，追加价格与偏离字段复原
-- 口径：
--   1. price_reference 只保留种子 4 行（id<=4，均 V1 生效），
--      冒烟造的新版本物理清除；种子行复原 status=1、remark=NULL
--   2. price_stat_snapshot 只保留种子 4 行（id<=4），刷新覆盖的值按种子复原
--   3. purchase_order_item / purchase_order 的偏离字段按种子口径复原
--      （保存/重算会改写 rate/level/flag，回种子快照保持各轮一致）
--   4. 操作日志按审计要求保留，不清理
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- 一、入库明细与入库单（演示 id=1 保留；冒烟生成的草稿/确认/作废单一并清除）
DELETE FROM inbound_item WHERE inbound_id > 1;
DELETE FROM inbound_order WHERE id > 1;

-- 二、库存余额与流水（演示 id=1 保留；作废冲减后的零余额批次行也清除）
DELETE FROM stock_balance WHERE id > 1;
DELETE FROM stock_flow WHERE id > 1;

-- 三、轮次 6 兜底：M4 跨模块链冒烟生成的验收单（演示 id=1 保留）
DELETE FROM arrival_inspection_item WHERE inspection_id > 1;
DELETE FROM arrival_inspection WHERE id > 1;

-- 四、复原演示订单行的履约与交付口径（入库确认/作废会改写 delivered_qty）
UPDATE purchase_order_item SET delivered_qty = 600.0000, item_fulfill_status = 2 WHERE id = 1;
UPDATE purchase_order_item SET delivered_qty = 0.0000, item_fulfill_status = 1 WHERE id = 2;
UPDATE purchase_order_item SET delivered_qty = 0.0000, item_fulfill_status = 1 WHERE id = 3;
UPDATE purchase_order SET fulfill_status = 2 WHERE id = 1;

-- 四之二、轮次 8 新增：清冒烟造的订单及其关联（演示订单 id<=2 保留；
--         入库/验收已在前面清掉，此处不会撞外键语义）
DELETE FROM purchase_order_item WHERE order_id > 2;
DELETE FROM order_change_log WHERE order_id > 2;
DELETE FROM delivery_progress WHERE order_id > 2;
DELETE FROM approval_task WHERE bill_type = 2 AND bill_id > 2;
DELETE FROM purchase_order WHERE id > 2;

-- 五、价格参考（M6-1-1）：清冒烟新版本，种子 4 行复原生效态
DELETE FROM price_reference WHERE id > 4;
UPDATE price_reference
   SET status = 1, remark = NULL, updated_by = NULL, updated_at = NOW()
 WHERE id <= 4;

-- 六、成交统计快照（M6-1-2/1-3）：清刷新写入，按种子 4 行复原
DELETE FROM price_stat_snapshot WHERE id > 4;
UPDATE price_stat_snapshot
   SET stat_value = CASE id
         WHEN 1 THEN 1.1800
         WHEN 2 THEN 1.2000
         WHEN 3 THEN 24.5000
         WHEN 4 THEN 19.0000
       END,
       sample_count = CASE id
         WHEN 1 THEN 5
         WHEN 2 THEN 5
         WHEN 3 THEN 3
         WHEN 4 THEN 2
       END,
       stat_window = 'LAST_5_DEALS',
       source_bill_no = CASE id WHEN 2 THEN 'CG-DD-20261011-0001' ELSE NULL END,
       calc_time = '2026-10-15 02:00:00',
       updated_at = NOW()
 WHERE id <= 4;

-- 七、偏离字段按种子口径复原（订单 1 三行 + 订单 2 一行；订单 1 无偏离、订单 2 关注）
UPDATE purchase_order_item
   SET deviation_rate = CASE id
         WHEN 1 THEN 0.0000
         WHEN 2 THEN 0.0417
         WHEN 3 THEN 0.0698
       END,
       deviation_level = CASE id
         WHEN 1 THEN 0
         WHEN 2 THEN 0
         WHEN 3 THEN 1
       END
 WHERE id <= 3;
UPDATE purchase_order SET deviation_flag = CASE id WHEN 2 THEN 1 ELSE 0 END WHERE id <= 2;

-- 八、复原审批任务为演示规模（幂等，防只跑本脚本时审批历史缺失）
DELETE FROM approval_task;
INSERT INTO approval_task (task_no, bill_type, bill_id, bill_no, node_seq, node_name, approver_id, sign_type, task_status, opinion, handle_time) VALUES
  ('TASK-20261010-0001', 1, 1, 'CG-SQ-20261010-0001', 1, '采购主管审批', 4, 1, 2, '同意采购',                '2026-10-10 15:00:00'),
  ('TASK-20261014-0001', 1, 3, 'CG-SQ-20261014-0001', 1, '采购主管审批', 5, 1, 3, '需求日期过紧，请调整后重提', '2026-10-14 11:00:00'),
  ('TASK-20261011-0001', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 4, 2, 2, '价格与参考价一致，同意',    '2026-10-11 10:00:00'),
  ('TASK-20261011-0002', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 5, 2, 2, '同意',                    '2026-10-11 10:20:00')
ON DUPLICATE KEY UPDATE task_no = VALUES(task_no);

-- 九、验证：应回到演示规模
--      申请单 3 / 申请行 5 / 订单 2 / 订单行 3 / 审批任务 4 /
--      验收单 1 / 验收明细 1 / 入库单 1 / 入库明细 1 / 库存余额 1 / 库存流水 1 /
--      参考价 4 / 统计快照 4 / 物料 8 / 供应商 4
SELECT '申请单' AS tbl, COUNT(*) AS cnt FROM purchase_request
UNION ALL SELECT '申请行', COUNT(*) FROM purchase_request_item
UNION ALL SELECT '订单', COUNT(*) FROM purchase_order
UNION ALL SELECT '订单行', COUNT(*) FROM purchase_order_item
UNION ALL SELECT '审批任务', COUNT(*) FROM approval_task
UNION ALL SELECT '验收单', COUNT(*) FROM arrival_inspection
UNION ALL SELECT '验收明细', COUNT(*) FROM arrival_inspection_item
UNION ALL SELECT '入库单', COUNT(*) FROM inbound_order
UNION ALL SELECT '入库明细', COUNT(*) FROM inbound_item
UNION ALL SELECT '库存余额', COUNT(*) FROM stock_balance
UNION ALL SELECT '库存流水', COUNT(*) FROM stock_flow
UNION ALL SELECT '参考价', COUNT(*) FROM price_reference
UNION ALL SELECT '统计快照', COUNT(*) FROM price_stat_snapshot
UNION ALL SELECT '物料', COUNT(*) FROM material
UNION ALL SELECT '供应商', COUNT(*) FROM supplier;
