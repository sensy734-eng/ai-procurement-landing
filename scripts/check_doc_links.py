"""Check relative links in tracked/new Markdown, without network dependencies."""
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
paths = subprocess.check_output(["git", "ls-files", "-co", "--exclude-standard", "--", "*.md"], cwd=ROOT).decode("utf-8").splitlines()
errors = []
for name in sorted(set(paths)):
    path = ROOT / name
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8-sig")
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    for target in re.findall(r"\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)", text):
        target = unquote(target.strip("<>").split("#", 1)[0].split("?", 1)[0])
        if not target or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target):
            continue
        resolved = (ROOT / target.lstrip("/")) if target.startswith("/") else path.parent / target
        if not resolved.exists():
            errors.append(f"{name}: {target}")
if errors:
    raise SystemExit("Broken local Markdown links:\n" + "\n".join(errors))
print(f"Markdown links checked in {len(set(paths))} files")
