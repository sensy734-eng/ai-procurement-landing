-- 轮次 7 冒烟清理：M5 采购入库与库存 + M4 跨模块链残留
-- 执行：mysql -u root procurement_db < db/20_clean_smoke_round7.sql
-- 口径：演示数据保留（inbound_order/inbound_item/stock_balance/stock_flow 的演示行 id 均为 1），
--      冒烟数据按 id 边界清除；订单行履约与审批任务随订单一并复原。
--      操作日志按审计要求保留，不清理。
-- 注意（轮次 5 既有的演示数据口径）：演示订单 1 的行 2 仍是待交付，
--      按 recomputeFulfillStatus 规则整单应为待交付(1)，但演示种子按
--      「三状态正交样本」置为部分交付(2)，19_clean 同样复原为 2；
--      轮次 7 冒烟触发重算后会翻成 1，本脚本按种子基线复原，保持各轮一致。

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

-- 五、复原审批任务为演示规模（幂等，防只跑本脚本时审批历史缺失）
DELETE FROM approval_task;
INSERT INTO approval_task (task_no, bill_type, bill_id, bill_no, node_seq, node_name, approver_id, sign_type, task_status, opinion, handle_time) VALUES
  ('TASK-20261010-0001', 1, 1, 'CG-SQ-20261010-0001', 1, '采购主管审批', 4, 1, 2, '同意采购',                '2026-10-10 15:00:00'),
  ('TASK-20261014-0001', 1, 3, 'CG-SQ-20261014-0001', 1, '采购主管审批', 5, 1, 3, '需求日期过紧，请调整后重提', '2026-10-14 11:00:00'),
  ('TASK-20261011-0001', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 4, 2, 2, '价格与参考价一致，同意',    '2026-10-11 10:00:00'),
  ('TASK-20261011-0002', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 5, 2, 2, '同意',                    '2026-10-11 10:20:00')
ON DUPLICATE KEY UPDATE task_no = VALUES(task_no);

-- 六、验证：应回到演示规模
--      申请单 3 / 申请行 5 / 订单 2 / 订单行 3 / 审批任务 4 /
--      验收单 1 / 验收明细 1 / 入库单 1 / 入库明细 1 / 库存余额 1 / 库存流水 1 /
--      物料 8 / 供应商 4
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
UNION ALL SELECT '物料', COUNT(*) FROM material
UNION ALL SELECT '供应商', COUNT(*) FROM supplier;
