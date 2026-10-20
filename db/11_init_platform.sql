-- ==============================================================
-- 文件：11_init_platform.sql
-- 作用：角色、权限点、角色授权、用户、数据范围、审批节点配置初始化
-- 权限点编码规范：模块:对象:动作，三级固定，如 m2:request:submit
-- 智能模块只允许 query、list、view、audit 四类只读动作，不设写操作
-- 演示账号统一初始密码 123456（BCrypt 哈希），首次上线必须改密
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- 一、四个角色（规划书 2.2）
-- --------------------------------------------------------------
INSERT INTO sys_role (id, role_code, role_name, data_scope_all, is_system, status, remark) VALUES
  (1, 'buyer',            '采购员',     0, 1, 1, '拟制申请与订单、登记交付、查询库存与价格，数据范围为本部门'),
  (2, 'buyer_manager',    '采购主管',   0, 1, 1, '审批申请与订单、查看偏离、让步接收、关闭与作废'),
  (3, 'warehouse_keeper', '仓管员',     0, 1, 1, '到货验收、入库确认、批次与库位登记，数据范围为被授权仓库'),
  (4, 'admin',            '系统管理员', 1, 1, 1, '主数据、用户角色权限、知识库、审计与全量配置，不参与业务审批')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- --------------------------------------------------------------
