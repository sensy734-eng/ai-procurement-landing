-- ============================================================
-- 轮次 11 运营演示数据（M12 管理后台与数据可视化）
--
-- 目的：让 P25 后台首页与 P26 运营仪表盘一打开就有内容。
-- 原有演示数据只有 2 张订单、1 行库存，四张图里有三张是空的，
-- 无法验证「图表数字与明细一致」这条判据，也无法用于完整业务演示。
--
-- 本脚本只做 INSERT 与精确 DELETE（按脚本自造的单号/批次号），
-- 不改动 13_init_demo.sql 的既有单据；重复执行安全（先删后插）。
--
-- 三条自洽约束，与 99_verify.sql 的校验项对齐：
-- 1. 主单 total_amount = Σ 明细 amount（校验第 8 项）；
-- 2. 库存余额与流水累计一致：每行余额配一条入库流水，before 0 → after 数量（第 9 项）；
-- 3. 三状态组合合法：草稿/待审批/已审核/已关闭与履约状态不出现矛盾组合（第 6 项）。
--
-- 演示单不设价格偏离（deviation_flag=0、deviation_rate 为 NULL），
-- 避免进入 M6 偏离关注清单而干扰既有冒烟与演示基线。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 幂等清理：只删本脚本造的数据 ----------
DELETE FROM delivery_progress WHERE order_no IN (
  'CG-DD-20260415-0001', 'CG-DD-20260520-0001', 'CG-DD-20260618-0001',
  'CG-DD-20260722-0001', 'CG-DD-20260819-0001', 'CG-DD-20260910-0001',
  'CG-DD-20261027-0001');

DELETE FROM approval_task WHERE bill_no = 'CG-DD-20261027-0001';

DELETE FROM purchase_order_item WHERE order_id IN (
  SELECT id FROM purchase_order WHERE order_no IN (
    'CG-DD-20260415-0001', 'CG-DD-20260520-0001', 'CG-DD-20260618-0001',
    'CG-DD-20260722-0001', 'CG-DD-20260819-0001', 'CG-DD-20260910-0001',
    'CG-DD-20261027-0001'));

DELETE FROM purchase_order WHERE order_no IN (
  'CG-DD-20260415-0001', 'CG-DD-20260520-0001', 'CG-DD-20260618-0001',
  'CG-DD-20260722-0001', 'CG-DD-20260819-0001', 'CG-DD-20260910-0001',
  'CG-DD-20261027-0001');

DELETE FROM stock_flow WHERE source_no IN ('CG-RK-20260401-0001', 'CG-RK-20260501-0001');
DELETE FROM stock_balance WHERE batch_no IN (
  'B20260401001', 'B20260401002', 'B20260401003', 'B20260401004', 'B20260401005',
  'B20260501001', 'B20260501002', 'B20260501003', 'B20260501004');
DELETE FROM inbound_item WHERE inbound_id IN (
  SELECT id FROM inbound_order WHERE inbound_no IN ('CG-RK-20260401-0001', 'CG-RK-20260501-0001'));
DELETE FROM inbound_order WHERE inbound_no IN ('CG-RK-20260401-0001', 'CG-RK-20260501-0001');

-- ============================================================
-- 一、历史采购订单 7 张（覆盖五个状态桶 + 草稿已在 13_init_demo）
--    部门 1101 采购部 / 1102 生产部；采购员 2=caigou01、3=caigou02
-- ============================================================

-- 1) 已关闭（2026-04）：全部交付后人工关闭，余量关闭
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260415-0001', 2, 2, 1101, '2026-04-15', '2026-04-30',
  1, 4, 2, 4, 1300.0000, 28450.00, 25176.99, 3273.01, 0, '合同执行完毕，余量关闭', '运营演示数据',
  0, 0, 2, '2026-04-15 09:20:00', 2, '2026-04-30 16:00:00');
SET @o1 = LAST_INSERT_ID();

-- 2) 已完成（2026-05）：全部交付，lifecycle 仍为已审核、履约全部交付
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260520-0001', 1, 2, 1101, '2026-05-20', '2026-06-10',
  1, 3, 2, 3, 12040.0000, 17840.00, 15787.61, 2052.39, 0, NULL, '运营演示数据',
  0, 0, 2, '2026-05-20 10:05:00', 2, '2026-06-10 14:30:00');
SET @o2 = LAST_INSERT_ID();

