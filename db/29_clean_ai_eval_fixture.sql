-- ============================================================
-- 29_clean_ai_eval_fixture.sql
-- 撤销 28_init_ai_eval_fixture.sql 建立的评测夹具
-- 幂等；只删夹具账号与角色，不触碰业务数据
-- 执行：mysql procurement_db < db/29_clean_ai_eval_fixture.sql
-- ============================================================
USE procurement_db;

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.username IN ('eval_norole', 'eval_disabled');

DELETE FROM sys_user WHERE username IN ('eval_norole', 'eval_disabled');

DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_code = 'eval_limited';

DELETE FROM sys_role WHERE role_code = 'eval_limited';

SELECT '夹具已清理' AS step,
       (SELECT COUNT(*) FROM sys_user WHERE username LIKE 'eval\_%') AS users_left,
       (SELECT COUNT(*) FROM sys_role WHERE role_code = 'eval_limited') AS roles_left;
