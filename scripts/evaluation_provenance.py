"""Bind local runs to both HEAD and the actual tracked/untracked source tree."""
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def source_provenance():
    names = subprocess.check_output(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=ROOT).decode("utf-8").split("\0")
    digest = hashlib.sha256()
    count = 0
    for name in sorted(set(names)):
        path = ROOT / name
        if not name or not path.is_file():
            continue
        digest.update(name.encode("utf-8") + b"\0" + hashlib.sha256(path.read_bytes()).digest())
        count += 1
    return {"source_tree_sha256": digest.hexdigest(), "source_file_count": count,
            "scope": "git tracked and non-ignored untracked files; excludes artifacts, dependencies and build outputs"}
