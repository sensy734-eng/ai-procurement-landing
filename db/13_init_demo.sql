-- ==============================================================
-- 文件：13_init_demo.sql
-- 作用：演示主数据与演示单据
-- 声明：全部企业与人员信息均为自造演示数据，不对应任何真实公司、真实客户或真实人员
-- 演示故事线：
--   申请单 1 已审核并全部转单 -> 订单 1 已审核通过、部分交付
--   -> 验收单 1 到货 600 合格 600 -> 入库单 1 已确认 -> 库存余额 600、流水 1 条
--   申请单 2 草稿待提交
--   申请单 3 被驳回，生命周期回落草稿、审批结果驳回（演示三状态正交）
--   订单 2 草稿，偏离率 6.98% 记为关注
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- 一、物料分类
-- --------------------------------------------------------------
INSERT INTO material_category (id, category_no, category_name, parent_id, level_no, sort_no, status) VALUES
  (1, 'CL-01', '原材料',   0, 1, 1, 1),
  (2, 'CL-02', '标准件',   0, 1, 2, 1),
  (3, 'CL-03', '电气件',   0, 1, 3, 1),
  (4, 'CL-04', '包装材料', 0, 1, 4, 1)
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- --------------------------------------------------------------
-- 二、物料
-- --------------------------------------------------------------
INSERT INTO material (id, material_no, material_name, category_id, spec, brand, unit_id, material_type, safety_stock, status, remark, created_by) VALUES
  (1, 'M-1001', '内六角螺栓',  2, 'M8×30',            '国标',   1001, 3, 200.0000, 1, '演示数据', 2),
  (2, 'M-1002', '不锈钢板',    1, '304 2mm',          '太钢',   1003, 1, 100.0000, 1, '演示数据', 2),
  (3, 'M-1003', '铝合金型材',  1, '6063 40×40',       '南山',   1004, 1,  50.0000, 1, '演示数据', 2),
  (4, 'M-1004', '交流接触器',  3, 'CJX2-2510 220V',   '正泰',   1001, 3,  20.0000, 1, '演示数据', 2),
  (5, 'M-1005', '铜芯电缆',    3, 'BV 2.5mm²',        '远东',   1004, 1, 100.0000, 1, '演示数据', 2),
  (6, 'M-1006', '瓦楞纸箱',    4, '600×400×300',      '恒信',   1001, 2, 300.0000, 1, '演示数据', 2),
  (7, 'M-1007', '深沟球轴承',  2, '6204-2RS',         '哈轴',   1001, 3,  40.0000, 1, '演示数据', 2),
  (8, 'M-1008', '切削液',      1, '20L 桶装',         '长城',   1003, 2,  20.0000, 2, '演示数据，已停用，用于演示停用物料不可在新单据选择', 2)
ON DUPLICATE KEY UPDATE material_name = VALUES(material_name);

INSERT INTO material_ext_attr (material_id, attr_name, attr_value, sort_no, created_by) VALUES
  (1, '表面处理', '镀锌',       1, 2),
  (1, '强度等级', '8.8 级',     2, 2),
  (2, '执行标准', 'GB/T 4237',  1, 2)
ON DUPLICATE KEY UPDATE attr_value = VALUES(attr_value);

-- --------------------------------------------------------------
-- 三、供应商与联系人、供货范围
-- --------------------------------------------------------------
INSERT INTO supplier (id, supplier_no, supplier_name, supplier_type, tax_no, address, phone, bank_name, bank_account, status, remark, created_by) VALUES
  (1, 'SUP-001', '台州宏达紧固件有限公司',   1, '91331000MA2DEMO01X', '台州市椒江区演示路 1 号', '0576-88000001', '演示银行台州分行', '6222000000000001', 1, '演示数据', 2),
  (2, 'SUP-002', '宁波盛达金属材料有限公司', 2, '91330200MA2DEMO02X', '宁波市北仑区演示路 2 号', '0574-88000002', '演示银行宁波分行', '6222000000000002', 1, '演示数据', 2),
  (3, 'SUP-003', '温州正泰电气销售有限公司', 2, '91330300MA2DEMO03X', '温州市乐清市演示路 3 号', '0577-88000003', '演示银行温州分行', '6222000000000003', 1, '演示数据', 2),
  (4, 'SUP-004', '台州恒信包装制品厂',       1, '91331000MA2DEMO04X', '台州市黄岩区演示路 4 号', '0576-88000004', '演示银行台州分行', '6222000000000004', 1, '演示数据', 2)
ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name);

