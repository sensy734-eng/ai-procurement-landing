#!/usr/bin/env python3
"""Frozen strict retrieval evaluation; partial evidence survives failures."""
import argparse
import hashlib
import json
import math
import os
import statistics
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from evaluation_provenance import source_provenance
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = os.environ.get("EVAL_BASE_URL", "http://127.0.0.1:18080/api").rstrip("/")
STRATEGIES = ("bm25", "bm25_hash", "semantic", "bm25_semantic")

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def call(path, method="GET", token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(API + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=35) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"HTTP {error.code} on {path}") from error
    if payload.get("code") != "Success":
        raise RuntimeError(f"API {payload.get('code')} on {path}")
    return payload["data"]

def rank_metrics(hits, expected):
    ids = [item.get("chunkId") for item in hits[:5]]
    relevant = set(expected)
    return {
        "recall_at_5": len(set(ids) & relevant) / len(relevant) if relevant else None,
        "reciprocal_rank": next((1 / (i + 1) for i, value in enumerate(ids) if value in relevant), 0.0) if relevant else None,
        "no_answer_candidates_returned": bool(ids) if not relevant else None,
    }

def summarize(rows):
    valid = [r for r in rows if r.get("recall_at_5") is not None]
    latency = sorted(r["latency_ms"] for r in rows)
    known = [r["embedding_tokens"] for r in rows if r.get("embedding_tokens") is not None]
    return {
        "count": len(rows), "answerable_count": len(valid),
        "recall_at_5": statistics.mean(r["recall_at_5"] for r in valid) if valid else None,
        "mrr_at_5": statistics.mean(r["reciprocal_rank"] for r in valid) if valid else None,
        "p95_latency_ms": latency[math.ceil(.95 * len(latency)) - 1] if latency else None,
        "no_answer_count": len(rows) - len(valid),
        "no_answer_with_candidates": sum(r.get("no_answer_candidates_returned") is True for r in rows),
        "embedding_tokens_known": sum(known), "embedding_usage_missing": len(rows) - len(known),
    }

def recommendation(rows, complete):
    def recall(strategy, synonym=False):
        return summarize([r for r in rows if r["split"] == "holdout" and r["strategy"] == strategy
                          and (not synonym or r["synonym_variant"])])["recall_at_5"]
    base, new = recall("bm25_hash"), recall("bm25_semantic")
    base_syn, new_syn = recall("bm25_hash", True), recall("bm25_semantic", True)
    if not complete or any(v is None for v in (base, new, base_syn, new_syn)):
        return {"recommended": "bm25_hash", "status": "insufficient_evidence"}
    passed = new >= base and new_syn > base_syn
    return {"recommended": "bm25_semantic" if passed else "bm25_hash",
            "status": "threshold_passed" if passed else "experimental_only",
            "holdout_recall_delta": new - base, "synonym_recall_delta": new_syn - base_syn}

def verify_freeze(dataset_path):
    frozen = json.loads((ROOT / "docs/evaluation/frozen-sha256.json").read_text(encoding="utf-8"))
    if frozen.get(dataset_path.name) != sha(dataset_path):
        raise ValueError("Dataset differs from frozen manifest; create a separately reviewed version")
    corpus_path = ROOT / "docs/evaluation/corpus.json"
    if frozen["corpus.json"] != sha(corpus_path):
        raise ValueError("Frozen corpus hash mismatch")
    return json.loads(corpus_path.read_text(encoding="utf-8"))

