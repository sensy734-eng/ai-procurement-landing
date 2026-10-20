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
| 3 起 | 见 DEV_ROADMAP.md | 待进行 |

轮次 2 交付的页面：用户管理（P22）、角色权限（P23）、审批配置、参数配置、日志审计（P24）。
后端接口覆盖 M11-1 至 M11-5 共 22 个功能点，其中 M11-5-3 至 M11-5-5 的审批引擎随轮次 4 的 M2 一并实现。

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
    cd web && node scripts/ui_smoke.mjs

- smoke_round1.py 覆盖登录、会话、权限拦截、数据范围与用户列表，20 项断言
- smoke_round2.py 覆盖 M11 全部接口，67 项断言，包含越权与非法参数用例
- ui_smoke.mjs 用系统 Chrome 无头登录并逐页检查渲染，截图落在 web/scripts/screenshots/

冒烟脚本会留下 smoke 前缀的测试用户与角色，跑完执行一次清理：

    mysql -u root < db/15_clean_smoke.sql

## 约定

- 后端按 M1~M12 分一级包，包内 Controller / Service / Mapper 三层
- 权限点编码为 模块:对象:动作，接口用 @RequirePermission 校验
- 数据范围由 DataScopeService 注入查询条件，前端与模型不参与过滤
- 代码注释标注功能点编号，论文第 5 章按编号引用
- application-local.yml 存数据库口令与后续模型 key，不进版本库
