# 本地发布

首次启动：`docker compose up --build -d`。访问 `http://127.0.0.1:18080`。Compose 使用 MySQL 8.4、Java 17 后端和 Node 22 构建的 Nginx 前端。只有网页端口映射到宿主机回环地址；数据库不暴露宿主机端口。

数据库与后端统一使用 Asia/Shanghai 时区。启动后执行 `python scripts/smoke_workbench.py` 验证浏览器同源代理及默认无模型流程。

查看状态：`docker compose ps`；查看日志：`docker compose logs --tail 100 backend`；停止：`docker compose down`。正常重启保留数据。

初始化仅运行于本 Compose 的空数据库卷，明确排除 `db/00_create_database.sql`。已有数据库只执行 `db/migrations/` 的增量脚本，先备份并确认所选数据库。语义向量迁移只建新表；演示语料迁移使用保留ID1001起和切块ID10001起，若已有系统占用这些ID应先调整演示迁移，不可覆盖。

显式重置演示数据：`docker compose down -v`，再启动。该命令删除此 Compose 项目的数据库和上传文件卷。

## 配置真实聊天模型

订单审查助手、采购需求起草助手与制度问答共用聊天模型配置。两个任务助手不要求先启用云端语义嵌入，检索可保持 `bm25_hash`。

在仓库根目录执行以下 PowerShell 命令，已有 `.env` 时保留原文件：

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

以 DeepSeek 为例，在本机 `.env` 中填写：

```dotenv
LLM_ENABLED=true
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-flash
LLM_API_KEY=替换为你在服务商平台创建的真实密钥
LLM_THINKING_TYPE=disabled
KB_RETRIEVAL_STRATEGY=bm25_hash
```

截至 2026-11-09，DeepSeek 官方文档列出 `deepseek-flash`，并支持 `thinking.type=disabled`。本项目通过 `LLM_THINKING_TYPE` 透传该参数，减少有限调用预算被推理过程占用的情况。其他兼容服务需使用其实际基础地址和模型 ID；不支持 `thinking` 参数的服务应将 `LLM_THINKING_TYPE` 留空。网关自行追加 `/chat/completions`。密钥仅保存在本机，`.env` 已被 Git 忽略；不要粘贴到聊天、截图或提交。

保存后重新创建后端和网页容器，加载新环境并刷新 Nginx 上游：

```powershell
docker compose up -d --no-deps --force-recreate backend web
docker compose ps
```

只修改环境配置无需重新构建镜像；单纯 `docker compose restart` 不会加载新的容器环境。上述命令不重建数据库服务，不删除数据卷。后端启动后访问 `http://127.0.0.1:18080`。

使用 `ai_buyer / 123456` 登录，在需求起草助手输入自然语言，例如“下周五需要二百件AI演示螺栓，用于公共领用”，再人工确认候选物料。订单详情中可运行 AI 订单审查。返回 `mode=generated` 才说明本次使用了模型生成；`facts_only`、`rule_template` 或 `manual_required` 表示降级或需要补充信息，不应算成真实模型成功案例。

管理员可带登录令牌请求 `GET /api/assistant/llm/health` 检查 `enabled`、`available` 和 `probe.ok`。该接口需要 `m9:audit:list` 权限，探针会实际调用模型并消耗少量额度；`available=true` 仅表示配置齐全，不能替代 `probe.ok=true`。

真实任务评测执行 `python scripts/ai_task_eval_runner.py --repeats 3`，默认读取 12 条开发场景并保存每次原始响应。报告写入 `artifacts/ai-task-assistants/real-model-*`，语义质量仍需人工核对。没有可用凭据时只能完成离线与模拟故障验证，不能宣称真实模型评测通过。

## 可选语义嵌入与运维

语义嵌入单独配置：`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL=text-embedding-v4`、`EMBEDDING_DIMENSIONS=1024`。不启用时保留 `KB_RETRIEVAL_STRATEGY=bm25_hash`，无需填写嵌入密钥。

语义索引需由具有 `m10:index:rebuild` 的账号调用现有重建API，附 `target=semantic`。构建完成后才将 `KB_RETRIEVAL_STRATEGY` 设置为 `bm25_semantic` 或 `semantic`，并使用前述重新创建容器命令加载配置。默认 `bm25_hash`，默认重建目标 `hash`。

检索测试API `POST /api/m10/retrieval-tests` 支持 `strategy`、`strict`；策略取 `bm25`、`bm25_hash`、`semantic`、`bm25_semantic`。`strict=true` 时失败即终止。不要在正式产品服务上做故障注入；故障场景只在隔离测试中使用模拟网关。

增量升级使用 `docker compose exec db sh /workbench-deploy/migrate.sh`；已应用脚本记录哈希，不允许原地修改。MySQL DDL 不保证整份脚本原子性，失败时先核对迁移记录和实际列结构。

库存与入库现在严格校验仓库数据范围。拥有查询权限但只配置部门范围的账号，需要管理员另外授予仓库范围；不会因仓库集合为空而获得全仓库可见性。角色、仓库或部门授权变化后，旧的 AI 数据结果与上下文停止复用，用户需重新查询。旧知识消息仍按原格式展示；无法证明权限的旧数据快照保守隐藏。

发布前将 `BUILD_COMMIT` 配置为本次被测提交。提交前的本地报告同时记录 dirty 状态与完整源文件摘要，不能将旧 HEAD 冒充新增代码的发布版本。
