-- Repair the new demo role written by a client with a non-UTF-8 default charset.
SET NAMES utf8mb4;
UPDATE sys_role SET role_name='AI演示价格证据查看',remark='仅给独立AI演示账号补充价格证据查看权限'
WHERE id=81001 AND role_code='ai_demo_evidence';
