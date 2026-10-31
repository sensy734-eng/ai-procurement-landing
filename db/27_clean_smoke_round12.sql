-- ==============================================================
-- 文件：27_clean_smoke_round12.sql
-- 作用：全流程演示脚本（scripts/smoke_round12.py）跑完后的清理，复原演示基线
--
-- 为什么这轮要「真删」而不是像轮次 11 那样只复原状态：
--   smoke_round12 走的是一条完整的业务链，会真的造出申请单、订单、验收单、
--   入库单、库存余额、库存流水、审批任务与交付流水。这些单据的 remark 一律以
--   「smk12」开头，本脚本按这个前缀把它们整条链拆掉。
--
-- 删除顺序按依赖倒挂写（流水 → 余额 → 明细 → 主单），
-- 订单还要按 source_request_id 关联删除——转订单生成的订单 remark 是
-- 「由采购申请转订单生成」，不带 smk12 前缀，只按 remark 删会漏。
--
-- 幂等：数据不在时影响 0 行，可反复执行。
-- 用法：mysql -uroot procurement_db < db/27_clean_smoke_round12.sql
-- ==============================================================

SET NAMES utf8mb4;
USE procurement_db;

-- ------------------------------------------------------------
-- 一、先把这次链条涉及的主键收到临时表里（后面反复用，避免自引用子查询）
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_r12_req;
CREATE TEMPORARY TABLE tmp_r12_req AS
SELECT id, request_no FROM purchase_request WHERE remark LIKE 'smk12%';

DROP TEMPORARY TABLE IF EXISTS tmp_r12_ord;
CREATE TEMPORARY TABLE tmp_r12_ord AS
SELECT id, order_no FROM purchase_order
WHERE remark LIKE 'smk12%' OR source_request_id IN (SELECT id FROM tmp_r12_req);

DROP TEMPORARY TABLE IF EXISTS tmp_r12_ins;
CREATE TEMPORARY TABLE tmp_r12_ins AS
SELECT id FROM arrival_inspection
WHERE remark LIKE 'smk12%' OR order_id IN (SELECT id FROM tmp_r12_ord);

DROP TEMPORARY TABLE IF EXISTS tmp_r12_inb;
CREATE TEMPORARY TABLE tmp_r12_inb AS
SELECT id, inbound_no FROM inbound_order
WHERE remark LIKE 'smk12%' OR order_id IN (SELECT id FROM tmp_r12_ord);

DROP TEMPORARY TABLE IF EXISTS tmp_r12_batch;
CREATE TEMPORARY TABLE tmp_r12_batch AS
SELECT DISTINCT batch_no FROM inbound_item WHERE inbound_id IN (SELECT id FROM tmp_r12_inb);

-- 删除前先记一份规模读数：删完之后再查「残留」必然是 0，看不出这次到底清了什么
DROP TEMPORARY TABLE IF EXISTS tmp_r12_before;
CREATE TEMPORARY TABLE tmp_r12_before AS
SELECT '申请单' AS tbl, (SELECT COUNT(*) FROM tmp_r12_req) AS cnt
UNION ALL SELECT '订单', (SELECT COUNT(*) FROM tmp_r12_ord)
UNION ALL SELECT '验收单', (SELECT COUNT(*) FROM tmp_r12_ins)
UNION ALL SELECT '入库单', (SELECT COUNT(*) FROM tmp_r12_inb)
UNION ALL SELECT '库存批次', (SELECT COUNT(*) FROM tmp_r12_batch);

-- ------------------------------------------------------------
-- 二、库存：流水 → 余额（余额是流水的聚合结果，顺序反了会短暂不一致）
-- ------------------------------------------------------------
DELETE FROM stock_flow
WHERE source_no IN (SELECT inbound_no FROM tmp_r12_inb)
   OR batch_no IN (SELECT batch_no FROM tmp_r12_batch);

