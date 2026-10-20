-- ==============================================================
-- 文件：15_clean_smoke.sql
-- 用途：清理冒烟测试产生的数据，只删除 smoke 前缀的用户与角色，
--       不动 13_init_demo.sql 导入的演示主数据与单据。
-- 执行时机：跑完 scripts/smoke_round2.py 之后。
-- 说明：操作日志不清理，它记录的是真实发生过的操作，清理反而破坏审计完整性。
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- 1 用户角色关联
DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.username LIKE 'smoke%';

-- 2 测试用户
DELETE FROM sys_user WHERE username LIKE 'smoke%';

-- 3 角色权限关联
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_code LIKE 'smoke_role%';

-- 4 测试角色
DELETE FROM sys_role WHERE role_code LIKE 'smoke_role%';

-- 5 悬空的数据范围授权
DELETE FROM sys_data_scope
WHERE (subject_type = 1 AND subject_id NOT IN (SELECT id FROM sys_user))
   OR (subject_type = 2 AND subject_id NOT IN (SELECT id FROM sys_role));

-- 6 悬空会话
DELETE FROM sys_session WHERE user_id NOT IN (SELECT id FROM sys_user);

-- 校验：用户应为 6 个演示账号，角色应为 4 个内置角色
SELECT (SELECT COUNT(*) FROM sys_user) AS user_count,
       (SELECT COUNT(*) FROM sys_role) AS role_count,
       (SELECT COUNT(*) FROM sys_user WHERE username LIKE 'smoke%') AS smoke_user_left;
