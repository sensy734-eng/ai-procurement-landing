-- ============================================================
-- 28_init_ai_eval_fixture.sql
-- 大模型评测夹具：G2-07（停用账号）、G2-08（无 AI 权限角色）两条用例
-- 幂等：可重复执行；清理见 29_clean_ai_eval_fixture.sql
-- 密码与 caigou01 相同（123456），直接复制其哈希，避免明文口令落库
-- 执行：mysql procurement_db < db/28_init_ai_eval_fixture.sql
-- ============================================================
USE procurement_db;

-- 1 无 AI 权限角色：只持一条与问答无关的权限点
INSERT INTO sys_role (role_code, role_name, data_scope_all, is_system, status, remark, is_deleted, version)
SELECT 'eval_limited', '评测用受限角色', 0, 0, 1, 'AI 评测夹具，无 m7:chat:query', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'eval_limited');

INSERT INTO sys_role_permission (role_id, perm_id, is_deleted, version)
SELECT r.id, p.id, 0, 0
FROM sys_role r, sys_permission p
WHERE r.role_code = 'eval_limited' AND p.perm_code = 'm1:material:list'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.perm_id = p.id);

-- 2 评测账号：eval_norole（启用、无 AI 权限）、eval_disabled（停用）
INSERT INTO sys_user (username, password_hash, real_name, dept_id, status, remark, is_deleted, version)
SELECT 'eval_norole', u.password_hash, '评测受限账号', 1101, 1, 'AI 评测夹具', 0, 0
FROM sys_user u WHERE u.username = 'caigou01'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'eval_norole');

INSERT INTO sys_user (username, password_hash, real_name, dept_id, status, remark, is_deleted, version)
SELECT 'eval_disabled', u.password_hash, '评测停用账号', 1101, 0, 'AI 评测夹具', 0, 0
FROM sys_user u WHERE u.username = 'caigou01'
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'eval_disabled');

INSERT INTO sys_user_role (user_id, role_id, is_deleted, version)
SELECT u.id, r.id, 0, 0
FROM sys_user u, sys_role r
WHERE u.username = 'eval_norole' AND r.role_code = 'eval_limited'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

SELECT '夹具就绪' AS step, username, status FROM sys_user
WHERE username IN ('eval_norole', 'eval_disabled');
