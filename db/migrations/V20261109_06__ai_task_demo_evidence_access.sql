-- Explicit additional evidence-view permission for the new fictional demo account only.
-- Do not change existing buyer/manager roles or department/owner ranges.
START TRANSACTION;
INSERT INTO sys_role (id,role_code,role_name,data_scope_all,is_system,status,remark)
VALUES (81001,'ai_demo_evidence','AI演示价格证据查看',0,0,1,'仅给独立AI演示账号补充价格证据查看权限')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO sys_role_permission (role_id,perm_id)
SELECT 81001,p.id FROM sys_permission p WHERE p.perm_code='m6:evidence:view'
ON DUPLICATE KEY UPDATE role_id=81001;
INSERT INTO sys_user_role (user_id,role_id) VALUES (81001,81001) ON DUPLICATE KEY UPDATE id=id;
COMMIT;
