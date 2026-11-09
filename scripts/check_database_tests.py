import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports = list(Path("server/target/failsafe-reports").glob("TEST-*.xml"))
if not reports: raise SystemExit("No database integration test reports found")
for path in reports:
    root = ET.parse(path).getroot()
    if any(int(root.get(key, 0)) for key in ("errors", "failures", "skipped")):
        raise SystemExit(f"Database integration tests did not all run successfully: {path}")
print("Database integration tests ran without skips or failures")
