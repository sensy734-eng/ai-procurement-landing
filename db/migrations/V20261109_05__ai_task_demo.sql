-- Fictional AI task scenarios, isolated by AI-DEMO codes and reserved IDs 81001-81099.
-- Dates are relative to the first migration. Restarts never reset them or user edits.
-- No receipts, stock balances or stock flows are fabricated: all orders remain undelivered.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO material (id,material_no,material_name,category_id,spec,unit_id,status,remark) VALUES
 (81001,'AI-DEMO-M8','AI演示螺栓',2,'M8×30',1001,1,'虚构演示：同名不同规格'),
 (81002,'AI-DEMO-M10','AI演示螺栓',2,'M10×40',1001,1,'虚构演示：必须消歧'),
 (81003,'AI-DEMO-SENSOR','AI演示传感器',3,'S-01',1001,1,'虚构演示：没有参考价'),
 (81004,'AI-DEMO-CABLE','AI演示电缆',3,'2.5mm²',1004,1,'虚构演示：单位为米，不自动换算卷'),
 (81005,'AI-DEMO-RETIRED','AI演示停用件',2,'旧版',1001,2,'虚构演示：不得用于新申请')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO owner_object (id,owner_type,owner_no,owner_name,status) VALUES
 (81001,2,'AI-DEMO-PRJ-A','AI演示产线项目',1),
 (81002,2,'AI-DEMO-PRJ-B','AI演示隔离项目',1)
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO sys_user (id,username,password_hash,real_name,dept_id,status,remark) VALUES
 (81001,'ai_buyer','$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6','AI演示采购员',1101,1,'虚构演示账号，仅授权采购部、公共与项目A')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO sys_user_role (user_id,role_id) VALUES (81001,1) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO sys_data_scope (subject_type,subject_id,scope_dimension,scope_value,remark) VALUES
 (1,81001,2,'1101','AI任务演示部门'),
 (1,81001,3,'PUBLIC','AI任务演示公共归属'),
 (1,81001,3,'AI-DEMO-PRJ-A','AI任务演示项目A')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO supplier_scope (supplier_id,scope_type,scope_id,status) VALUES
 (1,1,81001,1),(1,1,81002,1),(3,1,81003,1),(3,1,81004,1)
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO price_reference (id,material_id,ref_price,effective_date,price_version,source_type,status) VALUES
 (81001,81001,10,DATE_SUB(CURDATE(),INTERVAL 60 DAY),1,1,1),
 (81002,81002,15,DATE_SUB(CURDATE(),INTERVAL 60 DAY),1,1,1)
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO purchase_order (id,order_no,supplier_id,buyer_id,dept_id,order_date,delivery_date,
 lifecycle_status,audit_status,fulfill_status,total_qty,total_amount,no_tax_amount,tax_amount,deviation_flag,remark,created_at) VALUES
 (81001,'AI-DEMO-ORDER-NORMAL',1,81001,1101,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,100,1000,884.96,115.04,0,'虚构演示：参考价一致，未到交期',NOW()),
 (81002,'AI-DEMO-ORDER-RISK',1,81001,1101,CURDATE(),DATE_SUB(CURDATE(),INTERVAL 7 DAY),3,2,1,200,3600,3185.84,414.16,2,'虚构演示：偏离80%，交期已过，原因待核实',NOW()),
 (81003,'AI-DEMO-ORDER-NOREF',3,81001,1101,CURDATE(),NULL,3,2,1,10,2000,1769.91,230.09,0,'虚构演示：无参考价、无交期、无同物料历史',NOW()),
 (81004,'AI-DEMO-ORDER-FOREIGN',1,3,1102,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,10,990,876.11,113.89,2,'虚构演示：另部门订单，不得越权审查',NOW()),
 (81005,'AI-DEMO-HISTORY-A',1,81001,1101,DATE_SUB(CURDATE(),INTERVAL 20 DAY),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,100,1000,884.96,115.04,0,'虚构演示：授权范围内价格样本A，待交付',DATE_SUB(NOW(),INTERVAL 20 DAY)),
 (81006,'AI-DEMO-HISTORY-B',1,81001,1101,DATE_SUB(CURDATE(),INTERVAL 10 DAY),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,300,3600,3185.84,414.16,2,'虚构演示：授权范围内价格样本B，待交付',DATE_SUB(NOW(),INTERVAL 10 DAY)),
 (81007,'AI-DEMO-HISTORY-FOREIGN',1,3,1102,DATE_SUB(CURDATE(),INTERVAL 5 DAY),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,100,99900,88407.08,11492.92,2,'虚构演示：外部门极端价不得污染本部门统计',DATE_SUB(NOW(),INTERVAL 5 DAY)),
 (81008,'AI-DEMO-HISTORY-OWNER',1,2,1101,DATE_SUB(CURDATE(),INTERVAL 3 DAY),DATE_ADD(CURDATE(),INTERVAL 30 DAY),3,2,1,100,80000,70796.46,9203.54,2,'虚构演示：同部门未授权归属不得污染统计',DATE_SUB(NOW(),INTERVAL 3 DAY))
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO purchase_order_item (id,order_id,item_no,material_id,quantity,unit_id,unit_price,tax_rate,amount,no_tax_amount,
 delivery_date,delivered_qty,owner_type,owner_id,owner_no,item_fulfill_status,deviation_rate,deviation_level) VALUES
 (81001,81001,1,81001,100,1001,10,0.13,1000,884.96,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,1,1,'PUBLIC',1,0,0),
 (81002,81002,1,81001,200,1001,18,0.13,3600,3185.84,DATE_SUB(CURDATE(),INTERVAL 7 DAY),0,2,81001,'AI-DEMO-PRJ-A',1,0.8,2),
 (81003,81003,1,81003,10,1001,200,0.13,2000,1769.91,NULL,0,1,1,'PUBLIC',1,NULL,0),
 (81004,81004,1,81001,10,1001,99,0.13,990,876.11,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,1,1,'PUBLIC',1,8.9,2),
 (81005,81005,1,81001,100,1001,10,0.13,1000,884.96,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,1,1,'PUBLIC',1,0,0),
 (81006,81006,1,81001,300,1001,12,0.13,3600,3185.84,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,2,81001,'AI-DEMO-PRJ-A',1,0.2,2),
 (81007,81007,1,81001,100,1001,999,0.13,99900,88407.08,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,1,1,'PUBLIC',1,98.9,2),
 (81008,81008,1,81001,100,1001,800,0.13,80000,70796.46,DATE_ADD(CURDATE(),INTERVAL 30 DAY),0,2,81002,'AI-DEMO-PRJ-B',1,79,2)
ON DUPLICATE KEY UPDATE id=id;
COMMIT;
