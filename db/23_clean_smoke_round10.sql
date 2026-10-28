-- ==============================================================
-- 文件：23_clean_smoke_round10.sql
-- 作用：轮次 10 冒烟后清理，复原智能助手演示基线
-- 基线口径（12_init_ai.sql + 14_init_kb_demo.sql 执行后）：
--   会话 0、消息 0、查询审计 0（三张表都是过程数据，演示前应为空）；
--   意图白名单 5 条、意图参数 24 条、实体别名不变；
--   知识文档 2 篇、切块 10 个、倒排索引与向量为空。
-- 说明：ai_session 与 ai_message、ai_query_audit 之间无外键约束，
--       删除顺序按依赖写只为可读性；审计表是"受控"的可验证凭据，
--       开发期用它核对被拒参数与数据范围，演示前清空避免把调试记录当结论。
-- 前置提醒：本脚本只清助手侧过程数据，业务表（订单/库存/知识库）不受影响；
--           若上一轮知识库冒烟残留未清，先执行 22_clean_smoke_round9.sql。
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- 一、清查询审计（M9-5-5 的落库凭据，属于过程数据）
-- --------------------------------------------------------------
DELETE FROM ai_query_audit;

-- --------------------------------------------------------------
-- 二、清会话消息与会话（先子后父）
-- --------------------------------------------------------------
DELETE FROM ai_message;
DELETE FROM ai_session;

-- --------------------------------------------------------------
-- 三、重置自增，让演示时的会话号从 1 开始
-- --------------------------------------------------------------
ALTER TABLE ai_message AUTO_INCREMENT = 1;
ALTER TABLE ai_session AUTO_INCREMENT = 1;
ALTER TABLE ai_query_audit AUTO_INCREMENT = 1;

-- --------------------------------------------------------------
-- 四、兜底：清理逻辑删除残留行（正常流程不应存在）
-- --------------------------------------------------------------
DELETE FROM ai_message WHERE is_deleted = 1;
DELETE FROM ai_session WHERE is_deleted = 1;
DELETE FROM ai_query_audit WHERE is_deleted = 1;

-- --------------------------------------------------------------
-- 五、复原意图白名单与参数规格的启用状态（开发期调试可能改过）
-- --------------------------------------------------------------
UPDATE ai_intent SET enabled = 1 WHERE intent_code IN
  ('order_status', 'delivery_progress', 'purchase_price_history', 'stock_available', 'supplier_delivery');

-- --------------------------------------------------------------
-- 六、验证
-- --------------------------------------------------------------
SELECT '会话' AS tbl, COUNT(*) AS cnt FROM ai_session
UNION ALL SELECT '消息', COUNT(*) FROM ai_message
UNION ALL SELECT '查询审计', COUNT(*) FROM ai_query_audit
UNION ALL SELECT '启用意图', COUNT(*) FROM ai_intent WHERE enabled = 1 AND is_deleted = 0
UNION ALL SELECT '意图参数', COUNT(*) FROM ai_intent_param WHERE is_deleted = 0
UNION ALL SELECT '知识文档', COUNT(*) FROM kb_document
UNION ALL SELECT '知识切块', COUNT(*) FROM kb_chunk;