-- 二、权限点（156 个，按模块编号顺序）
-- --------------------------------------------------------------
INSERT INTO sys_permission (id, perm_code, perm_name, module_code, action_type, sort_no) VALUES
  -- M1 基础数据管理（1 ~ 30）
  (1,  'm1:material:list',              '物料档案查询',       'M1', 'list',   101),
  (2,  'm1:material:view',              '物料档案详情',       'M1', 'view',   102),
  (3,  'm1:material:create',            '新增物料',           'M1', 'create', 103),
  (4,  'm1:material:update',            '编辑物料',           'M1', 'update', 104),
  (5,  'm1:material:toggle',            '物料启用停用',       'M1', 'update', 105),
  (6,  'm1:material:import',            '物料批量导入',       'M1', 'import', 106),
  (7,  'm1:material:export',            '物料导出',           'M1', 'export', 107),
  (8,  'm1:material_ext:manage',        '物料扩展属性维护',   'M1', 'update', 108),
  (9,  'm1:material_category:list',     '物料分类查询',       'M1', 'list',   109),
  (10, 'm1:material_category:create',   '新增物料分类',       'M1', 'create', 110),
  (11, 'm1:material_category:update',   '编辑物料分类',       'M1', 'update', 111),
  (12, 'm1:supplier:list',              '供应商查询',         'M1', 'list',   112),
  (13, 'm1:supplier:view',              '供应商详情',         'M1', 'view',   113),
  (14, 'm1:supplier:create',            '新增供应商',         'M1', 'create', 114),
  (15, 'm1:supplier:update',            '编辑供应商',         'M1', 'update', 115),
  (16, 'm1:supplier:toggle',            '供应商启用停用',     'M1', 'update', 116),
  (17, 'm1:supplier_contact:manage',    '供应商联系人维护',   'M1', 'update', 117),
  (18, 'm1:supplier_attachment:upload', '供应商资质上传',     'M1', 'create', 118),
  (19, 'm1:supplier_attachment:delete', '供应商资质删除',     'M1', 'delete', 119),
  (20, 'm1:supplier_scope:manage',      '供货范围维护',       'M1', 'update', 120),
  (21, 'm1:warehouse:list',             '仓库查询',           'M1', 'list',   121),
  (22, 'm1:warehouse:create',           '新增仓库',           'M1', 'create', 122),
  (23, 'm1:warehouse:update',           '编辑仓库',           'M1', 'update', 123),
  (24, 'm1:location:list',              '库位查询',           'M1', 'list',   124),
  (25, 'm1:location:create',            '新增库位',           'M1', 'create', 125),
  (26, 'm1:location:update',            '编辑库位',           'M1', 'update', 126),
  (27, 'm1:warehouse_user:manage',      '仓库使用人配置',     'M1', 'update', 127),
  (28, 'm1:dict:list',                  '业务字典查询',       'M1', 'list',   128),
  (29, 'm1:dict:create',                '新增字典',           'M1', 'create', 129),
  (30, 'm1:dict:update',                '编辑字典',           'M1', 'update', 130),
  -- M2 采购申请管理（31 ~ 50）
  (31, 'm2:request:list',               '申请单查询',         'M2', 'list',   201),
  (32, 'm2:request:view',               '申请单详情',         'M2', 'view',   202),
  (33, 'm2:request:create',             '新建申请单',         'M2', 'create', 203),
  (34, 'm2:request:update',             '编辑申请单',         'M2', 'update', 204),
  (35, 'm2:request:delete',             '删除申请单',         'M2', 'delete', 205),
  (36, 'm2:request:copy',               '复制申请单',         'M2', 'create', 206),
  (37, 'm2:request:export',             '申请单打印导出',     'M2', 'export', 207),
  (38, 'm2:request:submit',             '提交审批',           'M2', 'submit', 208),
  (39, 'm2:request:approve',            '审批通过',           'M2', 'approve',209),
  (40, 'm2:request:reject',             '审批驳回',           'M2', 'reject', 210),
  (41, 'm2:request:withdraw',           '撤回申请',           'M2', 'withdraw',211),
  (42, 'm2:request:close',              '整单关闭',           'M2', 'close',  212),
  (43, 'm2:request:todo',               '申请待办列表',       'M2', 'list',   213),
  (44, 'm2:request_item:create',        '新增申请明细行',     'M2', 'create', 214),
  (45, 'm2:request_item:update',        '编辑申请明细行',     'M2', 'update', 215),
  (46, 'm2:request_item:delete',        '删除申请明细行',     'M2', 'delete', 216),
  (47, 'm2:request_item:suggest',       '指定建议供应商',     'M2', 'update', 217),
  (48, 'm2:request_item:owner',         '指定归属对象',       'M2', 'update', 218),
  (49, 'm2:request_item:return',        '按行退回',           'M2', 'approve',219),
  (50, 'm2:request:transfer',           '申请转采购订单',     'M2', 'create', 220),
  -- M3 采购订单管理（51 ~ 77）
  (51, 'm3:order:list',                 '订单查询',           'M3', 'list',   301),
  (52, 'm3:order:view',                 '订单详情',           'M3', 'view',   302),
  (53, 'm3:order:create',               '新建订单',           'M3', 'create', 303),
  (54, 'm3:order:update',               '编辑订单',           'M3', 'update', 304),
  (55, 'm3:order:void',                 '作废订单',           'M3', 'void',   305),
  (56, 'm3:order:export',               '订单打印导出',       'M3', 'export', 306),
  (57, 'm3:order:submit',               '提交审批',           'M3', 'submit', 307),
  (58, 'm3:order:approve',              '审批通过',           'M3', 'approve',308),
  (59, 'm3:order:reject',               '审批驳回',           'M3', 'reject', 309),
  (60, 'm3:order:withdraw',             '撤回审批',           'M3', 'withdraw',310),
  (61, 'm3:order:reaudit',              '反审',               'M3', 'reaudit',311),
  (62, 'm3:order:close',                '关闭订单',           'M3', 'close',  312),
  (63, 'm3:order:todo',                 '订单待办列表',       'M3', 'list',   313),
  (64, 'm3:order:trace',                '订单溯源',           'M3', 'view',   314),
  (65, 'm3:order_item:create',          '新增订单明细行',     'M3', 'create', 315),
  (66, 'm3:order_item:update',          '编辑订单明细行',     'M3', 'update', 316),
  (67, 'm3:order_item:delete',          '删除订单明细行',     'M3', 'delete', 317),
  (68, 'm3:order_item:close',           '按行关闭',           'M3', 'close',  318),
  (69, 'm3:order_item:owner',           '指定归属对象',       'M3', 'update', 319),
  (70, 'm3:order_change:create',        '变更登记',           'M3', 'create', 320),
  (71, 'm3:order_change:list',          '变更历史查看',       'M3', 'list',   321),
  (72, 'm3:delivery:register',          '交付进度登记',       'M3', 'create', 322),
  (73, 'm3:delivery:list',              '交付进度查询',       'M3', 'list',   323),
  (74, 'm3:delivery:overdue',           '超期未交付筛选',     'M3', 'list',   324),
  (75, 'm3:delivery:summary',           '交付汇总视图',       'M3', 'list',   325),
  (76, 'm3:deviation:view',             '价格偏离查看',       'M3', 'view',   326),
  (77, 'm3:deviation:recalc',           '偏离重算',           'M3', 'update', 327),
  -- M4 到货验收管理（78 ~ 85）
  (78, 'm4:inspection:list',            '验收记录查询',       'M4', 'list',   401),
  (79, 'm4:inspection:view',            '验收明细查看',       'M4', 'view',   402),
  (80, 'm4:inspection:create',          '新建到货登记',       'M4', 'create', 403),
  (81, 'm4:inspection:update',          '编辑验收登记',       'M4', 'update', 404),
  (82, 'm4:inspection:submit',          '验收单提交',         'M4', 'submit', 405),
  (83, 'm4:inspection:concession',      '让步接收标记',       'M4', 'approve',406),
  (84, 'm4:inspection:stat',            '验收结果统计',       'M4', 'list',   407),
  (85, 'm4:inbound:generate',           '按合格数生成入库单', 'M4', 'create', 408),
  -- M5 采购入库与库存管理（86 ~ 102）
  (86, 'm5:inbound:list',               '入库单查询',         'M5', 'list',   501),
  (87, 'm5:inbound:view',               '入库单详情',         'M5', 'view',   502),
  (88, 'm5:inbound:create',             '新建入库单',         'M5', 'create', 503),
  (89, 'm5:inbound:update',             '编辑入库单',         'M5', 'update', 504),
  (90, 'm5:inbound:confirm',            '确认入库',           'M5', 'update', 505),
  (91, 'm5:inbound:void',               '作废入库单',         'M5', 'void',   506),
  (92, 'm5:inbound:trace',              '入库单溯源',         'M5', 'view',   507),
  (93, 'm5:batch:list',                 '批次登记查询',       'M5', 'list',   508),
  (94, 'm5:batch:trace',                '批次追溯',           'M5', 'view',   509),
  (95, 'm5:stock:list',                 '库存余额查询',       'M5', 'list',   510),
  (96, 'm5:stock:available',            '可用量查询',         'M5', 'list',   511),
  (97, 'm5:stock:owner_group',          '按归属对象分组查询', 'M5', 'list',   512),
  (98, 'm5:stock:export',               '库存余额导出',       'M5', 'export', 513),
  (99, 'm5:stock:alert',                '库存不足清单',       'M5', 'list',   514),
  (100,'m5:flow:list',                  '库存流水查询',       'M5', 'list',   515),
  (101,'m5:flow:trace',                 '流水来源追溯',       'M5', 'view',   516),
  (102,'m5:flow:reconcile',             '余额与流水对账',     'M5', 'audit',  517),
  -- M6 价格参考与审批辅助（103 ~ 109）
  (103,'m6:price:list',                 '参考价查询',         'M6', 'list',   601),
  (104,'m6:price:view',                 '参考价详情',         'M6', 'view',   602),
  (105,'m6:price:create',               '新增参考价',         'M6', 'create', 603),
  (106,'m6:price:update',               '编辑参考价',         'M6', 'update', 604),
  (107,'m6:evidence:view',              '审批证据面板查看',   'M6', 'view',   605),
  (108,'m6:stat:view',                  '成交价统计查看',     'M6', 'list',   606),
  (109,'m6:param:config',               '价格口径与阈值配置', 'M6', 'config', 607),
  -- M7 智能助手统一入口（110 ~ 112，只读）
  (110,'m7:chat:query',                 '智能助手提问',       'M7', 'query',  701),
  (111,'m7:session:list',               '对话历史查看',       'M7', 'list',   702),
  (112,'m7:query:retry',                '参数修正与重跑',     'M7', 'query',  703),
  -- M8 制度与教程问答（113 ~ 114，只读）
  (113,'m8:kb:query',                   '制度与教程问答',     'M8', 'query',  801),
  (114,'m8:kb:cite',                    '来源引用查看',       'M8', 'view',   802),
  -- M9 受控数据查询（115 ~ 116，只读）
  (115,'m9:data:query',                 '自然语言数据查询',   'M9', 'query',  901),
  (116,'m9:audit:list',                 '智能查询审计查看',   'M9', 'audit',  902),
  -- M10 知识库管理（117 ~ 127）
  (117,'m10:document:list',             '知识文档查询',       'M10','list',   1001),
  (118,'m10:document:upload',           '知识文档上传',       'M10','create', 1002),
  (119,'m10:document:update',           '知识文档编辑',       'M10','update', 1003),
  (120,'m10:document:delete',           '知识文档删除',       'M10','delete', 1004),
  (121,'m10:document:parse',            '知识文档解析',       'M10','update', 1005),
  (122,'m10:document:toggle',           '知识文档启停用',     'M10','update', 1006),
  (123,'m10:chunk:list',                '切块查看',           'M10','list',   1007),
  (124,'m10:chunk:edit',                '切块调整',           'M10','update', 1008),
  (125,'m10:index:rebuild',             '索引重建',           'M10','config', 1009),
  (126,'m10:test:run',                  '检索测试',           'M10','query',  1010),
  (127,'m10:test:stat',                 '命中率统计',         'M10','list',   1011),
  -- M11 平台与权限管理（128 ~ 149）
  (128,'m11:user:list',                 '用户查询',           'M11','list',   1101),
  (129,'m11:user:create',               '新增用户',           'M11','create', 1102),
  (130,'m11:user:update',               '编辑用户',           'M11','update', 1103),
  (131,'m11:user:toggle',               '用户启用停用',       'M11','update', 1104),
  (132,'m11:user:resetpwd',             '重置密码',           'M11','update', 1105),
  (133,'m11:role:list',                 '角色查询',           'M11','list',   1106),
  (134,'m11:role:create',               '新增角色',           'M11','create', 1107),
  (135,'m11:role:update',               '编辑角色',           'M11','update', 1108),
  (136,'m11:role:grant',                '角色授权',           'M11','config', 1109),
  (137,'m11:permission:list',           '权限点查询',         'M11','list',   1110),
  (138,'m11:scope:list',                '数据范围查询',       'M11','list',   1111),
  (139,'m11:scope:grant',               '数据范围授权',       'M11','config', 1112),
  (140,'m11:log:list',                  '操作日志查询',       'M11','list',   1113),
  (141,'m11:log:export',                '操作日志导出',       'M11','export', 1114),
  (142,'m11:param:list',                '参数查询',           'M11','list',   1115),
  (143,'m11:param:update',              '参数修改',           'M11','config', 1116),
  (144,'m11:approval_node:list',        '审批流程查询',       'M11','list',   1117),
  (145,'m11:approval_node:create',      '审批节点新增',       'M11','create', 1118),
  (146,'m11:approval_node:update',      '审批节点编辑',       'M11','update', 1119),
  (147,'m11:approval_node:delete',      '审批节点删除',       'M11','delete', 1120),
  (148,'m11:session:list',              '在线会话查询',       'M11','list',   1121),
  (149,'m11:session:kick',              '强制下线',           'M11','config', 1122),
  -- M12 管理后台与数据可视化（150 ~ 156）
  (150,'m12:summary:purchase',          '采购单据汇总',       'M12','list',   1201),
  (151,'m12:summary:stock',             '库存汇总',           'M12','list',   1202),
  (152,'m12:summary:export',            '汇总结果导出',       'M12','export', 1203),
  (153,'m12:chart:status',              '订单状态分布图',     'M12','list',   1204),
  (154,'m12:chart:trend',               '采购金额趋势图',     'M12','list',   1205),
  (155,'m12:chart:supplier',            '供应商金额排名图',   'M12','list',   1206),
  (156,'m12:chart:stockalert',          '库存不足统计图',     'M12','list',   1207)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name);