INSERT INTO supplier_contact (supplier_id, contact_name, position, phone, email, is_default, status, created_by) VALUES
  (1, '王经理', '销售经理', '13800000001', 'demo1@example.com', 1, 1, 2),
  (2, '刘经理', '销售经理', '13800000002', 'demo2@example.com', 1, 1, 2),
  (3, '周经理', '销售经理', '13800000003', 'demo3@example.com', 1, 1, 2),
  (4, '徐经理', '销售经理', '13800000004', 'demo4@example.com', 1, 1, 2)
ON DUPLICATE KEY UPDATE contact_name = VALUES(contact_name);

INSERT INTO supplier_scope (supplier_id, scope_type, scope_id, status, created_by) VALUES
  (1, 1, 1, 1, 2),
  (1, 1, 7, 1, 2),
  (2, 1, 2, 1, 2),
  (2, 1, 3, 1, 2),
  (3, 1, 4, 1, 2),
  (3, 1, 5, 1, 2),
  (4, 1, 6, 1, 2)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- --------------------------------------------------------------
-- 四、仓库、库位、仓库使用人
-- --------------------------------------------------------------
INSERT INTO warehouse (id, warehouse_no, warehouse_name, manager_id, address, status, created_by) VALUES
  (1, 'WH-001', '原料仓', 6, '台州市演示工业园 1 号库', 1, 1),
  (2, 'WH-002', '成品仓', 6, '台州市演示工业园 2 号库', 1, 1)
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

INSERT INTO warehouse_location (id, warehouse_id, location_no, location_name, status, created_by) VALUES
  (1, 1, 'A-01-01', '原料仓 A 区 1 排 1 位', 1, 1),
  (2, 1, 'A-01-02', '原料仓 A 区 1 排 2 位', 1, 1),
  (3, 2, 'B-01-01', '成品仓 B 区 1 排 1 位', 1, 1),
  (4, 2, 'B-01-02', '成品仓 B 区 1 排 2 位', 1, 1)
ON DUPLICATE KEY UPDATE location_name = VALUES(location_name);

INSERT INTO warehouse_user (warehouse_id, user_id, is_default, created_by) VALUES
  (1, 6, 1, 1),
  (2, 6, 0, 1)
ON DUPLICATE KEY UPDATE is_default = VALUES(is_default);

-- --------------------------------------------------------------
-- 五、归属对象
-- --------------------------------------------------------------
INSERT INTO owner_object (id, owner_type, owner_no, owner_name, status, created_by) VALUES
  (1, 1, 'PUBLIC',        '公共库存',       1, 1),
  (2, 2, 'PRJ-2026-001',  '生产线改造项目', 1, 1),
  (3, 3, 'WO-2026-001',   '装配工单 001',   1, 1)
ON DUPLICATE KEY UPDATE owner_name = VALUES(owner_name);

-- --------------------------------------------------------------
-- 六、价格参考与统计快照
-- 声明：参考价为手工设定值，统计快照为历史成交口径，
--       二者都不是当前供应商报价（R6）
-- --------------------------------------------------------------
INSERT INTO price_reference (material_id, ref_price, effective_date, price_version, source_type, status, created_by) VALUES
  (1,  1.2000, '2026-09-01', 1, 1, 1, 4),
  (2, 24.0000, '2026-09-01', 1, 1, 1, 4),
  (3, 18.5000, '2026-09-01', 1, 1, 1, 4),
  (4, 86.0000, '2026-09-01', 1, 1, 1, 4)
