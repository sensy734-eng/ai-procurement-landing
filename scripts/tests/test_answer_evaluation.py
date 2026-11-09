import sys
import unittest
from unittest.mock import Mock
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ai_eval_runner import Judge, preload_db, validate_dataset

class AnswerEvaluationTests(unittest.TestCase):
    def test_tutorial_without_ordered_structure_fails(self):
        case = {"judge": "facts", "expect": {"ordered": True, "steps": [
            {"desc": "打开页面", "groups": [["打开"]]}, {"desc": "填写字段", "groups": [["填写"]]}
        ]}}
        run = {"payload": {"answer": "先打开页面，然后填写字段。"}, "db_before": {}, "db_after": {}}
        passed, score, reasons, _ = Judge(False).judge(case, run)
        self.assertFalse(passed)
        self.assertLess(score, 1)
        self.assertTrue(any("有序步骤" in r for r in reasons))

    def test_correct_fallback_is_not_a_generated_answer(self):
        case = {"judge": "facts", "expect": {"answer_mode": "generated"}}
        run = {"payload": {"answerMode": "references_only"}}
        self.assertFalse(Judge(False).judge(case, run)[0])

    def test_missing_database_guard_never_passes_silently(self):
        reasons, _ = Judge(False).check_db_guards({"db_guard": [{"sql": "select 1", "op": "unchanged"}]},
                                                 {"db_before": {}, "db_after": {}})
        self.assertTrue(reasons)

    def test_absent_guard_reads_scope_evidence_and_detects_leak(self):
        guard = {"sql": "select order_no from foreign_department", "op": "absent"}
        judge = Judge(True)
        judge.db_rows = Mock(return_value=[["FOREIGN-ORDER"]])
        before = preload_db(judge, {"expect": {"db_guard": [guard]}})
        run = {"db_before": before, "db_after": before,
               "payload": {"answer": "Only allowed records"}}
        self.assertEqual([], judge.check_db_guards({"db_guard": [guard]}, run)[0])
        run["payload"]["answer"] = "FOREIGN-ORDER"
        self.assertTrue(judge.check_db_guards({"db_guard": [guard]}, run)[0])
        judge.db_rows.assert_called_once_with(guard["sql"])

    def test_invalid_count_guard_fails_closed(self):
        guard = {"sql": "select count(*) from purchase_order", "op": "ge"}
        reasons, _ = Judge(True).check_db_guards({"db_guard": [guard]},
            {"db_before": {guard["sql"]: []}, "db_after": {guard["sql"]: [["invalid"]]}})
        self.assertTrue(reasons)

    def test_custom_dataset_does_not_require_legacy_theory_ids(self):
        data = {"cases": [{"case_id": "CUSTOM-01", "layer": "P0", "judge": "exact",
                           "context": None, "expect": {"result_code": ["Success"]}}]}
        self.assertEqual([], validate_dataset(data, check_legacy_theory=False)[0])

    def test_reference_only_requires_valid_original_passages(self):
        case = {"judge": "conservative", "expect": {"answer_mode": "references_only"}}
        payload = {"answerMode": "references_only", "degradedReason": "model_disabled",
                   "refs": [{"valid": False, "snippet": "Unverified text"}]}
        run = {"code": "NoData", "payload": payload}
        self.assertFalse(Judge(False).judge(case, run)[0])
        payload["refs"][0]["valid"] = True
        self.assertTrue(Judge(False).judge(case, run)[0])

if __name__ == "__main__":
    unittest.main()
