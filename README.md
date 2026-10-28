# 融合 AI 助手的制造企业采购管理系统

毕业设计工程。主体为采购管理系统（前后端分离 Web），AI 助手为融合其中的特色功能。
业务用户端与管理后台同属一个工程，按角色与路由区分。

## 目录结构

    procurement-system/
      db/          数据库脚本，49 张表 DDL 与初始化数据，按域拆分
      server/      后端工程，Spring Boot 3.5.16 + MyBatis-Plus 3.5.17 + JDK 17
      web/         前端工程，Vue 3 + TypeScript + Vite + Element Plus
      scripts/     接口自测脚本
      DEV_ROADMAP.md  开发路线与每轮验证方式
      DEVELOPMENT_LOG.md  开发记录（系统状态快照、每轮做了什么、问题与解决），每次开发前后更新

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 本机为 E:\jak17，Maven 以它为 JAVA_HOME |
| Maven | 3.6.3 | 本机为 E:\apache-maven-3.6.3-bin\apache-maven-3.6.3 |
| Node.js | 22 | 前端构建 |
| 数据库 | MySQL 8 或 MariaDB 10.4 | 当前实测为 XAMPP 自带 MariaDB 10.4.32 |

## 初始化数据库

按顺序执行 db 目录脚本，顺序不可乱：

    cd db
    mysql -u root < 00_create_database.sql
    mysql -u root < 01_schema_a_base.sql
    mysql -u root < 02_schema_b_bill.sql
    mysql -u root < 03_schema_c_stock.sql
    mysql -u root < 04_schema_d_approval.sql
    mysql -u root < 05_schema_e_price.sql
    mysql -u root < 06_schema_f_platform.sql
    mysql -u root < 07_schema_g_kb.sql
    mysql -u root < 08_schema_h_ai.sql
    mysql -u root < 10_init_dict.sql
    mysql -u root < 11_init_platform.sql
    mysql -u root < 12_init_ai.sql
    mysql -u root < 13_init_demo.sql
    mysql -u root < 14_init_kb_demo.sql
    mysql -u root < 99_verify.sql

99_verify.sql 是校验脚本，预期 12 项全部为零违规。

## 运行过的轮次

| 轮次 | 范围 | 状态 |
|---|---|---|
| 1 | 工程骨架与认证闭环 | 已完成 |
| 2 | M11 平台与权限管理 | 已完成 |
| 3 | M1 基础数据管理 | 已完成 |
| 4 | M2 采购申请与审批引擎 | 已完成 |
| 5 | M3 采购订单管理 | 已完成 |
| 6 | M4 到货验收管理 | 已完成 |
| 7 | M5 采购入库与库存 | 已完成 |
| 8 | M6 价格参考与审批辅助 + M3-6 价格偏离关注 | 已完成 |
| 9 | M10 知识库管理 | 已完成 |
| 10 | M7 / M8 / M9 智能助手（统一对话、制度教程问答、受控查询、审计） | 已完成 |
| 11 起 | 见 DEV_ROADMAP.md | 待进行 |

轮次 2 交付的页面：用户管理（P22）、角色权限（P23）、审批配置、参数配置、日志审计（P24）。
后端接口覆盖 M11-1 至 M11-5 共 22 个功能点，其中 M11-5-3 至 M11-5-5 的审批引擎随轮次 4 的 M2 一并实现。

轮次 3 交付的页面：物料管理（P1）、供应商管理（P2）、仓库管理（P3）、字典管理（P4）。
后端接口覆盖 M1-1 至 M1-4 共 18 个功能点。物料导入导出为 CSV 模板（UTF-8 BOM，Excel 可直接打开）；
分类维护按功能清单的角色列归采购员，系统管理员不持物料与供应商权限，演示基础数据页需用 caigou01 登录。

轮次 4 交付的页面：采购申请（P5）、我的待办（P6）。
后端接口覆盖 M2-1 至 M2-4 共 20 个功能点，并落地 M11-5-3 或签/会签规则、M11-5-4 任务生成与流转、
M11-5-5 结果回写（审批引擎）。审批待办归采购主管角色（zhuguan01/zhuguan02），
采购员用 caigou01 提交与维护申请单。转订单生成的 purchase_order 记录在轮次 5（M3）提供完整维护界面。