ON DUPLICATE KEY UPDATE ref_price = VALUES(ref_price);

INSERT INTO price_stat_snapshot (material_id, supplier_id, stat_type, stat_value, stat_window, sample_count, source_bill_no, calc_time) VALUES
  (1, 0, 1,   1.1800, 'LAST_5_DEALS', 5, NULL,                       '2026-10-15 02:00:00'),
  (1, 0, 2,   1.2000, 'LAST_5_DEALS', 5, 'CG-DD-20261011-0001',      '2026-10-15 02:00:00'),
  (2, 0, 1,  24.5000, 'LAST_5_DEALS', 3, NULL,                       '2026-10-15 02:00:00'),
  (3, 0, 1,  19.0000, 'LAST_5_DEALS', 2, NULL,                       '2026-10-15 02:00:00')
ON DUPLICATE KEY UPDATE stat_value = VALUES(stat_value);

-- --------------------------------------------------------------
-- 七、采购申请单与明细行
-- --------------------------------------------------------------
INSERT INTO purchase_request (id, request_no, request_date, dept_id, applicant_id, require_date, total_amount, lifecycle_status, audit_status, remark, created_by, updated_by) VALUES
  (1, 'CG-SQ-20261010-0001', '2026-10-10', 1101, 2, '2026-10-20', 13700.00, 3, 2, '演示数据：已审核且已全部转单', 2, 2),
  (2, 'CG-SQ-20261012-0001', '2026-10-12', 1102, 3, '2026-10-25',  7398.00, 1, 1, '演示数据：草稿待提交',           3, 3),
  (3, 'CG-SQ-20261014-0001', '2026-10-14', 1101, 2, '2026-10-22',  1600.00, 1, 3, '演示数据：被驳回，生命周期回落草稿、审批结果驳回', 2, 2)
ON DUPLICATE KEY UPDATE request_no = VALUES(request_no);

INSERT INTO purchase_request_item (request_id, item_no, material_id, quantity, unit_id, est_price, est_amount, suggest_supplier_id, require_date, owner_type, owner_id, owner_no, transferred_qty, item_status, remark) VALUES
  (1, 1, 1, 1000.0000, 1001,  1.2000,  1200.00, 1, '2026-10-20', 1, 1, 'PUBLIC',       1000.0000, 3, NULL),
  (1, 2, 2,  500.0000, 1003, 25.0000, 12500.00, 2, '2026-10-20', 2, 2, 'PRJ-2026-001',  500.0000, 3, '归属生产线改造项目'),
  (2, 1, 3,  200.0000, 1004, 18.5000,  3700.00, 2, '2026-10-25', 1, 1, 'PUBLIC',          0.0000, 1, NULL),
  (2, 2, 4,   43.0000, 1001, 86.0000,  3698.00, 3, '2026-10-25', 1, 1, 'PUBLIC',          0.0000, 1, NULL),
  (3, 1, 5,  500.0000, 1004,  3.2000,  1600.00, 3, '2026-10-22', 1, 1, 'PUBLIC',          0.0000, 1, '驳回原因：需求日期过紧')
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- --------------------------------------------------------------
-- 八、采购订单与明细行
-- 订单 1 三状态：已审核 + 通过 + 部分交付（三状态正交的演示样本）
-- --------------------------------------------------------------
INSERT INTO purchase_order (id, order_no, source_request_id, source_request_no, supplier_id, buyer_id, dept_id, order_date, delivery_date, delivery_place, settlement_type,
                            lifecycle_status, audit_status, fulfill_status, total_qty, total_amount, no_tax_amount, tax_amount, deviation_flag, remark, created_by, updated_by) VALUES
  (1, 'CG-DD-20261011-0001', 1, 'CG-SQ-20261010-0001', 1, 2, 1101, '2026-10-11', '2026-10-18', '台州市演示工业园 1 号库', 1,
      3, 2, 2, 1500.0000, 13700.00, 12123.90, 1576.10, 0, '演示数据：已审核通过，部分交付', 2, 2),
  (2, 'CG-DD-20261016-0001', NULL, NULL, 3, 3, 1102, '2026-10-16', '2026-10-30', '台州市演示工业园 1 号库', 2,
      1, 1, 1, 50.0000, 4600.00, 4070.80, 529.20, 1, '演示数据：草稿，偏离关注', 3, 3)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

