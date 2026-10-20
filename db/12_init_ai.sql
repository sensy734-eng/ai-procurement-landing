-- ==============================================================
-- 文件：12_init_ai.sql
-- 作用：系统参数、意图白名单、意图参数规格、实体别名初始化
-- 说明：参数值是业务口径的唯一来源，一旦配置不得随查询变化；
--       意图只做五类，每类映射到一个确定的后端查询服务，
--       模型只负责把问句解析成这张表允许的参数，不参与选表选字段
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- 一、系统参数
-- --------------------------------------------------------------
INSERT INTO sys_param (param_code, param_name, param_value, param_type, param_group, editable, remark) VALUES
  ('session_timeout_minutes',        '会话超时分钟数',       '120',            'int',     'common',  1, 'M11-1-2 会话保持与超时'),
  ('pwd_min_length',                 '密码最小长度',         '8',              'int',     'common',  1, 'M11-1-4 修改密码校验'),
  ('price_stat_window_type',         '成交价统计窗口类型',   'LAST_N_DEALS',   'string',  'price',   1, 'M6-1-5 口径配置：LAST_N_DEALS 按成交次数、LAST_N_DAYS 按天数'),
  ('price_stat_window_value',        '成交价统计窗口取值',   '5',              'int',     'price',   1, 'M6-1-5 与窗口类型配合，默认近 5 次成交'),
  ('deviation_threshold_focus',      '偏离关注阈值',         '0.05',           'decimal', 'price',   1, 'M6-2-2 偏离率超过 5% 记为关注'),
  ('deviation_threshold_significant','偏离显著阈值',         '0.15',           'decimal', 'price',   1, 'M6-2-2 偏离率超过 15% 记为显著'),
  ('stock_alert_default',            '默认库存预警下限',     '0',              'decimal', 'stock',   1, 'M11-4-2 物料未单独设置下限时使用'),
  ('bill_no_rule_request',           '采购申请单号规则',     'CG-SQ-{yyyyMMdd}-{seq:4}', 'string', 'bill_no', 1, 'M11-4-3 前缀日期流水'),
  ('bill_no_rule_order',             '采购订单号规则',       'CG-DD-{yyyyMMdd}-{seq:4}', 'string', 'bill_no', 1, 'M11-4-3'),
  ('bill_no_rule_inspection',        '到货验收单号规则',     'CG-YS-{yyyyMMdd}-{seq:4}', 'string', 'bill_no', 1, 'M11-4-3'),
  ('bill_no_rule_inbound',           '采购入库单号规则',     'CG-RK-{yyyyMMdd}-{seq:4}', 'string', 'bill_no', 1, 'M11-4-3'),
  ('kb_chunk_size',                  '切块目标字数',         '500',            'int',     'kb',      1, 'M10-2-1 块长 300 至 600 字'),
  ('kb_chunk_overlap',               '切块重叠字数',         '50',             'int',     'kb',      1, 'M10-2-1 相邻块重叠'),
  ('kb_recall_topk',                 '召回条数',             '5',              'int',     'kb',      1, 'M8-1-1 稀疏与向量两路各取 Top-K'),
  ('kb_score_threshold',             '拒答相似度阈值',       '0.35',           'decimal', 'kb',      1, 'M8-5-1 最高相似度低于此值判为无可靠依据'),
  ('ai_intent_confidence_threshold', '意图置信度阈值',       '0.60',           'decimal', 'ai',      1, 'M9-1-2 低于此值转澄清或 Unsupported'),
  ('ai_history_round',               '多轮上下文保留轮数',   '10',             'int',     'ai',      1, 'M7-1-3 指代消解使用的历史轮数')
ON DUPLICATE KEY UPDATE param_value = VALUES(param_value);

-- --------------------------------------------------------------
-- 二、意图白名单（五类，一版范围）
-- --------------------------------------------------------------
INSERT INTO ai_intent (id, intent_code, intent_name, service_code, question_samples, enabled, sort_no, remark) VALUES
  (1, 'order_status',           '订单状态查询',       'svc_order_status_query',
   '订单 CG-DD-20261011-0001 现在什么状态|查一下这张订单审批通过没有|我下的订单到哪一步了', 1, 1,
   '返回单据生命周期状态、审批结果状态与业务履约状态三个字段，不合并成一句话'),
  (2, 'delivery_progress',      '交付进度查询',       'svc_delivery_progress_query',
   '哪些订单还没交货|近 30 天超期未交付的订单|供应商 A 的订单交付到哪了', 1, 2,
   '超期天数由后端按当前日期与交货期计算'),
  (3, 'purchase_price_history', '历史采购价格查询',   'svc_price_history_query',
   '内六角螺栓最近成交价是多少|这个物料近半年采购均价|不锈钢板从哪些供应商买过', 1, 3,
   '只返回历史成交口径，不表述为当前供应商报价'),
  (4, 'stock_available',        '库存余量与可用量查询', 'svc_stock_available_query',
   '内六角螺栓还有多少库存|原料仓的可用量是多少|这个项目的库存还有多少', 1, 4,
   '可用量等于数量减冻结量减预留量，由后端实时计算'),
  (5, 'supplier_delivery',      '供应商历史交付情况查询', 'svc_supplier_delivery_query',
   '供应商 SUP-001 的交付准时率怎么样|这家供应商历史交付情况|A 厂有没有延期交货过', 1, 5,
   '按历史订单的交付与超期记录统计，不代表当前履约承诺')