-- 3) 已完成（2026-06）
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260618-0001', 3, 3, 1101, '2026-06-18', '2026-07-08',
  2, 3, 2, 3, 1400.0000, 43200.00, 38230.09, 4969.91, 0, NULL, '运营演示数据',
  0, 0, 3, '2026-06-18 09:40:00', 3, '2026-07-08 11:20:00');
SET @o3 = LAST_INSERT_ID();

-- 4) 已完成（2026-07，生产部）
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260722-0001', 4, 3, 1102, '2026-07-22', '2026-08-05',
  1, 3, 2, 3, 3000.0000, 9600.00, 8495.58, 1104.42, 0, NULL, '运营演示数据',
  0, 0, 3, '2026-07-22 15:10:00', 3, '2026-08-05 09:00:00');
SET @o4 = LAST_INSERT_ID();

-- 5) 待交付（2026-08）：交货期在未来，不落入「超期未交付」
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260819-0001', 2, 2, 1101, '2026-08-19', '2026-11-15',
  1, 3, 2, 1, 1450.0000, 33425.00, 29579.65, 3845.35, 0, NULL, '运营演示数据',
  0, 0, 2, '2026-08-19 11:30:00', 2, '2026-08-19 11:30:00');
SET @o5 = LAST_INSERT_ID();

-- 6) 部分交付（2026-09，生产部）
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20260910-0001', 1, 3, 1102, '2026-09-10', '2026-10-20',
  1, 3, 2, 2, 15060.0000, 20880.00, 18477.87, 2402.13, 0, NULL, '运营演示数据',
  0, 0, 3, '2026-09-10 08:50:00', 3, '2026-10-08 10:00:00');
SET @o6 = LAST_INSERT_ID();

-- 7) 待审批（2026-10）：已提交、未审核，对应会签两条待办任务
INSERT INTO purchase_order (order_no, supplier_id, buyer_id, dept_id, order_date, delivery_date,
  settlement_type, lifecycle_status, audit_status, fulfill_status, total_qty, total_amount,
  no_tax_amount, tax_amount, deviation_flag, close_reason, remark,
  is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-DD-20261027-0001', 3, 2, 1101, '2026-10-27', '2026-12-10',
  1, 2, 1, 1, 250.0000, 12800.00, 11327.43, 1472.57, 0, NULL, '运营演示数据',
  0, 0, 2, '2026-10-27 09:15:00', 2, '2026-10-27 09:15:00');
SET @o7 = LAST_INSERT_ID();

-- ============================================================
-- 二、订单明细（金额与主单一致；已交付行回写 delivered_qty）
-- ============================================================

-- 订单 1：不锈钢板 800 + 铝合金型材 500，全部交付后关闭
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o1, 1, 2, 800.0000, 1003, 24.0000, 0.1300, 19200.00, 16991.15, '2026-04-28',
  800.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 2, '2026-04-15 09:20:00', '2026-04-28 10:00:00');
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o1, 2, 3, 500.0000, 1004, 18.5000, 0.1300, 9250.00, 8185.84, '2026-04-28',
  500.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 2, '2026-04-15 09:20:00', '2026-04-28 10:00:00');

-- 订单 2：内六角螺栓 12000 + 交流接触器 40，全部交付
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o2, 1, 1, 12000.0000, 1001, 1.2000, 0.1300, 14400.00, 12743.36, '2026-06-08',
  12000.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 2, '2026-05-20 10:05:00', '2026-06-08 15:00:00');
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o2, 2, 4, 40.0000, 1001, 86.0000, 0.1300, 3440.00, 3044.25, '2026-06-08',
  40.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 2, '2026-05-20 10:05:00', '2026-06-08 15:00:00');

-- 订单 3：铜芯电缆 1200 + 深沟球轴承 200，全部交付
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o3, 1, 5, 1200.0000, 1004, 28.0000, 0.1300, 33600.00, 29734.51, '2026-07-06',
  1200.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 3, '2026-06-18 09:40:00', '2026-07-06 16:30:00');
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o3, 2, 7, 200.0000, 1001, 48.0000, 0.1300, 9600.00, 8495.58, '2026-07-06',
  200.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 3, '2026-06-18 09:40:00', '2026-07-06 16:30:00');

-- 订单 4：瓦楞纸箱 3000，全部交付
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o4, 1, 6, 3000.0000, 1001, 3.2000, 0.1300, 9600.00, 8495.58, '2026-08-03',
  3000.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 3, '2026-07-22 15:10:00', '2026-08-03 10:00:00');