def verify_serving_corpus(manifest, corpus):
    online = {c["chunkId"]: c for c in manifest["chunks"]}
    if not online or any(not c.get("baselineReady") for c in online.values()):
        raise ValueError("Baseline indexes are incomplete/stale; explicitly rebuild hash indexes first")
    for doc in corpus["documents"]:
        for chunk in doc["chunks"]:
            normalized = chunk["text"].replace("\r\n", "\n").replace("\r", "\n").strip()
            expected = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
            actual = online.get(chunk["id"], {})
            if actual.get("contentHash") != expected or actual.get("docId") != doc["id"]:
                raise ValueError(f"Serving corpus differs at chunk {chunk['id']}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="docs/evaluation/retrieval-v1.json")
    parser.add_argument("--mode", choices=("all", "dev", "holdout"), default="dev")
    parser.add_argument("--output-dir", default="artifacts/rag-eval")
    parser.add_argument("--repeats", type=int, default=1)
    parser.add_argument("--strategy", choices=STRATEGIES, action="append")
    args = parser.parse_args()
    if not 1 <= args.repeats <= 20:
        parser.error("--repeats must be 1..20")
    dataset_path = (ROOT / args.dataset).resolve()
    corpus = verify_freeze(dataset_path)
    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    cases = [c for c in dataset["cases"] if args.mode == "all" or c["split"] == args.mode]
    if not cases:
        parser.error("dataset selection is empty")
    strategies = list(dict.fromkeys(args.strategy or STRATEGIES))
    price = os.getenv("EMBEDDING_PRICE_PER_MILLION")
    if price is not None:
        try:
            price = float(price)
            if not math.isfinite(price) or price < 0:
                raise ValueError()
        except ValueError:
            parser.error("Invalid embedding price")
    out = ROOT / args.output_dir / (time.strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8])
    out.mkdir(parents=True, exist_ok=False)
    started = time.perf_counter()
    rows, errors, before, after = [], [], None, None
    active = {}
    try:
        login = call("/auth/login", "POST", body={"username": os.getenv("EVAL_USERNAME", "admin"),
                                                 "password": os.getenv("EVAL_PASSWORD", "123456")})
        token = login["token"]
        before = call("/m10/retrieval-tests/manifest", token=token)
        verify_serving_corpus(before, corpus)
        for repeat in range(1, args.repeats + 1):
            offset = (repeat - 1) % len(strategies)
            for strategy in strategies[offset:] + strategies[:offset]:
                for case in cases:
                    active = {"case_id": case["id"], "strategy": strategy, "repeat": repeat}
                    tick = time.perf_counter()
                    data = call("/m10/retrieval-tests", "POST", token, {
                        "question": case["question"], "topK": 5, "strategy": strategy, "strict": True,
                        "testBatch": f"{out.name}-{strategy}-{repeat}"})
                    if data.get("actualStrategy") != strategy or data.get("degradedReason"):
                        raise RuntimeError("Strict experiment silently degraded")
                    row = {**active, "family": case["family"], "split": case["split"],
                           "query": case["question"], "synonym_variant": case["id"].endswith("-2"),
                           "expected_chunk_ids": case["relevant_chunk_ids"], "ranked": data.get("ranked") or [],
                           "latency_ms": round((time.perf_counter() - tick) * 1000, 2),
                           "embedding_tokens": data.get("embeddingTokens"), "config_id": data.get("configId")}
                    row.update(rank_metrics(row["ranked"], case["relevant_chunk_ids"]))
                    rows.append(row)
                    with (out / "evidence.jsonl").open("a", encoding="utf-8", newline="\n") as f:
                        f.write(json.dumps(row, ensure_ascii=False) + "\n")
                after = call("/m10/retrieval-tests/manifest", token=token)
                if before != after:
                    raise RuntimeError("Serving corpus/config changed during experiment")
    except Exception as error:
        errors.append({**active, "type": type(error).__name__, "message": str(error)})
    complete = not errors and len(rows) == len(cases) * len(strategies) * args.repeats
    results = {split: {s: summarize([r for r in rows if r["strategy"] == s and r["split"] == split])
                       for s in strategies} for split in sorted({c["split"] for c in cases})}
    for split, values in results.items():
        for strategy, metrics in values.items():
            repeated = [summarize([r for r in rows if r["split"] == split and r["strategy"] == strategy
                                  and r["repeat"] == repeat])["recall_at_5"] for repeat in range(1, args.repeats + 1)]
            present = [v for v in repeated if v is not None]
            metrics["repeat_stddev_recall_at_5"] = statistics.stdev(present) if len(present) > 1 else None
    meta = {"commit_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            **source_provenance(),
            "working_tree_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
            "dataset_sha256": sha(dataset_path), "demo_corpus_sha256": sha(ROOT / "docs/evaluation/corpus.json"),
            "serving_manifest_before": before, "serving_manifest_after": after,
            "database_guard": "snapshot_verified" if complete and before == after else "not_verified",
            "mode": args.mode, "repeats": args.repeats, "complete": complete, "simulated_faults": False,
            "elapsed_s": round(time.perf_counter() - started, 2),
            "synonym_subset": "Frozen family variant -2, answerable questions only; designated before tuning",
            "usage_scope": "Online query embeddings only; excludes index building and failed calls"}
    if price is not None:
        meta["cost_estimate"] = {"known_usage_only": sum(r.get("embedding_tokens") or 0 for r in rows) * price / 1_000_000,
                                 "currency": os.getenv("EMBEDDING_PRICE_CURRENCY", "unspecified"), "price_per_million": price}
    report = {"meta": meta, "results": results, "recommendation": recommendation(rows, complete),
              "errors": errors, "rows": rows}
    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# 检索对照报告", "", f"- 完整运行：{complete}", f"- 提交：{meta['commit_sha']}（工作区有变更：{meta['working_tree_dirty']}）",
             f"- 数据集 SHA256：{meta['dataset_sha256']}", f"- 数据库守卫：{meta['database_guard']}",
             f"- 推荐判定：{report['recommendation']}", "",
             "Recall@5 / MRR@5 仅统计有答案题；无答案题的候选返回数不是误答率。问答误答由 ai_eval_runner 单独评测。",
             "", "| 集合 | 策略 | 有答案题数 | Recall@5 | MRR@5 | p95 ms |", "|---|---|---:|---:|---:|---:|"]
    def display(value):
        return "N/A" if value is None else f"{value:.4f}"
    for split, values in results.items():
        for s, m in values.items():
            lines.append(f"| {split} | {s} | {m['answerable_count']} | {display(m['recall_at_5'])} | {display(m['mrr_at_5'])} | {display(m['p95_latency_ms'])} |")
    lines += ["", "完整配置、全量语料逐块哈希、tokens、失败证据及逐题结果见 report.json / evidence.jsonl。",
              "不含模拟云端结果；未配置价格时不估算成本。", "", *[f"运行失败：{e}" for e in errors]]
    (out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out)
    return 0 if complete else 1

if __name__ == "__main__":
    raise SystemExit(main())