-- --------------------------------------------------------------
-- 三、角色授权
-- --------------------------------------------------------------
-- 采购员：M1 物料与供应商全量维护，M2 建单与提交，M3 建单与交付登记，
--         其余模块只读；不含审批、关闭、作废、反审、让步接收
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE id IN (
  1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,24,28,
  31,32,33,34,35,36,37,38,41,50,44,45,46,47,48,
  51,52,53,54,56,57,60,64,65,66,67,69,70,71,72,73,74,75,76,77,
  78,79,
  86,87,93,94,95,96,97,99,100,101,
  103,104,108,
  110,111,112,113,114,115,156
);

-- 采购主管：审批、驳回、整单关闭、订单关闭与作废、按行退回、按行关闭、
--           让步接收、偏离证据、统计图表
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE id IN (
  1,2,12,13,21,24,28,
  31,32,37,39,40,42,43,49,
  51,52,55,56,58,59,62,63,64,68,71,73,74,75,76,
  78,79,83,84,
  86,87,91,95,96,97,98,99,100,101,
  103,104,105,106,107,108,
  110,111,112,113,114,115,
  150,151,152,153,154,155,156
);

-- 仓管员：验收与入库全流程，库存只读查询，不含让步接收与入库单作废
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE id IN (
  1,2,21,24,28,
  31,32,
  51,52,64,
  78,79,80,81,82,85,
  86,87,88,89,90,92,93,94,95,96,97,100,101,
  110,111,112,113,114,115,156
);