-- 订单 5：待交付，交付量为 0
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o5, 1, 2, 1200.0000, 1003, 24.0000, 0.1300, 28800.00, 25486.73, '2026-11-15',
  0.0000, 1, 1, 'PUBLIC', 1, 0, NULL, 0, 0, 2, '2026-08-19 11:30:00', '2026-08-19 11:30:00');
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o5, 2, 3, 250.0000, 1004, 18.5000, 0.1300, 4625.00, 4092.92, '2026-11-15',
  0.0000, 1, 1, 'PUBLIC', 1, 0, NULL, 0, 0, 2, '2026-08-19 11:30:00', '2026-08-19 11:30:00');

-- 订单 6：部分交付（螺栓 8000/15000，轴承 60/60）
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o6, 1, 1, 15000.0000, 1001, 1.2000, 0.1300, 18000.00, 15929.20, '2026-10-20',
  8000.0000, 1, 1, 'PUBLIC', 2, 0, NULL, 0, 0, 3, '2026-09-10 08:50:00', '2026-10-08 10:00:00');
SET @oi6_1 = LAST_INSERT_ID();
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o6, 2, 7, 60.0000, 1001, 48.0000, 0.1300, 2880.00, 2548.67, '2026-10-20',
  60.0000, 1, 1, 'PUBLIC', 3, 0, NULL, 0, 0, 3, '2026-09-10 08:50:00', '2026-10-08 10:00:00');

-- 订单 7：待审批，交付量为 0
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o7, 1, 4, 100.0000, 1001, 86.0000, 0.1300, 8600.00, 7610.62, '2026-12-10',
  0.0000, 1, 1, 'PUBLIC', 1, 0, NULL, 0, 0, 2, '2026-10-27 09:15:00', '2026-10-27 09:15:00');
INSERT INTO purchase_order_item (order_id, item_no, material_id, quantity, unit_id, unit_price,
  tax_rate, amount, no_tax_amount, delivery_date, delivered_qty, owner_type, owner_id, owner_no,
  item_fulfill_status, deviation_level, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@o7, 2, 5, 150.0000, 1004, 28.0000, 0.1300, 4200.00, 3716.81, '2026-12-10',
  0.0000, 1, 1, 'PUBLIC', 1, 0, NULL, 0, 0, 2, '2026-10-27 09:15:00', '2026-10-27 09:15:00');

-- ============================================================
-- 三、部分交付订单的交付流水（与 delivered_qty 一致）
-- ============================================================
INSERT INTO delivery_progress (order_id, order_no, order_item_id, item_no, material_id,
  delivery_qty, delivery_date, batch_no, remark, operator_id,
  is_deleted, version, created_by, created_at, updated_at)
VALUES (@o6, 'CG-DD-20260910-0001', @oi6_1, 1, 1, 8000.0000, '2026-10-08', 'B20261008001',
  '首批到货', 3, 0, 0, 3, '2026-10-08 10:00:00', '2026-10-08 10:00:00');

-- ============================================================
-- 四、待审批订单的会签任务（两位主管各一条，进入「我的待办」）
-- ============================================================
INSERT INTO approval_task (task_no, bill_type, bill_id, bill_no, node_seq, node_name,
  approver_id, sign_type, task_status, is_deleted, version, created_by, created_at, updated_at)
VALUES ('CG-TASK-20261027-0001', 2, @o7, 'CG-DD-20261027-0001', 1, '采购订单会签',
  4, 2, 1, 0, 0, 2, '2026-10-27 09:15:00', '2026-10-27 09:15:00');
INSERT INTO approval_task (task_no, bill_type, bill_id, bill_no, node_seq, node_name,
  approver_id, sign_type, task_status, is_deleted, version, created_by, created_at, updated_at)
VALUES ('CG-TASK-20261027-0002', 2, @o7, 'CG-DD-20261027-0001', 1, '采购订单会签',
  5, 2, 1, 0, 0, 2, '2026-10-27 09:15:00', '2026-10-27 09:15:00');

