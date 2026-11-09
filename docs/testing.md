# 测试与评测

工具链固定为 JDK 17、Maven Wrapper 3.9.9、Node 22、Python 3.12、MySQL 8.4。Windows 使用 `server/mvnw.cmd`，Unix 使用 `server/mvnw`。CI 执行后端测试、前端类型检查与构建、API 清单一致性和冻结数据集检查。

```powershell
cd server
.\mvnw.cmd verify
cd ../web
npm ci
npm run typecheck
npm run build
cd ..
python scripts/gen_api_list.py --check
python scripts/ai_eval_runner.py --check
python scripts/check_product_artifacts.py
python scripts/check_doc_links.py
python -m unittest discover -s scripts/tests -v
python scripts/check_database_tests.py
docker compose up --build -d
python scripts/smoke_workbench.py
python scripts/smoke_ai_tasks.py
python scripts/ai_task_eval_runner.py --check
```

数值和引用回归覆盖“30被改为3”、工作日与自然日错换、万元等价表达、中文数字、非法引用、越界引用和跨未引用片段借数字。模拟模型异常与真实模型质量评测必须分别报告。

`scripts/ai_eval_dataset.json` 的88条数据保持原始基线。历史报告位于 `docs/archive/`，不得将旧分数当作本版成绩。新制度回答使用生成、参考和拒答的期望；正确降级不是生成成功。教程要求有序步骤时，缺少编号结构或无法验证顺序应失败。

检索评测集及语料位于 `docs/evaluation/`。同一语义问题的两种改写留在同一集合，开发集与保留集各30条。`frozen-sha256.json` 记录冻结哈希；保留集不得参与调参。四组实验通过 `scripts/rag_eval_runner.py` 执行严格策略并输出原始逐题结果。真实模型、数据库守卫和环境不可用时必须明确说明，不能把跳过项计作通过。

## 三种运行模式

```powershell
# 仅测试替身；JUnit 服务对象和本机 HTTP stub，无生产故障注入接口。
python scripts/ai_eval_runner.py --mode fault --output-dir artifacts/fault-run

# 已运行的隔离 Compose 演示环境。守卫通过容器内 MySQL 客户端执行只读 SQL。
$env:EVAL_BASE_URL = 'http://127.0.0.1:18080/api'
$env:EVAL_USE_COMPOSE_DB = 'true'
python scripts/ai_eval_runner.py --mode conservative
# 启用真实聊天模型后另跑，不与模拟故障结果混合。
python scripts/ai_eval_runner.py --mode generated --repeats 3

# 调参阶段只看 dev；方案冻结后才运行 holdout。
python scripts/rag_eval_runner.py --mode dev --repeats 3
python scripts/rag_eval_runner.py --mode holdout --repeats 3
```

原88题保留原始断言，并不保证新增语料下继续得到旧分数；各模式独立报告。旧题需要的特定业务fixture应只在专门评测数据卷中准备，不能在用户业务库执行旧清理脚本。

`--mode fault` 是隔离服务对象级回归，不伪装成 HTTP 端到端实验；报告明确标记 simulated_faults。`--no-db` 或守卫读取失败时报告为 UNVERIFIED，不能据此宣布安全验收通过。MySQL集成测试没有运行时，`check_database_tests.py` 明确失败，即使 Maven 因 Testcontainers 跳过而退出0。

本机 Windows 如果 Java 报 `Unable to establish loopback connection`，可将 `JAVA_TOOL_OPTIONS` 中的 `jdk.net.unixdomain.tmpdir` 指向一个已存在、无短路径别名的事务工件目录。本次环境将它指向 `artifacts/productization` 后 HTTP 替身测试通过；CI 的 Linux 环境不需要此配置。

`smoke_workbench.py` 仅用于默认模型关闭的隔离演示环境，经真实反向代理携带 Origin 头检查登录、权限隔离、历史访问与参考降级，不调用云端。报告不保存登录token。当前实测结果和原88题未通过项见[实施记录](implementation-status.md)。

成文与查询嵌入 tokens 按供应商响应记录，缺失为 null；报告明确不包含路由、规划和失败请求的未知用量。只有提供 `EMBEDDING_PRICE_PER_MILLION`（及币种）时才估算已知嵌入用量费用。
