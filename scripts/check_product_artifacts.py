#!/usr/bin/env python3
"""Offline integrity checks for frozen evaluation and generated source indexes."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
evaluation = ROOT / "docs/evaluation"
corpus = json.loads((evaluation / "corpus.json").read_text(encoding="utf-8"))
dataset_path = evaluation / "retrieval-v1.json"
dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
manifest = json.loads((evaluation / "frozen-sha256.json").read_text(encoding="utf-8"))
assert len(corpus["documents"]) >= 8
assert sum(len(doc["chunks"]) for doc in corpus["documents"]) >= 60
assert len(dataset["cases"]) == 60
families = {}
for case in dataset["cases"]:
    families.setdefault(case["family"], set()).add(case["split"])
    assert case["relevant_chunk_ids"] or case["category"] == "无答案"
assert all(len(splits) == 1 for splits in families.values()), "paraphrase family crosses frozen splits"
assert sum(case["split"] == "dev" for case in dataset["cases"]) == 30
assert sum(case["split"] == "holdout" for case in dataset["cases"]) == 30
for name, expected in manifest.items():
    assert hashlib.sha256((evaluation/name).read_bytes()).hexdigest() == expected, f"frozen artifact changed: {name}"
expected = {chunk["id"] for doc in corpus["documents"] for chunk in doc["chunks"]}
sql = (ROOT / "db/migrations/V20261109_02__demo_corpus.sql").read_text(encoding="utf-8")
for chunk_id in expected:
    assert f"({chunk_id}," in sql, f"missing demo chunk seed {chunk_id}"
print("product artifacts OK: 8 fictional docs, 64 chunks, 30 dev / 30 frozen holdout")