DELETE FROM stock_balance
WHERE batch_no IN (SELECT batch_no FROM tmp_r12_batch);

-- ------------------------------------------------------------
-- 三、入库单与验收单（先子后父）
-- ------------------------------------------------------------
DELETE FROM inbound_item WHERE inbound_id IN (SELECT id FROM tmp_r12_inb);
DELETE FROM inbound_order WHERE id IN (SELECT id FROM tmp_r12_inb);

DELETE FROM arrival_inspection_item WHERE inspection_id IN (SELECT id FROM tmp_r12_ins);
DELETE FROM arrival_inspection WHERE id IN (SELECT id FROM tmp_r12_ins);

-- ------------------------------------------------------------
-- 四、审批任务与交付流水（按单号，任务表没有单据外键）
-- ------------------------------------------------------------
DELETE FROM approval_task
WHERE bill_no IN (SELECT request_no FROM tmp_r12_req)
   OR bill_no IN (SELECT order_no FROM tmp_r12_ord);

DELETE FROM delivery_progress WHERE order_no IN (SELECT order_no FROM tmp_r12_ord);

-- ------------------------------------------------------------
-- 五、订单与申请单（先子后父）
-- ------------------------------------------------------------
DELETE FROM purchase_order_item WHERE order_id IN (SELECT id FROM tmp_r12_ord);
DELETE FROM purchase_order WHERE id IN (SELECT id FROM tmp_r12_ord);

DELETE FROM purchase_request_item WHERE request_id IN (SELECT id FROM tmp_r12_req);
DELETE FROM purchase_request WHERE id IN (SELECT id FROM tmp_r12_req);

-- ------------------------------------------------------------
-- 六、本次清理的规模（删除前读数）
-- ------------------------------------------------------------
SELECT '本次清理（删除前）' AS stage, tbl, cnt FROM tmp_r12_before;

-- ------------------------------------------------------------
-- 七、核对：应全为 0（说明链条数据已清干净）
-- ------------------------------------------------------------
SELECT 'smk12 申请单残留' AS check_item, COUNT(*) AS violations
FROM purchase_request WHERE remark LIKE 'smk12%'
UNION ALL
SELECT 'smk12 订单残留（含转单生成）', COUNT(*)
FROM purchase_order WHERE remark LIKE 'smk12%'
   OR source_request_id IN (SELECT id FROM purchase_request WHERE remark LIKE 'smk12%')
UNION ALL
SELECT 'smk12 验收/入库单残留', COUNT(*)
FROM arrival_inspection WHERE remark LIKE 'smk12%'
UNION ALL
SELECT 'smk12 库存批次残留', COUNT(*)
FROM stock_balance WHERE batch_no IN ('B12A' , 'B12B') OR batch_no LIKE 'B12A%' OR batch_no LIKE 'B12B%'
UNION ALL
SELECT 'smk12 审批任务残留', COUNT(*)
FROM approval_task WHERE bill_no IN (
  SELECT request_no FROM purchase_request WHERE remark LIKE 'smk12%');

-- ------------------------------------------------------------
-- 八、复原基线读数（与 24 运营演示数据一致，便于一眼确认回到起点）
-- ------------------------------------------------------------
SELECT '订单' AS tbl, COUNT(*) AS cnt FROM purchase_order WHERE is_deleted = 0
UNION ALL SELECT '库存余额行', COUNT(*) FROM stock_balance WHERE is_deleted = 0
UNION ALL SELECT '库存流水行', COUNT(*) FROM stock_flow WHERE is_deleted = 0
UNION ALL SELECT '验收单', COUNT(*) FROM arrival_inspection WHERE is_deleted = 0
UNION ALL SELECT '入库单', COUNT(*) FROM inbound_order WHERE is_deleted = 0
UNION ALL SELECT '审批任务(待处理)', COUNT(*) FROM approval_task WHERE is_deleted = 0 AND task_status = 1;
