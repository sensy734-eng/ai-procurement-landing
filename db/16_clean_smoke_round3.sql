-- ==============================================================
-- 文件：16_clean_smoke_round3.sql
-- 用途：清理轮次 3 冒烟测试数据，只删 smk3 前缀的物料、分类、供应商、
--       仓库、库位、字典测试项，不动 13_init_demo.sql 的演示主数据。
-- 执行时机：跑完 scripts/smoke_round3.py 之后。
-- 说明：操作日志不清理，与 15_clean_smoke.sql 同一原则，
--       日志记录的是真实发生过的操作，清理反而破坏审计完整性。
--       附件文件在服务端 server/uploads/supplier-attachments/ 下，
--       记录删除后文件保留，需要时手工清理该目录。
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- 1 供应商从表
DELETE FROM supplier_attachment
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk3%');
DELETE FROM supplier_scope
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk3%');
DELETE FROM supplier_contact
WHERE supplier_id IN (SELECT id FROM supplier WHERE supplier_no LIKE 'smk3%');

-- 2 供应商
DELETE FROM supplier WHERE supplier_no LIKE 'smk3%';

-- 3 仓库从表与仓库
DELETE FROM warehouse_user
WHERE warehouse_id IN (SELECT id FROM warehouse WHERE warehouse_no LIKE 'smk3%');
DELETE FROM warehouse_location
WHERE warehouse_id IN (SELECT id FROM warehouse WHERE warehouse_no LIKE 'smk3%');
DELETE FROM warehouse WHERE warehouse_no LIKE 'smk3%';

-- 4 物料扩展属性与物料（唯一键不含 is_deleted，物理删除）
DELETE FROM material_ext_attr
WHERE material_id IN (SELECT id FROM material WHERE material_no LIKE 'smk3%');
DELETE FROM material WHERE material_no LIKE 'smk3%';

-- 5 物料分类
DELETE FROM material_category WHERE category_no LIKE 'smk3%';

-- 6 字典：测试类型及其项；bill_type 与 supplier_type 的测试项按编码删
DELETE FROM dict_item
WHERE type_id IN (SELECT id FROM dict_type WHERE type_code LIKE 'smk3%');
DELETE FROM dict_type WHERE type_code LIKE 'smk3%';
DELETE FROM dict_item WHERE type_id IN (4, 9) AND item_code = '9';

-- 校验：应回到演示数据规模
SELECT (SELECT COUNT(*) FROM material) AS material_count,
       (SELECT COUNT(*) FROM material_category) AS category_count,
       (SELECT COUNT(*) FROM supplier) AS supplier_count,
       (SELECT COUNT(*) FROM warehouse) AS warehouse_count,
       (SELECT COUNT(*) FROM dict_type) AS dict_type_count,
       (SELECT COUNT(*) FROM dict_item WHERE type_id IN (4, 9) AND item_code = '9') AS dict_test_left;