轮次 5 交付的页面：采购订单（P7，详情抽屉即 P8）、订单待办（P9）。
后端接口覆盖 M3-1 至 M3-5 共 25 个功能点（订单反审口径裁定为"禁止而非级联"，
见 DEV_ROADMAP.md 轮次 5 差异记录）。订单审批为单节点会签（两名主管全过才推进），
交付登记只写流水、已交付数量由入库单确认回写（轮次 7）。M3-6 价格偏离关注随轮次 8 交付。

轮次 6 交付的页面：到货验收列表（P10）、验收登记（P11，从列表页进入）。
后端接口覆盖 M4-1 至 M4-4 共 15 个功能点：到货登记（一次多行/一单多次，到货不超剩余量）、
验收判定（守恒律 合格+不合格=到货，整单结果由明细重算）、让步接收（整单动作，归采购主管，
理由必填，让步数量可入库）、生成入库单草稿（部分入库/多批次，只取合格数，让步单含不合格数）、
验收记录查询与统计（列表按仓库范围，统计为跨仓库质量视角）。验收登记用 cangku01，
让步与统计用 zhuguan01。入库单确认与库存写入在轮次 7（M5）。

轮次 7 交付的页面：采购入库（P12）、库存台账（P13）、库存流水（P14）。
后端接口覆盖 M5-1 至 M5-5 共 17 个功能点：入库单维护（手工新建/编辑，批次号与库位在编辑时登记）、
确认入库（**库存唯一写入点**：同事务写余额+流水+回写订单已交付数量）、作废（归采购主管，
冲减库存并回退）、批次号规则（B+yyyyMMdd+3 位当日流水）、入库单溯源（到申请人与部门）、
库存余额与可用量（现算）、归属分组、库存预警（缺口现算）、库存流水查询与来源回溯、余额对账（归 admin）。
入库操作用 cangku01，作废与对账分别用 zhuguan01 / admin。

轮次 8 交付的页面：价格参考（P15）、价格偏离关注（P16），订单列表增加偏离列与等级筛选、
订单详情抽屉明细增加偏离列、操作列增加价格证据抽屉（审批辅助）。
后端覆盖 M6-1 至 M6-4 共 7 个功能点（参考价维护与版本链、历史成交统计快照、口径与阈值配置、
审批证据面板），并把 README 与清单中明确的 M3-6 价格偏离关注并入本轮：偏离率随订单行计算落库、
参考价变更后可重算、偏离关注清单与汇总。参考价维护 create/update 归采购主管（zhuguan01），
统计刷新归 admin（m6:param:config），证据面板归审批人（m6:evidence:view），仓管员不持 m6 权限。
口径要点：参考价改价即生成新版本（旧版留痕）、历史成交与当前报价严格分离（R6）、
偏离等级按绝对值与 sys_param 阈值（5% 关注 / 15% 显著）比较、无生效参考价不判定。

轮次 9 交付的页面：知识库一页三页签（P18 文档管理、P19 切块查看、P20 检索测试），
系统管理菜单「知识库」入口（占位路由替换为真实页面）。
后端覆盖 M10-1 至 M10-5 共 17 个功能点：文档上传解析（md 原生 / docx JDK zip+DOM /
pdf 走正文补录）、正文补录、元数据维护与版本链（同文档名多版本并存）、启用停用（停用不参与召回）、
切块（条款边界保护+超长条款句界硬切+相邻重叠）、相邻切块合并、索引重建（单文档/全量，
倒排+向量均为派生数据）、双路检索测试台（BM25 与向量并行 Top-K，命中判定与命中率统计）、
文档删除（物理删，三级同步清索引）。11 个权限点全授系统管理员（admin），其他角色不持。
口径要点：解析零依赖不引规划书外库；条款完整时块间不重叠；向量路全量候选不预滤（保证对照实验公平）；
停用语义是排除该文档而非清空检索结果。M10-5-3 引用有效性校验留轮次 10 随答案链路实现。

