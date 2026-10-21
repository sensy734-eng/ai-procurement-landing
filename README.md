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
| 7 起 | 见 DEV_ROADMAP.md | 待进行 |

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
交付登记只写流水、已交付数量由入库单确认回写（轮次 7）。M3-6 价格偏离关注留轮次 8。

轮次 6 交付的页面：到货验收列表（P10）、验收登记（P11，从列表页进入）。
后端接口覆盖 M4-1 至 M4-4 共 15 个功能点：到货登记（一次多行/一单多次，到货不超剩余量）、
验收判定（守恒律 合格+不合格=到货，整单结果由明细重算）、让步接收（整单动作，归采购主管，
理由必填，让步数量可入库）、生成入库单草稿（部分入库/多批次，只取合格数，让步单含不合格数）、
验收记录查询与统计（列表按仓库范围，统计为跨仓库质量视角）。验收登记用 cangku01，
让步与统计用 zhuguan01。入库单确认与库存写入在轮次 7（M5）。

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
- ui_smoke.mjs 用系统 Chrome 无头登录并逐页检查渲染，截图落在 web/scripts/screenshots/

冒烟脚本会留下 smoke 前缀的测试数据，跑完执行一次清理：

    mysql -u root < db/15_clean_smoke.sql
    mysql -u root < db/16_clean_smoke_round3.sql
    mysql -u root < db/17_clean_smoke_round4.sql
    mysql -u root < db/18_clean_smoke_round5.sql
    mysql -u root < db/19_clean_smoke_round6.sql

- 15_clean_smoke.sql 清轮次 1、2 的测试用户与角色
- 16_clean_smoke_round3.sql 清轮次 3 的物料、分类、供应商、仓库与字典测试项，按外键依赖顺序删除，操作日志按审计要求保留
- 17_clean_smoke_round4.sql 清轮次 4 的申请单、明细、审批任务与转出的订单，复原演示数据规模（3 单 / 4 任务 / 2 订单）
- 18_clean_smoke_round5.sql 清轮次 5 的订单、明细、变更历史、交付流水与审批任务，复原审批节点种子与演示数据规模（3 单 / 2 订单 / 8 物料 / 4 供应商）
- 19_clean_smoke_round6.sql 清轮次 6 的验收单、验收明细、入库单与入库明细（演示行 id=1 保留），
  并幂等兜底轮次 5 残留，复原订单行履约口径与审批任务种子，回到演示规模
  （3 申请单 / 2 订单 / 1 验收单 / 1 入库单 / 8 物料 / 4 供应商）

## 约定

- 后端按 M1~M12 分一级包，包内 Controller / Service / Mapper 三层
- 权限点编码为 模块:对象:动作，接口用 @RequirePermission 校验
- 数据范围由 DataScopeService 注入查询条件，前端与模型不参与过滤
- 代码注释标注功能点编号，论文第 5 章按编号引用
- application-local.yml 存数据库口令与后续模型 key，不进版本库
