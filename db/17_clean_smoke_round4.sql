-- ==============================================================
-- 文件：17_clean_smoke_round4.sql
-- 作用：清理轮次 4 冒烟测试产生的 M2 数据，并把演示数据复原到初始状态。
-- 轮次 4 冒烟（smoke_round4.py）之外，调试期间的临时单据也一并清除。
--
-- 清理口径：
--   1. 先删子表（订单明细、审批任务、申请明细），再删主表（订单、申请单）；
--   2. 演示数据固定为申请单 1~3、申请明细 1~5、订单 1~2、订单明细 1~3、
--      审批任务 1~4，一律按 id 边界删除，不用备注匹配，避免误删演示数据；
--   3. 申请单 2、3 在调试中被改过状态，按 13_init_demo.sql 的初始值复原；
--   4. 操作日志按审计要求保留，不清理（与轮次 2、3 同一原则）。
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- 一、订单侧（演示订单只有 1、2 两条，其余全部是测试转单产生的）
DELETE FROM purchase_order_item
WHERE order_id > 2;
DELETE FROM purchase_order
WHERE id > 2;

-- 二、审批任务（演示任务 4 条：申请 2 条 + 订单会签 2 条）
DELETE FROM approval_task
WHERE id > 4;

-- 三、申请明细行（演示 5 行）与申请单（演示 3 条）
DELETE FROM purchase_request_item
WHERE request_id > 3;
DELETE FROM purchase_request
WHERE id > 3;

-- 四、演示申请单 2、3 状态复原（13_init_demo.sql 初始值）
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

-- 五、申请单 3 的明细行复原（调试中被转过订单，已转数量清零、行状态回待转）
UPDATE purchase_request_item
SET transferred_qty = 0.0000, item_status = 1
WHERE id = 5;

-- 六、验证：应回到演示规模（3 单 / 5 行 / 2 订单 / 4 任务）
SELECT 'purchase_request' AS tbl, COUNT(*) AS cnt FROM purchase_request
UNION ALL SELECT 'purchase_request_item', COUNT(*) FROM purchase_request_item
UNION ALL SELECT 'purchase_order', COUNT(*) FROM purchase_order
UNION ALL SELECT 'purchase_order_item', COUNT(*) FROM purchase_order_item
UNION ALL SELECT 'approval_task', COUNT(*) FROM approval_task;