## 启动后端

    cd server
    cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
    # 修改 application-local.yml 中的数据库账号密码
    mvn -DskipTests package
    java -jar target/procurement-server-1.0.0.jar

后端端口 8080。若所在环境注入了 SERVER__PORT 之类的变量，用 --server.port=8080 显式指定。

## 启动前端

    cd web
    npm install
    npm run dev

前端端口 5173，/api 代理到 8080。

## 演示账号

密码均为 123456，全部为自造演示数据。

| 账号 | 角色 | 数据范围 |
|---|---|---|
| admin | 系统管理员 | 全量 |
| caigou01 | 采购员（采购部） | 部门 1101 |
| caigou02 | 采购员（生产部） | 部门 1102 |
| zhuguan01 | 采购主管 | 部门 1101、1102 |
| zhuguan02 | 采购主管 | 部门 1101、1102 |
| cangku01 | 仓管员 | 仓库 1、2 |

## 自测

    python scripts/smoke_round1.py
    python scripts/smoke_round2.py
    python scripts/smoke_round3.py
    python scripts/smoke_round4.py
    python scripts/smoke_round5.py
    python scripts/smoke_round6.py
    python scripts/smoke_round7.py
    python scripts/smoke_round8.py
    python scripts/smoke_round9.py
    python scripts/smoke_round10.py
    cd web && node scripts/ui_smoke.mjs

- smoke_round1.py 覆盖登录、会话、权限拦截、数据范围与用户列表，20 项断言
- smoke_round2.py 覆盖 M11 全部接口，67 项断言，包含越权与非法参数用例
- smoke_round3.py 覆盖 M1 全部接口，78 项断言，含编码唯一冲突、停用不可选、导入明细、附件校验与角色权限边界
- smoke_round4.py 覆盖 M2 全部接口与审批引擎，101 项断言，含或签会签流转、驳回撤回、按行退回拆分、转订单合并与并发提交
- smoke_round5.py 覆盖 M3 全部接口，131 项断言，含会签双人规则、反审与作废前置（无交付/验收/入库）、
  交付累计守恒、履约重算、超期筛选、变更前后值留痕、按行关闭、数据范围、溯源与 M2 回归
- smoke_round6.py 覆盖 M4 全部接口，72 项断言，含数量守恒（到货=合格+不合格）、到货与入库双配额
  （草稿也占配额）、一单多次与一次多行、部分入库与多批次、让步接收（主管专属、理由必填、
  让步可入库/不合格不入库）、结果重算、数据范围（仓管员按仓库、部门用户失败关闭）、
  统计口径、权限边界与 M3 回归（有验收/入库记录的订单不可反审不可作废）
- smoke_round7.py 覆盖 M5 全部接口，99 项断言，含库存唯一写入点（确认同事务写余额/流水/回写
  delivered）、重复确认被拒、作废冲减与配额释放、批次号规则、溯源链、可用量现算、归属聚合、
  预警缺口、对账零差异、权限边界与 M2/M3/M4 回归
- smoke_round8.py 覆盖 M6 与 M3-6 偏离，46 项断言，含参考价版本链（立即生效停用旧版/预售并存/
  原地改备注）、统计快照刷新与无样本不伪造、偏离建行改行与等于参考价回落、无参考价不判定、
  关注清单与汇总与等级筛选、参考价变更后重算、证据面板 as-of 还原、权限边界
- smoke_round9.py 覆盖 M10 全部接口，47 项断言，含 md/docx/pdf 上传与自动解析、pdf 补录边界、
  条款边界保护与块长约束、超长条款句界硬切与相邻重叠、相邻/不相邻切块合并、单文档与全量索引重建、
  按名称/分类/状态过滤、版本链与同文档名并存、停用后不召回、删除清索引、双路命中与命中率统计、
  文件下载、权限边界（四角色 + 无 token 401）
