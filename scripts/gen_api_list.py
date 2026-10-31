#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从后端 Controller 源码生成接口清单（论文附录用）。

为什么用脚本生成而不是手工维护：
    手工表格一定会和代码脱节。这里直接扫 @RequestMapping / @GetMapping / @PostMapping …
    与 @RequirePermission，路径、方法、权限点三者都来自代码本身；
    说明文字取方法上的 JavaDoc 第一句（本工程的 JavaDoc 都标了功能点编号）。

用法：
    python scripts/gen_api_list.py            # 输出到 API_LIST.md
    python scripts/gen_api_list.py --check    # 只比对，发现文档与代码不一致时退出码 1
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "server", "src", "main", "java", "com", "zufe", "procurement")
OUT = os.path.join(ROOT, "API_LIST.md")

MODULE_TITLES = {
    "auth": "认证与会话",
    "common": "公共能力",
    "m1": "M1 基础数据管理",
    "m2": "M2 采购申请与审批",
    "m3": "M3 采购订单管理",
    "m4": "M4 到货验收管理",
    "m5": "M5 采购入库与库存",
    "m6": "M6 价格参考与审批辅助",
    "m7": "M7 智能助手统一入口",
    "m8": "M8 制度与操作教程问答",
    "m9": "M9 受控自然语言数据查询",
    "m10": "M10 知识库管理",
    "m11": "M11 平台与权限管理",
    "m12": "M12 管理后台与数据可视化",
}
MODULE_ORDER = ["auth", "common", "m1", "m2", "m3", "m4", "m5", "m6",
                "m7", "m8", "m9", "m10", "m11", "m12"]

MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete)Mapping(?:\((.*?)\))?', re.S)
CLASS_MAPPING_RE = re.compile(r'@RequestMapping\((.*?)\)', re.S)
PERM_RE = re.compile(r'@RequirePermission\("([^"]+)"\)')
STRING_RE = re.compile(r'"([^"]*)"')
# 一个方法 = 可选 JavaDoc + 若干注解 + public 声明
# 注解参数允许一层括号嵌套（@RequirePermission("x")、@RequestParam(defaultValue = "1")）
METHOD_RE = re.compile(
    r'(?P<doc>/\*\*.*?\*/\s*)?'
    r'(?P<ann>(?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)'
    r'public\s+[\w<>\[\],.\s]+?\s+(?P<name>\w+)\s*\(', re.S)


def first_sentence(text):
    """取 JavaDoc 第一句中文说明：去掉行首装饰与注解字样，按句号切。"""
    lines = []
    for raw in text.splitlines():
        line = raw.strip().lstrip("*").strip()
        if not line or line.startswith("@"):
            continue
        lines.append(line)
    if not lines:
        return ""
    joined = " ".join(lines)
    parts = re.split(r"[。；]", joined, maxsplit=1)
    return parts[0].strip()


def iter_controllers():
    for dirpath, _dirnames, filenames in os.walk(SRC):
        for name in sorted(filenames):
            if not name.endswith("Controller.java"):
                continue
            path = os.path.join(dirpath, name)
            yield path


def module_of(path):
    rel = os.path.relpath(path, SRC)
    head = rel.split(os.sep)[0]
    return head if head in MODULE_TITLES else "common"


def parse(path):
    text = open(path, encoding="utf-8").read()
    class_map = CLASS_MAPPING_RE.search(text)
    base = ""
    if class_map:
        found = STRING_RE.search(class_map.group(1))
        base = found.group(1) if found else ""
    base = base.replace("/api", "", 1) if base.startswith("/api") else base

    rows = []
    for match in METHOD_RE.finditer(text):
        ann = match.group("ann") or ""
        mapping = MAPPING_RE.search(ann)
        if not mapping:
            continue
        verb = mapping.group(1).upper()
        sub = STRING_RE.search(mapping.group(2) or "")
        sub_path = sub.group(1) if sub else ""
        perm = PERM_RE.search(ann)
        doc_body = (match.group("doc") or "").strip()
        if doc_body.startswith("/**"):
            doc_body = doc_body[3:]
        if doc_body.endswith("*/"):
            doc_body = doc_body[:-2]
        rows.append({
            "verb": verb,
            "path": "/api" + base + sub_path,
            "perm": perm.group(1) if perm else "",
            "desc": first_sentence(doc_body) if doc_body else "",
        })
    return rows


def build():
    groups = {}
    for path in iter_controllers():
        rows = parse(path)
        if not rows:
            continue
        groups.setdefault(module_of(path), []).extend(rows)

    lines = []
    lines.append("# 接口清单")
    lines.append("")
    lines.append("> 本文件由 `scripts/gen_api_list.py` 从后端 Controller 源码生成，请勿手工修改。")
    lines.append("> 重新生成：`python scripts/gen_api_list.py`；校验文档与代码是否一致：`--check`。")
    lines.append("")
    total = sum(len(v) for v in groups.values())
    lines.append("全部接口共 **%d** 个，按模块分组。`权限点` 列为空表示只要求登录态"
                 "（如登录、验证码、首页概览），其余接口都过 `@RequirePermission` 动作级校验。"
                 % total)
    lines.append("")
    lines.append("| 模块 | 接口数 |")
    lines.append("|---|---|")
    for key in MODULE_ORDER:
        if key in groups:
            lines.append("| %s | %d |" % (MODULE_TITLES[key], len(groups[key])))
    lines.append("")
    if "m9" not in groups:
        lines.append("> M9（受控自然语言数据查询）没有独立 REST 端点：它以 "
                     "`AiDataQueryPort` 的形式挂在 M7 的对话链路上，"
                     "外部只经 `POST /api/assistant/chat` 进入，"
                     "因此接口清单里看不到单独的 M9 路径。")
        lines.append("")
    for key in MODULE_ORDER:
        if key not in groups:
            continue
        lines.append("## %s（%d 个）" % (MODULE_TITLES[key], len(groups[key])))
        lines.append("")
        lines.append("| 方法 | 路径 | 权限点 | 说明 |")
        lines.append("|---|---|---|---|")
        for row in sorted(groups[key], key=lambda r: (r["path"], r["verb"])):
            lines.append("| %s | `%s` | %s | %s |" % (
                row["verb"], row["path"], row["perm"] or "—", row["desc"] or "—"))
        lines.append("")
    return "\n".join(lines).rstrip("\n") + "\n", total


def main():
    content, total = build()
    if "--check" in sys.argv:
        if not os.path.exists(OUT):
            print("缺少 API_LIST.md，先运行 python scripts/gen_api_list.py")
            return 1
        current = open(OUT, encoding="utf-8").read()
        if current != content:
            print("API_LIST.md 与 Controller 源码不一致，请重新生成（共 %d 个接口）" % total)
            return 1
        print("API_LIST.md 与代码一致（%d 个接口）" % total)
        return 0
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(content)
    print("已生成 API_LIST.md：%d 个接口" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
