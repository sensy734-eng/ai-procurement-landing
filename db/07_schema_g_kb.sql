-- ==============================================================
-- 文件：07_schema_g_kb.sql
-- 域：G 知识库（5 张表）  对应模块：M10，被 M8 使用
-- 文档删除采用物理删除并同步清理索引（规划书 5.1 第 10 条例外），
-- 其余表仍保留 is_deleted 以统一结构
-- ==============================================================
SET NAMES utf8mb4;
USE procurement_db;

-- --------------------------------------------------------------
-- G-1 知识文档 kb_document（M10-1、M10-4）
-- 同一制度多版本并存，检索按生效日期优先召回
-- --------------------------------------------------------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  doc_name       VARCHAR(200) NOT NULL                COMMENT '文档名称',
  doc_type       TINYINT      NOT NULL                COMMENT '文档类型：1 制度、2 规范、3 操作手册、4 流程说明',
  version        VARCHAR(20)  NOT NULL DEFAULT '1.0'  COMMENT '文档版本号',
  effective_date DATE         DEFAULT NULL            COMMENT '生效日期，版本优先召回依据',
  expire_date    DATE         DEFAULT NULL            COMMENT '失效日期',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用、2 停用、3 失效；停用与失效不参与默认召回（M10-4-3、M8-4-3）',
  file_path      VARCHAR(300) DEFAULT NULL            COMMENT '文件存储路径',
  file_type      VARCHAR(20)  DEFAULT NULL            COMMENT '文件类型：pdf、docx、md',
  file_size      BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小，单位字节',
  parse_status   TINYINT      NOT NULL DEFAULT 1      COMMENT '解析状态：1 待解析、2 解析中、3 解析成功、4 解析失败',
  chunk_count    INT          NOT NULL DEFAULT 0      COMMENT '切块数量',
  tags           VARCHAR(200) DEFAULT NULL            COMMENT '标签，逗号分隔',
  upload_by      BIGINT       DEFAULT NULL            COMMENT '上传人id',
  upload_at      DATETIME     DEFAULT NULL            COMMENT '上传时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除；文档删除走物理删除并清理索引',
  version_lock   INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号，命名为 version_lock 以免与文档版本 version 冲突',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_doc_name (doc_name),
  KEY idx_doc_type (doc_type),
  KEY idx_doc_status (status),
  KEY idx_doc_effective (effective_date, expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识文档 M10-1';

-- --------------------------------------------------------------
-- G-2 文档切块 kb_chunk（M10-2、M10-3-1）
-- 初始切分参数：块长 300 至 600 字，相邻块重叠 50 字，不跨带编号条款切断
-- --------------------------------------------------------------
DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  doc_id        BIGINT       NOT NULL                COMMENT '所属文档id',
  chunk_index   SMALLINT     NOT NULL                COMMENT '切块序号，文档内从 1 开始',
  chunk_text    MEDIUMTEXT   NOT NULL                COMMENT '切块文本',
  section_no    VARCHAR(50)  DEFAULT NULL            COMMENT '章节号，如 3.2.1',
  section_title VARCHAR(200) DEFAULT NULL            COMMENT '章节标题',
  char_count    INT          NOT NULL DEFAULT 0      COMMENT '字符数',
  embedding     MEDIUMBLOB   DEFAULT NULL            COMMENT '向量，float32 数组序列化存放',
  vector_model  VARCHAR(50)  DEFAULT NULL            COMMENT '向量化模型标识',
  vector_dim    SMALLINT     NOT NULL DEFAULT 0      COMMENT '向量维度',
  is_deleted    TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version_lock  INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_index (doc_id, chunk_index),
  KEY idx_chunk_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档切块 M10-2';

-- --------------------------------------------------------------
-- G-3 稀疏检索倒排索引 kb_sparse_index（M10-3-2）
-- token 为中文按字符二元组切分后的词元，与检索端分词方式一致
-- --------------------------------------------------------------
DROP TABLE IF EXISTS kb_sparse_index;
CREATE TABLE kb_sparse_index (
  id       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  chunk_id BIGINT      NOT NULL                COMMENT '切块id',
  token    VARCHAR(20) NOT NULL                COMMENT '词元，中文双字词',
  tf       SMALLINT    NOT NULL DEFAULT 1      COMMENT '词频',
  is_deleted TINYINT   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  version  INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sparse_chunk_token (chunk_id, token),
  KEY idx_sparse_token (token),
  KEY idx_sparse_chunk (chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='稀疏检索倒排索引 M10-3-2';

-- --------------------------------------------------------------
-- G-4 拒答记录 kb_reject_log（M8-5-3）
-- 拒答记录同时是知识库补充的输入
-- --------------------------------------------------------------
DROP TABLE IF EXISTS kb_reject_log;
CREATE TABLE kb_reject_log (
  id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  question   VARCHAR(500)  NOT NULL                COMMENT '原始问句',
  max_score  DECIMAL(9,4)  NOT NULL DEFAULT 0.0000 COMMENT '最高相似度得分',
  threshold  DECIMAL(9,4)  NOT NULL DEFAULT 0.0000 COMMENT '当时的拒答阈值',
  user_id    BIGINT        DEFAULT NULL            COMMENT '提问用户id',
  session_id BIGINT        DEFAULT NULL            COMMENT '会话id',
  handled    TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已处理：0 未处理、1 已补充知识库、2 判定无需补充',
  is_deleted TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_reject_time (created_at),
  KEY idx_reject_handled (handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拒答记录 M8-5-3';

-- --------------------------------------------------------------
-- G-5 检索测试记录 kb_retrieval_test（M10-5-1、M10-5-2）
-- 支撑稀疏与向量两路 Top-K 命中率对照实验
-- --------------------------------------------------------------
DROP TABLE IF EXISTS kb_retrieval_test;
CREATE TABLE kb_retrieval_test (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  question       VARCHAR(500) NOT NULL                COMMENT '测试问句',
  expect_doc_id  BIGINT       DEFAULT NULL            COMMENT '期望命中的文档id',
  expect_chunk_id BIGINT      DEFAULT NULL            COMMENT '期望命中的切块id',
  bm25_topk      TEXT         DEFAULT NULL            COMMENT '稀疏检索 Top-K 结果，JSON 数组',
  vector_topk    TEXT         DEFAULT NULL            COMMENT '向量检索 Top-K 结果，JSON 数组',
  hit_flag       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否命中：0 未命中、1 命中',
  hit_rank       SMALLINT     DEFAULT NULL            COMMENT '命中位置，未命中为空',
  hit_route      TINYINT      DEFAULT NULL            COMMENT '命中的检索路：1 稀疏、2 向量、3 两路均命中',
  test_batch     VARCHAR(50)  DEFAULT NULL            COMMENT '测试批次标识，用于分组统计',
  operator_id    BIGINT       DEFAULT NULL            COMMENT '测试人id',
  is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除、1 已删除',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_test_batch (test_batch),
  KEY idx_test_hit (hit_flag),
  KEY idx_test_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检索测试记录 M10-5';
