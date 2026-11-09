"""Run faults only in isolated JUnit service instances, never inject into a live deployment."""
import json
import os
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from evaluation_provenance import source_provenance

ROOT = Path(__file__).resolve().parents[1]
TESTS = ("ConservativeQaTest", "EmbeddingGatewayTest", "SemanticIndexTest", "SessionAuthorizationTest")

def run(output):
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    wrapper = ROOT / "server" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    started = time.time()
    with (output / "execution.log").open("w", encoding="utf-8") as log:
        proc = subprocess.run([str(wrapper), "-q", "-Dtest=" + ",".join(TESTS), "test"],
                              cwd=wrapper.parent, stdout=log, stderr=subprocess.STDOUT, timeout=300)
    rows = []
    observed = set()
    for path in (ROOT / "server/target/surefire-reports").glob("TEST-*.xml"):
        if path.stat().st_mtime < started - 2:
            continue
        suite = ET.parse(path).getroot()
        name = suite.get("name", "").split(".")[-1]
        if name not in TESTS:
            continue
        observed.add(name)
        shutil.copy2(path, output / path.name)
        for case in suite.findall("testcase"):
            failed = case.find("failure") is not None or case.find("error") is not None
            skipped = case.find("skipped") is not None
            rows.append({"suite": name, "case": case.get("name"), "passed": not failed and not skipped,
                         "skipped": skipped, "seconds": float(case.get("time", 0))})
    complete = proc.returncode == 0 and observed == set(TESTS) and bool(rows) and all(r["passed"] for r in rows)
    result = {"meta": {"mode": "fault", "simulated_faults": True, "real_model_calls": False,
                       **source_provenance(),
                       "isolation": "JUnit service objects and loopback HTTP stub; no production fault endpoint",
                       "commit_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                       "working_tree_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
                       "complete": complete, "database_guard": "not_applicable_no_database_calls"},
              "passed": sum(r["passed"] for r in rows), "count": len(rows), "cases": rows}
    (output / "results.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "report.md").write_text("# 模拟故障回归\n\n"
        f"完成：{complete}；通过 {result['passed']}/{len(rows)}。\n\n"
        "本报告仅证明隔离服务对象与本机 HTTP 替身的故障行为，不是模型准确率或真实服务可用率。"
        "逐条证据见 results.json 与 JUnit XML。\n", encoding="utf-8")
    print(output)
    return 0 if complete else 1