INSERT INTO purchase_order_item (order_id, item_no, material_id, source_request_item_id, quantity, unit_id, unit_price, tax_rate, amount, no_tax_amount,
                                 delivery_date, delivery_place, delivered_qty, owner_type, owner_id, owner_no, item_fulfill_status,
                                 deviation_rate, deviation_level, remark) VALUES
  (1, 1, 1, 1, 1000.0000, 1001,  1.2000, 0.1300,  1200.00,  1061.95, '2026-10-18', '台州市演示工业园 1 号库',  600.0000, 1, 1, 'PUBLIC',       2, 0.0000, 0, NULL),
  (1, 2, 2, 2,  500.0000, 1003, 25.0000, 0.1300, 12500.00, 11061.95, '2026-10-18', '台州市演示工业园 1 号库',    0.0000, 2, 2, 'PRJ-2026-001', 1, 0.0417, 0, '参考价 24.00，偏离 4.17%，未达关注阈值'),
  (2, 1, 4, NULL, 50.0000, 1001, 92.0000, 0.1300,  4600.00,  4070.80, '2026-10-30', '台州市演示工业园 1 号库',    0.0000, 1, 1, 'PUBLIC',       1, 0.0698, 1, '参考价 86.00，偏离 6.98%，记为关注')
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO delivery_progress (order_id, order_no, order_item_id, item_no, material_id, delivery_qty, delivery_date, remark, operator_id) VALUES
  (1, 'CG-DD-20261011-0001', 1, 1, 1, 600.0000, '2026-10-15', '演示数据：首批到货并已入库', 2)
ON DUPLICATE KEY UPDATE delivery_qty = VALUES(delivery_qty);

-- --------------------------------------------------------------
-- 九、到货验收单与明细
-- --------------------------------------------------------------
INSERT INTO arrival_inspection (id, inspection_no, order_id, order_no, supplier_id, arrival_date, warehouse_id, inspection_mode, inspection_result,
                                qualified_qty, unqualified_qty, concession_flag, inspector_id, status, remark, created_by, updated_by) VALUES
  (1, 'CG-YS-20261015-0001', 1, 'CG-DD-20261011-0001', 1, '2026-10-15', 1, 2, 1, 600.0000, 0.0000, 0, 6, 2, '演示数据：到货 600 全部合格', 6, 6)
ON DUPLICATE KEY UPDATE inspection_no = VALUES(inspection_no);

INSERT INTO arrival_inspection_item (inspection_id, order_item_id, material_id, unit_id, arrival_qty, qualified_qty, unqualified_qty, batch_no, owner_type, owner_id, owner_no) VALUES
  (1, 1, 1, 1001, 600.0000, 600.0000, 0.0000, 'B20261015', 1, 1, 'PUBLIC')
ON DUPLICATE KEY UPDATE arrival_qty = VALUES(arrival_qty);

-- --------------------------------------------------------------
-- 十、采购入库单与明细
-- --------------------------------------------------------------
INSERT INTO inbound_order (id, inbound_no, inspection_id, inspection_no, order_id, order_no, supplier_id, warehouse_id, inbound_date,
                           status, confirm_time, confirm_by, operator_id, remark, created_by, updated_by) VALUES
  (1, 'CG-RK-20261015-0001', 1, 'CG-YS-20261015-0001', 1, 'CG-DD-20261011-0001', 1, 1, '2026-10-15',
      2, '2026-10-15 16:30:00', 6, 6, '演示数据：已确认，库存唯一写入点', 6, 6)
