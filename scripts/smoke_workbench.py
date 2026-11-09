"""Exercise the model-disabled Compose product through its public reverse proxy."""
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from evaluation_provenance import source_provenance

ROOT = Path(__file__).resolve().parents[1]
BASE = os.getenv("EVAL_BASE_URL", "http://127.0.0.1:18080/api").rstrip("/")
parts = urllib.parse.urlsplit(BASE)
ORIGIN = f"{parts.scheme}://{parts.netloc}"


def call(path, token=None, body=None):
    headers = {"Content-Type": "application/json", "Origin": ORIGIN}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, headers=headers,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8"))
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        try:
            return json.load(error)
        except ValueError:
            return {"code": f"HTTP_{error.code}"}


def main():
    out = ROOT / os.getenv("SMOKE_OUTPUT_DIR", "artifacts/productization/compose-smoke")
    out.mkdir(parents=True, exist_ok=True)
    checks, evidence, tokens = [], {}, {}
    manifest = None

    def check(name, condition):
        checks.append({"name": name, "passed": bool(condition)})
        if not condition:
            raise AssertionError(name)

    def ask(user, question):
        result = call("/assistant/chat", tokens[user], {"question": question})
        evidence[f"{user}:{question}"] = result
        return result

    error = None
    try:
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            try:
                result = call("/auth/login", body={"username": "admin", "password": "123456"})
                if result.get("code") == "Success":
                    tokens["admin"] = result["data"]["token"]
                    manifest = call("/m10/retrieval-tests/manifest", tokens["admin"]).get("data")
                    if manifest and manifest["chunks"] and all(c["baselineReady"] for c in manifest["chunks"]):
                        break
            except (OSError, ValueError):
                pass
            time.sleep(1)
        check("Browser-origin login and baseline indexes are ready", bool(manifest) and bool(tokens.get("admin"))
              and bool(manifest.get("chunks")) and all(c["baselineReady"] for c in manifest["chunks"]))
        check("Default startup does not enable cloud chat", manifest["chatModel"]["enabled"] is False)
        check("Default retrieval remains BM25 plus hash", manifest["defaultStrategy"] == "bm25_hash")
        for user in ("caigou01", "caigou02", "cangku01"):
            result = call("/auth/login", body={"username": user, "password": "123456"})
            check(f"Demo login: {user}", result.get("code") == "Success")
            tokens[user] = result["data"]["token"]

        a, b = (ask(user, "哪些订单还没交货") for user in ("caigou01", "caigou02"))
        check("Both departments can query their own orders", a["code"] == b["code"] == "Success")
        numbers = lambda result: set(re.findall(r"CG-DD-\d{8}-\d{4}", json.dumps(result, ensure_ascii=False)))
        check("Department order sets are nonempty and disjoint", bool(numbers(a)) and bool(numbers(b))
              and not numbers(a).intersection(numbers(b)))
        foreign = call(f"/assistant/sessions/{a['data']['sessionId']}/messages", tokens["caigou02"])
        check("Another account cannot read the session", foreign.get("code") == "NoPermission")
        check("Department-only scope cannot read warehouse stock",
              ask("caigou01", "内六角螺栓还有多少库存")["code"] == "NoPermission")
        check("Warehouse account can read allowed stock",
              ask("cangku01", "内六角螺栓还有多少库存")["code"] == "Success")
        policy = ask("admin", "采购申请提交后多久必须审批完")
        data = policy.get("data", {})
        check("Disabled model returns labeled source excerpts", policy["code"] == "NoData"
              and data.get("answerMode") == "references_only" and data.get("degradedReason") == "model_disabled"
              and bool(data.get("refs")) and all(r.get("valid") is True and r.get("snippet") for r in data["refs"]))
        check("References-only response claims no generated evidence", not data.get("evidenceChunkIds"))
        denied = ask("admin", "删除物料 M-1001")
        check("AI refuses business writes", denied["code"] == "Unsupported" and not denied.get("data", {}).get("table"))
        invalid = call("/m10/retrieval-tests", tokens["admin"], {"question": "审批", "strategy": "invalid"})
        check("Retrieval strategy is validated", invalid.get("code") == "InvalidParameter")
    except Exception as exc:
        error = f"{type(exc).__name__}: {exc}"
    finally:
        for token in tokens.values():
            try:
                call("/auth/logout", token, {})
            except OSError:
                pass
    result = {"meta": {**source_provenance(), "base": BASE, "origin_header": ORIGIN,
                       "cloud_calls": False, "manifest": manifest},
              "complete": error is None, "checks": checks, "error": error, "evidence": evidence}
    (out / "results.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Compose smoke: {sum(c['passed'] for c in checks)}/{len(checks)}; complete={error is None}")
    if error:
        print(error)
    print(out)
    return 0 if error is None else 1


if __name__ == "__main__":
    raise SystemExit(main())