-- ============================================================
-- 五、入库单两张（已确认）——库存余额的唯一写入来源
--     显式造入库单而不是直接插余额，是为了让「余额 = 流水累计」在对账时成立。
-- ============================================================
INSERT INTO inbound_order (inbound_no, inspection_id, inspection_no, order_id, order_no,
  supplier_id, warehouse_id, inbound_date, status, confirm_time, confirm_by,
  operator_id, remark, is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-RK-20260401-0001', NULL, NULL, NULL, NULL, NULL, 1, '2026-04-01', 2,
  '2026-04-01 10:00:00', 6, 6, '运营演示：原料仓期初库存（无来源单据）', 0, 0, 6,
  '2026-04-01 09:30:00', 6, '2026-04-01 10:00:00');
SET @i1 = LAST_INSERT_ID();

INSERT INTO inbound_order (inbound_no, inspection_id, inspection_no, order_id, order_no,
  supplier_id, warehouse_id, inbound_date, status, confirm_time, confirm_by,
  operator_id, remark, is_deleted, version, created_by, created_at, updated_by, updated_at)
VALUES ('CG-RK-20260501-0001', NULL, NULL, NULL, NULL, NULL, 2, '2026-05-01', 2,
  '2026-05-01 10:00:00', 6, 6, '运营演示：成品仓期初库存（无来源单据）', 0, 0, 6,
  '2026-05-01 09:30:00', 6, '2026-05-01 10:00:00');
SET @i2 = LAST_INSERT_ID();