ON DUPLICATE KEY UPDATE inbound_no = VALUES(inbound_no);

INSERT INTO inbound_item (inbound_id, order_item_id, material_id, quantity, unit_id, batch_no, location_id, owner_type, owner_id, owner_no) VALUES
  (1, 1, 1, 600.0000, 1001, 'B20261015', 1, 1, 1, 'PUBLIC')
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- --------------------------------------------------------------
-- 十一、库存余额与流水（与入库单确认结果严格一致）
-- --------------------------------------------------------------
INSERT INTO stock_balance (material_id, warehouse_id, batch_no, owner_type, owner_id, owner_no, quantity, frozen_qty, reserved_qty) VALUES
  (1, 1, 'B20261015', 1, 1, 'PUBLIC', 600.0000, 0.0000, 0.0000)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO stock_flow (material_id, warehouse_id, batch_no, owner_type, owner_id, owner_no, change_type, change_qty, before_qty, after_qty,
                        source_type, source_id, source_no, operator_id, flow_time) VALUES
  (1, 1, 'B20261015', 1, 1, 'PUBLIC', 1, 600.0000, 0.0000, 600.0000, 1, 1, 'CG-RK-20261015-0001', 6, '2026-10-15 16:30:00')
ON DUPLICATE KEY UPDATE change_qty = VALUES(change_qty);

-- --------------------------------------------------------------
-- 十二、审批任务（申请走或签，订单走会签）
-- --------------------------------------------------------------
INSERT INTO approval_task (task_no, bill_type, bill_id, bill_no, node_seq, node_name, approver_id, sign_type, task_status, opinion, handle_time) VALUES
  ('TASK-20261010-0001', 1, 1, 'CG-SQ-20261010-0001', 1, '采购主管审批', 4, 1, 2, '同意采购',         '2026-10-10 15:00:00'),
  ('TASK-20261014-0001', 1, 3, 'CG-SQ-20261014-0001', 1, '采购主管审批', 5, 1, 3, '需求日期过紧，请调整后重提', '2026-10-14 11:00:00'),
  ('TASK-20261011-0001', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 4, 2, 2, '价格与参考价一致，同意', '2026-10-11 10:00:00'),
  ('TASK-20261011-0002', 2, 1, 'CG-DD-20261011-0001', 1, '采购主管会签', 5, 2, 2, '同意',           '2026-10-11 10:20:00')
ON DUPLICATE KEY UPDATE task_no = VALUES(task_no);

-- --------------------------------------------------------------
-- 十三、演示操作日志
-- --------------------------------------------------------------
INSERT INTO sys_operation_log (user_id, username, real_name, module_code, action, target_type, target_id, target_no, detail, result_code, ip) VALUES
  (2, 'caigou01',  '张伟',       'M2', 'create',  'purchase_request', 1, 'CG-SQ-20261010-0001', '新建申请单，2 行明细', 'Success', '127.0.0.1'),
  (2, 'caigou01',  '张伟',       'M2', 'submit',  'purchase_request', 1, 'CG-SQ-20261010-0001', '生命周期 1 -> 2',      'Success', '127.0.0.1'),
  (4, 'zhuguan01', '王强',       'M2', 'approve', 'purchase_request', 1, 'CG-SQ-20261010-0001', '生命周期 2 -> 3，审批结果 1 -> 2', 'Success', '127.0.0.1'),
  (2, 'caigou01',  '张伟',       'M2', 'transfer','purchase_request', 1, 'CG-SQ-20261010-0001', '按行转订单，生成 CG-DD-20261011-0001', 'Success', '127.0.0.1'),
  (6, 'cangku01',  '陈杰',       'M5', 'confirm', 'inbound_order',    1, 'CG-RK-20261015-0001', '入库确认，库存 0 -> 600', 'Success', '127.0.0.1')
ON DUPLICATE KEY UPDATE action = VALUES(action);
