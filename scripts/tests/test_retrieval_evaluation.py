import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rag_eval_runner import rank_metrics, summarize, recommendation, verify_serving_corpus, verify_freeze, ROOT

class RetrievalEvaluationTests(unittest.TestCase):
    def test_no_answer_is_not_in_recall_denominator(self):
        row = {**rank_metrics([{"chunkId": 1}], []), "latency_ms": 2, "embedding_tokens": None}
        result = summarize([row])
        self.assertIsNone(result["recall_at_5"])
        self.assertEqual(result["no_answer_with_candidates"], 1)
        self.assertEqual(result["embedding_usage_missing"], 1)

    def test_recall_uses_all_relevant_labels_and_rank_is_one_based(self):
        result = rank_metrics([{"chunkId": 8}, {"chunkId": 2}], [2, 3])
        self.assertEqual(result["recall_at_5"], .5)
        self.assertEqual(result["reciprocal_rank"], .5)

    def test_empty_split_does_not_crash_or_recommend_semantics(self):
        self.assertIsNone(summarize([])["p95_latency_ms"])
        self.assertEqual(recommendation([], True)["status"], "insufficient_evidence")

    def test_semantic_recommendation_requires_both_gates(self):
        def row(strategy, recall):
            return {"split": "holdout", "strategy": strategy, "synonym_variant": True,
                    "recall_at_5": recall, "reciprocal_rank": recall, "latency_ms": 1, "embedding_tokens": 2}
        tied = [row("bm25_hash", .8), row("bm25_semantic", .8)]
        self.assertEqual(recommendation(tied, True)["status"], "experimental_only")
        improved = [row("bm25_hash", .8), row("bm25_semantic", .9)]
        self.assertEqual(recommendation(improved, True)["recommended"], "bm25_semantic")
        self.assertEqual(recommendation(improved, False)["status"], "insufficient_evidence")

    def test_corpus_mismatch_fails_closed(self):
        corpus = {"documents": [{"id": 2, "chunks": [{"id": 1, "text": "changed"}]}]}
        with self.assertRaises(ValueError):
            verify_serving_corpus({"chunks": []}, corpus)

    def test_frozen_inputs_still_match(self):
        self.assertEqual(len(verify_freeze(ROOT / "docs/evaluation/retrieval-v1.json")["documents"]), 8)

if __name__ == "__main__":
    unittest.main()