-- 系统管理员：基础数据配置、知识库、权限、审计与可视化；
--             业务侧只有反审一个动作，不授予任何审批权
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 4, id FROM sys_permission WHERE id IN (
  21,22,23,24,25,26,27,28,29,30,
  61,
  78,
  95,102,
  103,104,109,
  110,111,112,113,114,115,116,
  117,118,119,120,121,122,123,124,125,126,127,
  128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,
  150,151,152,153,154,155,156
);

-- --------------------------------------------------------------
-- 四、演示用户（演示数据，非真实人员信息）
-- 初始密码均为 123456，BCrypt 哈希，pwd_need_change 置 1 强制首登改密
-- --------------------------------------------------------------
INSERT INTO sys_user (id, username, password_hash, real_name, dept_id, phone, email, status, pwd_need_change, remark) VALUES
  (1, 'admin',     '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '系统管理员', 1105, NULL, NULL, 1, 0, '演示账号'),
  (2, 'caigou01',  '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '张伟',       1101, NULL, NULL, 1, 0, '演示账号，采购部采购员'),
  (3, 'caigou02',  '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '李娜',       1102, NULL, NULL, 1, 0, '演示账号，生产部采购员'),
  (4, 'zhuguan01', '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '王强',       1101, NULL, NULL, 1, 0, '演示账号，采购主管'),
  (5, 'zhuguan02', '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '赵敏',       1101, NULL, NULL, 1, 0, '演示账号，采购主管'),
  (6, 'cangku01',  '$2a$10$N7eCxdmi/nx.c7MEBgIDg.wYfRI3DupQgq/cGuwD0Z0GF8F0sQcb6', '陈杰',       1103, NULL, NULL, 1, 0, '演示账号，仓管员')
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);

INSERT INTO sys_user_role (user_id, role_id) VALUES
  (1, 4),
  (2, 1),
  (3, 1),
  (4, 2),
  (5, 2),
  (6, 3)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- --------------------------------------------------------------
-- 五、数据范围授权（M11-3-3）
-- 采购员按部门、主管按部门、仓管员按仓库；系统管理员由角色全量标记控制
-- --------------------------------------------------------------
INSERT INTO sys_data_scope (subject_type, subject_id, scope_dimension, scope_value, remark) VALUES
  (2, 2, 2, '1101', '采购主管角色：采购部'),
  (2, 2, 2, '1102', '采购主管角色：生产部'),
  (1, 2, 2, '1101', '张伟：采购部'),
  (1, 3, 2, '1102', '李娜：生产部'),
  (1, 4, 2, '1101', '王强：采购部'),
  (1, 4, 2, '1102', '王强：兼管生产部'),
  (1, 5, 2, '1101', '赵敏：采购部'),
  (1, 5, 2, '1102', '赵敏：兼管生产部'),
  (1, 6, 1, '1',    '陈杰：原料仓'),
  (1, 6, 1, '2',    '陈杰：成品仓')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- --------------------------------------------------------------
-- 六、审批节点配置（M11-5-1、M11-5-2）
-- 采购申请：单节点或签，任一采购主管通过即推进
-- 采购订单：单节点会签，两名采购主管全部通过才通过，任一驳回即整单驳回
-- 两种签方式各配一单，便于答辩现场对照演示
-- --------------------------------------------------------------
INSERT INTO approval_flow_node (bill_type, node_seq, node_name, approver_type, role_id, user_id, sign_type, status, remark) VALUES
  (1, 1, '采购主管审批', 1, 2, NULL, 1, 1, '采购申请：或签'),
  (2, 1, '采购主管会签', 1, 2, NULL, 2, 1, '采购订单：会签')
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name);