ON DUPLICATE KEY UPDATE intent_name = VALUES(intent_name);

-- --------------------------------------------------------------
-- 三、意图参数规格（白名单校验依据）
-- operator_set 为允许的操作符，enum_values 为允许的取值域；
-- 模型输出若越界，直接返回 InvalidParameter，不进入执行环节
-- --------------------------------------------------------------
INSERT INTO ai_intent_param (id, intent_id, param_code, param_name, data_type, operator_set, required, enum_values, sort_no, remark) VALUES
  -- 1 订单状态查询
  (101, 1, 'order_no',         '订单号',     'string',  'eq,like',        0, NULL,           1, NULL),
  (102, 1, 'supplier_id',      '供应商',     'long',    'eq',             0, NULL,           2, NULL),
  (103, 1, 'date_from',        '开始日期',   'date',    'gte,between',    0, NULL,           3, NULL),
  (104, 1, 'date_to',          '结束日期',   'date',    'lte,between',    0, NULL,           4, NULL),
  (105, 1, 'lifecycle_status', '生命周期状态','int',    'eq,in',          0, '1,2,3,4,5',    5, '草稿 1、已提交 2、已审核 3、已关闭 4、已作废 5'),
  (106, 1, 'audit_status',     '审批结果状态','int',    'eq,in',          0, '1,2,3',        6, '未审核 1、通过 2、驳回 3'),
  (107, 1, 'fulfill_status',   '履约状态',   'int',     'eq,in',          0, '1,2,3,4',      7, '待交付 1、部分交付 2、全部交付 3、已关闭 4'),
  -- 2 交付进度查询
  (201, 2, 'order_no',         '订单号',     'string',  'eq,like',        0, NULL,           1, NULL),
  (202, 2, 'supplier_id',      '供应商',     'long',    'eq',             0, NULL,           2, NULL),
  (203, 2, 'date_from',        '开始日期',   'date',    'gte,between',    0, NULL,           3, NULL),
  (204, 2, 'date_to',          '结束日期',   'date',    'lte,between',    0, NULL,           4, NULL),
  (205, 2, 'overdue_only',     '仅看超期',   'boolean', 'eq',             0, 'true,false',   5, '为真时只返回当前日期已过交货期且未交付完的订单'),
  -- 3 历史采购价格查询
  (301, 3, 'material_id',      '物料',       'long',    'eq',             1, NULL,           1, '必填，缺失时返回澄清'),
  (302, 3, 'supplier_id',      '供应商',     'long',    'eq',             0, NULL,           2, NULL),
  (303, 3, 'date_from',        '开始日期',   'date',    'gte,between',    0, NULL,           3, NULL),
  (304, 3, 'date_to',          '结束日期',   'date',    'lte,between',    0, NULL,           4, NULL),
  -- 4 库存余量与可用量查询
  (401, 4, 'material_id',      '物料',       'long',    'eq',             1, NULL,           1, '必填，缺失时返回澄清'),
  (402, 4, 'warehouse_id',     '仓库',       'long',    'eq',             0, NULL,           2, NULL),
  (403, 4, 'owner_type',       '归属类型',   'int',     'eq,in',          0, '1,2,3',        3, '公共 1、项目 2、工单 3'),
  -- 5 供应商历史交付情况查询
  (501, 5, 'supplier_id',      '供应商',     'long',    'eq',             1, NULL,           1, '必填，缺失时返回澄清'),
  (502, 5, 'date_from',        '开始日期',   'date',    'gte,between',    0, NULL,           2, NULL),
  (503, 5, 'date_to',          '结束日期',   'date',    'lte,between',    0, NULL,           3, NULL)
ON DUPLICATE KEY UPDATE param_name = VALUES(param_name);

-- --------------------------------------------------------------
-- 四、实体别名（演示数据，支撑口语化名称归一）
-- 同一别名可对应多个实体，匹配到多个候选时返回 Ambiguous 并列出候选
-- --------------------------------------------------------------
INSERT INTO ai_entity_alias (entity_type, entity_id, entity_no, alias_name, source, hit_count, remark) VALUES
  (1, 1, 'M-1001', '内六角螺栓',   1, 0, '演示别名'),
  (1, 1, 'M-1001', '螺栓',         1, 0, '演示别名，可能一词多物'),
  (1, 2, 'M-1002', '不锈钢板',     1, 0, '演示别名'),
  (2, 1, 'SUP-001', 'A厂',         1, 0, '演示别名'),
  (2, 2, 'SUP-002', 'B厂',         1, 0, '演示别名'),
  (3, 1, 'WH-001', '原料仓',       1, 0, '演示别名'),
  (3, 2, 'WH-002', '成品仓',       1, 0, '演示别名')
ON DUPLICATE KEY UPDATE alias_name = VALUES(alias_name);