- smoke_round10.py 覆盖 M7/M8/M9 全部接口，98 项断言，含三类路由（制度/教程/数据）、
  五类意图与置信度、七种结果码全覆盖（Success/NoData/NotFound/Ambiguous/NoPermission/
  Unsupported/InvalidParameter）、参数白名单拦截（被拒参数逐条给原因）、实体归一与多候选消歧、
  多轮指代继承、参数修正重跑与结果对比、双路召回对照、条款原文直引、步骤化教程与功能入口、
  引用有效性校验（切块存在/属所引文档）与覆盖率统计、拒答判定与拒答话术、数据范围注入、
  只读约束（问答前后订单与库存不变）、四角色权限边界与无 token 401
- ui_smoke.mjs 用系统 Chrome 无头登录并逐页检查渲染（当前 25 页），截图落在 web/scripts/screenshots/

冒烟脚本会留下 smoke 前缀的测试数据，跑完执行一次清理：

    mysql -u root < db/15_clean_smoke.sql
    mysql -u root < db/16_clean_smoke_round3.sql
    mysql -u root < db/17_clean_smoke_round4.sql
    mysql -u root < db/18_clean_smoke_round5.sql
    mysql -u root < db/19_clean_smoke_round6.sql
    mysql -u root < db/20_clean_smoke_round7.sql
    mysql -u root < db/21_clean_smoke_round8.sql
    mysql -u root < db/22_clean_smoke_round9.sql
    mysql -u root < db/23_clean_smoke_round10.sql

- 15_clean_smoke.sql 清轮次 1、2 的测试用户与角色
- 16_clean_smoke_round3.sql 清轮次 3 的物料、分类、供应商、仓库与字典测试项，按外键依赖顺序删除，操作日志按审计要求保留
- 17_clean_smoke_round4.sql 清轮次 4 的申请单、明细、审批任务与转出的订单，复原演示数据规模（3 单 / 4 任务 / 2 订单）
- 18_clean_smoke_round5.sql 清轮次 5 的订单、明细、变更历史、交付流水与审批任务，复原审批节点种子与演示数据规模（3 单 / 2 订单 / 8 物料 / 4 供应商）
- 19_clean_smoke_round6.sql 清轮次 6 的验收单、验收明细、入库单与入库明细（演示行 id=1 保留），
  并幂等兜底轮次 5 残留，复原订单行履约口径与审批任务种子，回到演示规模
  （3 申请单 / 2 订单 / 1 验收单 / 1 入库单 / 8 物料 / 4 供应商）
- 20_clean_smoke_round7.sql 清轮次 7 的入库单、入库明细、库存流水，冲减的余额行随流水清掉，
  复原订单行 delivered_qty 与履约口径，回到演示规模（3 申请单 / 2 订单 / 1 验收单 /
  1 入库单 / 1 库存余额 / 1 流水 / 8 物料 / 4 供应商）
- 21_clean_smoke_round8.sql 清轮次 8 的冒烟订单与参考价/快照测试行，复原参考价 4 行（均 V1 生效）、
  统计快照 4 行与订单行偏离种子值，回到演示规模（3 申请单 / 2 订单 / 4 参考价 / 4 快照）
- 22_clean_smoke_round9.sql 物理删轮次 9 的冒烟文档、切块与倒排索引（含 is_deleted=1 兜底行），
  清空派生索引与向量、检索测试记录，复原演示知识库（2 文档 / 10 切块，索引为空）
- 23_clean_smoke_round10.sql 清轮次 10 的会话、消息与查询审计（三张表都是过程数据，
  演示前应为空），重置自增并复原意图白名单启用状态；审计是「受控」的可验证凭据，
  清空是为了不把调试记录当结论

## 约定

- 后端按 M1~M12 分一级包，包内 Controller / Service / Mapper 三层
- 权限点编码为 模块:对象:动作，接口用 @RequirePermission 校验
- 数据范围由 DataScopeService 注入查询条件，前端与模型不参与过滤
- 代码注释标注功能点编号，论文第 5 章按编号引用
- application-local.yml 存数据库口令与后续模型 key，不进版本库