-- 原料仓五行
INSERT INTO inbound_item (inbound_id, order_item_id, material_id, quantity, unit_id, batch_no,
  location_id, owner_type, owner_id, owner_no, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@i1, NULL, 2, 150.0000, 1003, 'B20260401001', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-04-01 09:30:00', '2026-04-01 09:30:00'),
       (@i1, NULL, 3, 30.0000, 1004, 'B20260401002', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-04-01 09:30:00', '2026-04-01 09:30:00'),
       (@i1, NULL, 7, 60.0000, 1001, 'B20260401003', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-04-01 09:30:00', '2026-04-01 09:30:00'),
       (@i1, NULL, 8, 25.0000, 1003, 'B20260401004', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-04-01 09:30:00', '2026-04-01 09:30:00'),
       (@i1, NULL, 2, 40.0000, 1003, 'B20260401005', NULL, 2, 2, 'PRJ-2026-001', '生产线改造项目专用', 0, 0, 6, '2026-04-01 09:30:00', '2026-04-01 09:30:00');

-- 成品仓四行
INSERT INTO inbound_item (inbound_id, order_item_id, material_id, quantity, unit_id, batch_no,
  location_id, owner_type, owner_id, owner_no, remark, is_deleted, version, created_by, created_at, updated_at)
VALUES (@i2, NULL, 1, 80.0000, 1001, 'B20260501001', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-05-01 09:30:00', '2026-05-01 09:30:00'),
       (@i2, NULL, 4, 12.0000, 1001, 'B20260501002', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-05-01 09:30:00', '2026-05-01 09:30:00'),
       (@i2, NULL, 5, 40.0000, 1004, 'B20260501003', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-05-01 09:30:00', '2026-05-01 09:30:00'),
       (@i2, NULL, 6, 280.0000, 1001, 'B20260501004', NULL, 1, 1, 'PUBLIC', NULL, 0, 0, 6, '2026-05-01 09:30:00', '2026-05-01 09:30:00');

-- ============================================================
-- 六、库存余额与入库流水（九行，before 0 → after 数量）
--     不足物料：铝合金型材 30/50、交流接触器 12/20、铜芯电缆 40/100、瓦楞纸箱 280/300
-- ============================================================
INSERT INTO stock_balance (material_id, warehouse_id, batch_no, owner_type, owner_id, owner_no,
  quantity, frozen_qty, reserved_qty, is_deleted, version, created_by, created_at, updated_at)
VALUES (2, 1, 'B20260401001', 1, 1, 'PUBLIC', 150.0000, 0.0000, 0.0000, 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (3, 1, 'B20260401002', 1, 1, 'PUBLIC', 30.0000, 0.0000, 0.0000, 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (7, 1, 'B20260401003', 1, 1, 'PUBLIC', 60.0000, 0.0000, 0.0000, 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (8, 1, 'B20260401004', 1, 1, 'PUBLIC', 25.0000, 0.0000, 0.0000, 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (2, 1, 'B20260401005', 2, 2, 'PRJ-2026-001', 40.0000, 0.0000, 0.0000, 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (1, 2, 'B20260501001', 1, 1, 'PUBLIC', 80.0000, 0.0000, 0.0000, 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (4, 2, 'B20260501002', 1, 1, 'PUBLIC', 12.0000, 0.0000, 0.0000, 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (5, 2, 'B20260501003', 1, 1, 'PUBLIC', 40.0000, 0.0000, 0.0000, 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (6, 2, 'B20260501004', 1, 1, 'PUBLIC', 280.0000, 0.0000, 0.0000, 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00');

INSERT INTO stock_flow (material_id, warehouse_id, batch_no, owner_type, owner_id, owner_no,
  change_type, change_qty, before_qty, after_qty, source_type, source_id, source_no,
  operator_id, flow_time, is_deleted, version, created_by, created_at, updated_at)
VALUES (2, 1, 'B20260401001', 1, 1, 'PUBLIC', 1, 150.0000, 0.0000, 150.0000, 1, @i1, 'CG-RK-20260401-0001', 6, '2026-04-01 10:00:00', 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (3, 1, 'B20260401002', 1, 1, 'PUBLIC', 1, 30.0000, 0.0000, 30.0000, 1, @i1, 'CG-RK-20260401-0001', 6, '2026-04-01 10:00:00', 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (7, 1, 'B20260401003', 1, 1, 'PUBLIC', 1, 60.0000, 0.0000, 60.0000, 1, @i1, 'CG-RK-20260401-0001', 6, '2026-04-01 10:00:00', 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (8, 1, 'B20260401004', 1, 1, 'PUBLIC', 1, 25.0000, 0.0000, 25.0000, 1, @i1, 'CG-RK-20260401-0001', 6, '2026-04-01 10:00:00', 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (2, 1, 'B20260401005', 2, 2, 'PRJ-2026-001', 1, 40.0000, 0.0000, 40.0000, 1, @i1, 'CG-RK-20260401-0001', 6, '2026-04-01 10:00:00', 0, 0, 6, '2026-04-01 10:00:00', '2026-04-01 10:00:00'),
       (1, 2, 'B20260501001', 1, 1, 'PUBLIC', 1, 80.0000, 0.0000, 80.0000, 1, @i2, 'CG-RK-20260501-0001', 6, '2026-05-01 10:00:00', 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (4, 2, 'B20260501002', 1, 1, 'PUBLIC', 1, 12.0000, 0.0000, 12.0000, 1, @i2, 'CG-RK-20260501-0001', 6, '2026-05-01 10:00:00', 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (5, 2, 'B20260501003', 1, 1, 'PUBLIC', 1, 40.0000, 0.0000, 40.0000, 1, @i2, 'CG-RK-20260501-0001', 6, '2026-05-01 10:00:00', 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00'),
       (6, 2, 'B20260501004', 1, 1, 'PUBLIC', 1, 280.0000, 0.0000, 280.0000, 1, @i2, 'CG-RK-20260501-0001', 6, '2026-05-01 10:00:00', 0, 0, 6, '2026-05-01 10:00:00', '2026-05-01 10:00:00');

-- ============================================================
-- 七、自检：本脚本造成的三处可对账关系，输出应全部为 0
-- ============================================================
SELECT '演示单金额与明细不一致' AS check_item, COUNT(*) AS violations
FROM purchase_order o
WHERE o.order_no LIKE 'CG-DD-2026%0001' AND o.remark = '运营演示数据'
  AND ABS(o.total_amount - (SELECT COALESCE(SUM(i.amount), 0)
                             FROM purchase_order_item i
                             WHERE i.order_id = o.id AND i.is_deleted = 0)) > 0.01
UNION ALL
SELECT '演示库存与流水累计不一致', COUNT(*)
FROM stock_balance b
WHERE b.batch_no IN ('B20260401001', 'B20260401002', 'B20260401003', 'B20260401004',
                     'B20260401005', 'B20260501001', 'B20260501002', 'B20260501003', 'B20260501004')
  AND b.quantity <> (SELECT COALESCE(SUM(f.change_qty), 0) FROM stock_flow f
                     WHERE f.material_id = b.material_id AND f.warehouse_id = b.warehouse_id
                       AND f.batch_no = b.batch_no AND f.owner_no = b.owner_no AND f.is_deleted = 0)
UNION ALL
SELECT '演示订单三状态非法组合', COUNT(*)
FROM purchase_order o
WHERE o.order_no LIKE 'CG-DD-2026%0001' AND o.remark = '运营演示数据'
  AND (o.lifecycle_status = 1 AND o.audit_status = 2);
