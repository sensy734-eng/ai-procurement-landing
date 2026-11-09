# 检索对照报告

- 完整运行：True
- 提交：a1f7ef955cc786e37634141e541f779da9947411（工作区有变更：True）
- 数据集 SHA256：5ed09646874de2caf31322e020592abbef6cae1850480df3a61aefbd441b92c5
- 数据库守卫：snapshot_verified
- 推荐判定：{'recommended': 'bm25_hash', 'status': 'insufficient_evidence'}

Recall@5 / MRR@5 仅统计有答案题；无答案题的候选返回数不是误答率。问答误答由 ai_eval_runner 单独评测。

| 集合 | 策略 | 有答案题数 | Recall@5 | MRR@5 | p95 ms |
|---|---|---:|---:|---:|---:|
| dev | bm25 | 84 | 0.8929 | 0.7780 | 70.2300 |
| dev | bm25_hash | 84 | 0.8571 | 0.7214 | 74.8700 |

完整配置、全量语料逐块哈希、tokens、失败证据及逐题结果见 report.json / evidence.jsonl。
不含模拟云端结果；未配置价格时不估算成本。
