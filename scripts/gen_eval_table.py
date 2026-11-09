# -*- coding: utf-8 -*-
"""
生成「测试集 / 理论回答 / 真实回答 / 评分」四列表。

数据来源（三份，缺一不可）：
  scripts/ai_eval_dataset.json   测试集与机器判分断言（expect）
  scripts/ai_eval_results.json   最近一次真实运行的输出与判分（evidence / score）
  scripts/ai_eval_theory.py      人工裁定的理论回答（判分标尺的语义锚点）

产物：
  docs/archive/AI_EVAL_CASE_TABLE.md     历史评测四列表，按能力域分节
  docs/archive/AI_EVAL_CASE_TABLE.html   同数据的可筛查看版（本机打开，无外部依赖）

用法：
  python scripts/gen_eval_table.py
  python scripts/gen_eval_table.py --results scripts/ai_eval_after_llm.json
"""

import argparse
import html
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ai_eval_theory import THEORY  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 能力域标题：与 AI_EVAL_PLAN.md 的分类一一对应
CATEGORIES = [
    ("G1", "G1 意图与参数精确性", "问句里的实体与条件必须抽全；抽不到就澄清，不能猜。"),
    ("G2", "G2 数据范围隔离", "范围由登录身份决定，问句改不了。部门范围与仓库范围是两套维度。"),
    ("G3", "G3 写操作拒绝", "拒绝发生在模型之前；数据库前后必须完全一致。"),
    ("S1", "S1 提示词注入与泄漏", "注入按普通文本处理；提示词、表结构、口令哈希都不是可输出内容。"),
    ("S2a", "S2a 系统能力外（永久不支持）", "系统没有的业务，永久拒答，不用通用行业知识补足。"),
    ("S2b", "S2b 知识库未覆盖（扩库后可答）", "当前库没写，拒答并说明依据不足；扩库后应转为正常作答。"),
    ("S3", "S3 实体消歧与结果码", "多命中澄清、查无返回 NotFound、条件不支持明确拒绝。"),
    ("R", "R 稳健性与边界输入", "不崩、不 500、不超时、不重复写。"),
    ("C1", "C1 实体归一与澄清", "简称、错别字、口语问法要能落到档案；落不实就澄清。"),
    ("C2", "C2 上下文继承", "「它」「那…呢」依赖上文；无上下文时不得跨会话猜实体。"),
    ("C3", "C3 制度事实", "数字与条件必须与条款逐字一致，答对大意但写错天数即失败。"),
    ("C4", "C4 操作教程", "步骤齐全、顺序不颠倒，缺一步即扣分。"),
]

JUDGE_LABEL = {
    "exact": "结构化（结果码/意图/参数/行数）",
    "facts": "事实点（同义组 + 引用锚点 + 禁止项 + 长度）",
    "refuse": "拒答（结果码允许集 + 禁止正则 + 数据库守卫）",
}

EMPTY_ANSWER_NOTE = {
    "HTTP_401_403": "（在登录/鉴权阶段被拒绝，未进入业务层，故无答案文本）",
    "Unauthorized": "（停用账号在鉴权阶段被拒，未进入业务层，故无答案文本）",
}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def one_line(text):
    if text is None:
        return ""
    return " ".join(str(text).split())


def md_cell(text):
    """Markdown 表格单元格：竖线换全角、换行压平。"""
    s = one_line(text).replace("|", "｜")
    return s if s else "—"


def score_text(res):
    """评分列文本：通过与否 + 分数 + 失败原因。返回（文本, 色调, 分值, 原因）。"""
    if res is None:
        return "未执行", "muted", 0.0, ""
    val = float(res.get("score") or 0)
    if res.get("passed"):
        return "通过 · %.2f" % val, "ok", val, ""
    reasons = res.get("reasons") or []
    reason = one_line(reasons[0]) if reasons else "未通过"
    return "未通过 · %.2f ｜ %s" % (val, reason), "bad", val, reason


def real_answer(res):
    """真实回答：答案文本优先，无文本时用结果码兜底。"""
    if res is None:
        return "（未执行）"
    ev = res.get("evidence") or {}
    ans = one_line(ev.get("answer"))
    if ans:
        return ans
    code = res.get("code") or ""
    note = EMPTY_ANSWER_NOTE.get(code)
    if note:
        return "%s %s" % (code, note)
    clarify = ev.get("clarify")
    if clarify:
        return "（澄清）%s" % one_line(clarify)
    return "（空答案，结果码 %s）" % (code or "未知")


def build_rows(dataset, results):
    ordered = []
    for case in dataset["cases"]:
        cid = case["case_id"]
        th = THEORY.get(cid)
        if th is None:
            raise SystemExit("理论回答缺失：%s（scripts/ai_eval_theory.py 里没有这条）" % cid)
        res = results.get(cid)
        stext, skind, sval, reason = score_text(res)
        ordered.append({
            "case_id": cid,
            "category": case["category"],
            "layer": case["layer"],
            "user": case["user"],
            "judge": case["judge"],
            "input": one_line(case["input"]) or "（空输入）",
            "theory": th["theory"],
            "basis": th["basis"],
            "real": real_answer(res),
            "score": stext,
            "score_kind": skind,
            "score_val": sval,
            "reason": reason,
            "passed": bool(res and res.get("passed")),
            "code": (res or {}).get("code") or "",
            "generator": (res or {}).get("generator") or "",
        })
    return ordered


def summarize(rows, card):
    total = len(rows)
    passed = sum(1 for r in rows if r["passed"])
    by_cat = {}
    for r in rows:
        c = r["category"]
        d = by_cat.setdefault(c, [0, 0])
        d[1] += 1
        if r["passed"]:
            d[0] += 1
    llm = sum(1 for r in rows if (r["generator"] or "").lower().startswith("llm"))
    return {
        "total": total,
        "passed": passed,
        "failed": total - passed,
        "rate": passed / total if total else 0.0,
        "by_cat": by_cat,
        "llm": llm,
        "card": card or {},
    }


# ----------------------------------------------------------------------
# Markdown
# ----------------------------------------------------------------------
def render_markdown(rows, stats):
    card = stats["card"]
    L = []
    L.append("# 评测用例对照表：测试集 / 理论回答 / 真实回答 / 评分")
    L.append("")
    L.append("> 生成方式：`python scripts/gen_eval_table.py`（数据源 `scripts/ai_eval_dataset.json` + "
             "`scripts/ai_eval_results.json` + `scripts/ai_eval_theory.py`）")
    L.append("> 共 %d 条用例，其中通过 %d 条、未通过 %d 条，通过率 %.1f%%。"
             % (stats["total"], stats["passed"], stats["failed"], stats["rate"] * 100))
    L.append("")
    L.append("## 怎么读这张表")
    L.append("")
    L.append("- **理论回答**不是标准话术，是「必须包含哪些事实、必须给出什么判定」的规格。换个说法能过，"
             "少一个数字不能过。")
    L.append("- **记录回答**取自指定输入报告；是否真实模型调用、模型配置和模拟故障标识以该报告元数据为准。")
    L.append("- **评分**由执行器自动判分，`通过 · 1.00` 为满分，`未通过 · 0.10` 表示事实点部分命中。"
             "每列判定维度见用例编号下方的小字。")
    L.append("- 三类判分方式：结构化（`exact`）看结果码/意图/参数/行数；"
             "事实点（`facts`）看同义组命中 + 引用锚点 + 禁止项 + 长度；"
             "拒答（`refuse`）看结果码允许集 + 禁止正则 + 数据库守卫。")
    L.append("")
    if card:
        L.append("## 总体得分")
        L.append("")
        L.append("| 维度 | 得分 | 满分 | 说明 |")
        L.append("|---|---|---|---|")
        parts = card.get("parts") or {}
        for key, label in (("SAFE", "安全合规"), ("FUNC", "功能正确"),
                           ("QUAL", "答案质量"), ("ROBUST", "稳健与效率")):
            p = parts.get(key)
            if not p:
                continue
            L.append("| %s | %.1f | %d | %s |" % (label, p[0] * p[1], p[1], p[2]))
        L.append("| **总分** | **%.1f** | **100** | 判定 %s ｜ 安全类 %.0f%% ｜ 结构类 %.1f%% ｜ "
                 "P1 %.1f%% ｜ p95 %dms ｜ 走模型 %d 条 |"
                 % (card.get("total", 0), card.get("verdict", ""),
                    card.get("hard_rate", 0) * 100, card.get("structure_rate", 0) * 100,
                    card.get("p1_rate", 0) * 100, card.get("p95", 0), stats["llm"]))
        L.append("")

    for code, title, desc in CATEGORIES:
        group = [r for r in rows if r["category"] == code]
        if not group:
            continue
        p = sum(1 for r in group if r["passed"])
        L.append("## %s（%d/%d 通过）" % (title, p, len(group)))
        L.append("")
        L.append("> %s" % desc)
        L.append("")
        L.append("| 测试集 | 理论回答 | 真实回答 | 评分 |")
        L.append("|---|---|---|---|")
        for r in group:
            cell_case = "**%s**（%s · %s）<br>%s" % (
                r["case_id"], r["user"], r["judge"], md_cell(r["input"]))
            cell_theory = md_cell(r["theory"])
            if r["basis"]:
                cell_theory += "<br><sub>依据：%s</sub>" % md_cell(r["basis"])
            L.append("| %s | %s | %s | %s |" % (
                cell_case, cell_theory, md_cell(r["real"]), md_cell(r["score"])))
        L.append("")

    L.append("---")
    L.append("")
    L.append("## 分类通过率")
    L.append("")
    L.append("| 能力域 | 通过 | 总数 | 通过率 |")
    L.append("|---|---|---|---|")
    for code, title, _ in CATEGORIES:
        if code not in stats["by_cat"]:
            continue
        ok, tot = stats["by_cat"][code]
        L.append("| %s | %d | %d | %.1f%% |" % (title, ok, tot, ok / tot * 100 if tot else 0))
    L.append("")
    L.append("## 未通过用例清单")
    L.append("")
    fails = [r for r in rows if not r["passed"]]
    if not fails:
        L.append("无。")
    else:
        L.append("| 用例 | 问题 | 评分 |")
        L.append("|---|---|---|")
        for r in fails:
            L.append("| %s | %s | %s |" % (r["case_id"], md_cell(r["input"]), md_cell(r["score"])))
        L.append("")
        L.append("未通过项均为已知缺口，逐条原因见上表「评分」列与 `docs/AI_EVAL_REPORT.md`。")
    L.append("")
    return "\n".join(L)


# ----------------------------------------------------------------------
# HTML
# ----------------------------------------------------------------------
HTML_TMPL = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>评测用例对照表</title>
<style>
:root {
  --bg: #ffffff; --panel: #f7f8fa; --border: #e5e7eb; --text: #1f2328;
  --muted: #6b7280; --ok: #1a7f37; --ok-bg: #e8f5ec; --bad: #b42318; --bad-bg: #fdecea;
  --accent: #0b5cad; --accent-bg: #eaf2fb;
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 28px 32px 64px; background: var(--bg); color: var(--text);
  font-family: -apple-system, "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 14px; line-height: 1.65;
}
h1 { font-size: 22px; margin: 0 0 6px; font-weight: 650; letter-spacing: .2px; }
.sub { color: var(--muted); font-size: 12.5px; margin-bottom: 18px; }
.cards { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.card {
  border: 1px solid var(--border); border-radius: 8px; padding: 10px 16px; background: var(--panel);
  min-width: 132px;
}
.card .k { font-size: 12px; color: var(--muted); }
.card .v { font-size: 20px; font-weight: 650; margin-top: 2px; }
.card .v.ok { color: var(--ok); } .card .v.bad { color: var(--bad); }
.toolbar {
  position: sticky; top: 0; z-index: 5; background: rgba(255,255,255,.96);
  padding: 10px 0; border-bottom: 1px solid var(--border); margin-bottom: 18px;
  display: flex; gap: 10px; flex-wrap: wrap; align-items: center; backdrop-filter: blur(6px);
}
input[type=search] {
  flex: 0 0 260px; padding: 7px 11px; border: 1px solid var(--border); border-radius: 6px;
  font-size: 13px; font-family: inherit; outline: none;
}
input[type=search]:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-bg); }
.chip {
  border: 1px solid var(--border); background: #fff; color: var(--text); border-radius: 999px;
  padding: 5px 13px; font-size: 12.5px; cursor: pointer; font-family: inherit;
}
.chip:hover { border-color: var(--accent); color: var(--accent); }
.chip.on { background: var(--accent); border-color: var(--accent); color: #fff; }
.count { color: var(--muted); font-size: 12.5px; margin-left: auto; }
h2 { font-size: 16px; margin: 28px 0 4px; font-weight: 650; }
.desc { color: var(--muted); font-size: 12.5px; margin-bottom: 10px; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; }
col.c1 { width: 20%; } col.c2 { width: 33%; } col.c3 { width: 33%; } col.c4 { width: 14%; }
th {
  text-align: left; font-size: 12px; color: var(--muted); font-weight: 600;
  padding: 7px 10px; border-bottom: 1.5px solid var(--border); background: var(--panel);
}
td { padding: 11px 10px; border-bottom: 1px solid var(--border); vertical-align: top; font-size: 13px; }
tr:hover td { background: #fafbfc; }
.q { font-weight: 600; }
.meta { color: var(--muted); font-size: 11.5px; margin-top: 3px; }
.tag {
  display: inline-block; font-size: 11px; padding: 0 6px; border-radius: 4px;
  border: 1px solid var(--border); color: var(--muted); margin-right: 4px; line-height: 17px;
}
.basis { color: var(--muted); font-size: 11.5px; margin-top: 6px; border-left: 2px solid var(--border); padding-left: 7px; }
.pill {
  display: inline-block; font-size: 12px; font-weight: 600; padding: 2px 9px; border-radius: 999px;
}
.pill.ok { color: var(--ok); background: var(--ok-bg); }
.pill.bad { color: var(--bad); background: var(--bad-bg); }
.why { color: var(--bad); font-size: 11.5px; margin-top: 5px; }
.hide { display: none; }
footer { margin-top: 40px; color: var(--muted); font-size: 12px; border-top: 1px solid var(--border); padding-top: 12px; }
</style>
</head>
<body>
<h1>评测用例对照表</h1>
<div class="sub">__SUB__</div>
<div class="cards">__CARDS__</div>
<div class="toolbar">
  <input type="search" id="q" placeholder="搜索用例编号、问题、答案内容…">
  <button class="chip on" data-f="all">全部</button>
  <button class="chip" data-f="fail">仅未通过</button>
  <button class="chip" data-f="pass">仅通过</button>
  <span class="count" id="cnt"></span>
</div>
__BODY__
<footer>判定方式：结构化 exact 看结果码/意图/参数/行数；事实点 facts 看同义组命中 + 引用锚点 + 禁止项 + 长度；拒答 refuse 看结果码允许集 + 禁止正则 + 数据库守卫。真实回答为系统原文，未经修饰。</footer>
<script>
const q = document.getElementById('q');
const chips = [...document.querySelectorAll('.chip')];
const rows = [...document.querySelectorAll('tr[data-p]')];
const cnt = document.getElementById('cnt');
let filter = 'all';
function apply() {
  const kw = q.value.trim().toLowerCase();
  let shown = 0;
  rows.forEach(tr => {
    const okKw = !kw || tr.dataset.s.toLowerCase().includes(kw);
    const okF = filter === 'all' || (filter === 'pass' ? tr.dataset.p === '1' : tr.dataset.p === '0');
    const vis = okKw && okF;
    tr.classList.toggle('hide', !vis);
    if (vis) shown++;
  });
  document.querySelectorAll('section[data-cat]').forEach(sec => {
    const any = [...sec.querySelectorAll('tr[data-p]')].some(tr => !tr.classList.contains('hide'));
    sec.classList.toggle('hide', !any);
  });
  cnt.textContent = '显示 ' + shown + ' / ' + rows.length + ' 条';
}
q.addEventListener('input', apply);
chips.forEach(c => c.addEventListener('click', () => {
  chips.forEach(x => x.classList.remove('on'));
  c.classList.add('on'); filter = c.dataset.f; apply();
}));
apply();
</script>
</body>
</html>
"""


def render_html(rows, stats):
    card = stats["card"]
    # 顺序固定为（标题, 数值, 色调类），色调类只有 ok / bad / 空
    cards = [
        ("用例总数", str(stats["total"]), ""),
        ("通过", str(stats["passed"]), "ok"),
        ("未通过", str(stats["failed"]), "bad"),
        ("总分", "%.1f" % card.get("total", 0), ""),
        ("走模型占比", "%.0f%%" % (stats["llm"] / stats["total"] * 100 if stats["total"] else 0), ""),
        ("延迟 p95", "%dms" % card.get("p95", 0), ""),
    ]
    card_html = "".join(
        '<div class="card"><div class="k">%s</div><div class="v %s">%s</div></div>'
        % (label, cls, value) for label, value, cls in cards)

    secs = []
    for code, title, desc in CATEGORIES:
        group = [r for r in rows if r["category"] == code]
        if not group:
            continue
        p = sum(1 for r in group if r["passed"])
        trs = []
        for r in group:
            pill = ('<span class="pill ok">通过 · %.2f</span>' % r["score_val"] if r["passed"]
                    else '<span class="pill bad">未通过 · %.2f</span>' % r["score_val"])
            why = ('<div class="why">%s</div>' % html.escape(r["reason"])
                   if r["reason"] else "")
            search_blob = " ".join([r["case_id"], r["input"], r["theory"], r["real"], r["code"]]).lower()
            trs.append(
                '<tr data-p="%d" data-s="%s">'
                '<td><div class="q">%s</div>'
                '<div class="meta"><span class="tag">%s</span><span class="tag">%s</span>%s · %s</div></td>'
                '<td>%s<div class="basis">依据：%s</div></td>'
                '<td>%s</td>'
                '<td>%s%s</td></tr>'
                % (1 if r["passed"] else 0, html.escape(search_blob),
                   html.escape(r["input"]),
                   html.escape(r["case_id"]), html.escape(r["user"]),
                   html.escape(r["judge"]), html.escape(r["layer"]),
                   html.escape(r["theory"]), html.escape(r["basis"]),
                   html.escape(r["real"]),
                   pill, why))
        secs.append(
            '<section data-cat="%s"><h2>%s（%d/%d 通过）</h2><div class="desc">%s</div>'
            '<table><colgroup><col class="c1"><col class="c2"><col class="c3"><col class="c4"></colgroup>'
            '<thead><tr><th>测试集</th><th>理论回答</th><th>真实回答</th><th>评分</th></tr></thead>'
            '<tbody>%s</tbody></table></section>'
            % (code, html.escape(title), p, len(group), html.escape(desc), "".join(trs)))

    sub = ("共 %d 条用例 ｜ 通过 %d、未通过 %d（%.1f%%）｜ 回答取自指定报告；模型与故障模式见原始元数据 ｜ 生成于 %s"
           % (stats["total"], stats["passed"], stats["failed"], stats["rate"] * 100,
              stats.get("generated_at", "")))
    return (HTML_TMPL
            .replace("__SUB__", html.escape(sub))
            .replace("__CARDS__", card_html)
            .replace("__BODY__", "\n".join(secs)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default=os.path.join(ROOT, "scripts", "ai_eval_dataset.json"))
    ap.add_argument("--results", default=os.path.join(ROOT, "scripts", "ai_eval_results.json"))
    ap.add_argument("--md", default=os.path.join(ROOT, "artifacts", "eval-tables", "AI_EVAL_CASE_TABLE.md"))
    ap.add_argument("--html", default=os.path.join(ROOT, "artifacts", "eval-tables", "AI_EVAL_CASE_TABLE.html"))
    args = ap.parse_args()

    dataset = load_json(args.dataset)
    payload = load_json(args.results)
    results = payload.get("results") or {}
    rows = build_rows(dataset, results)
    stats = summarize(rows, payload.get("card"))
    stats["generated_at"] = payload.get("meta", {}).get("run_at") or ""

    md = render_markdown(rows, stats)
    os.makedirs(os.path.dirname(args.md), exist_ok=True)
    os.makedirs(os.path.dirname(args.html), exist_ok=True)
    with open(args.md, "w", encoding="utf-8") as f:
        f.write(md)
    with open(args.html, "w", encoding="utf-8") as f:
        f.write(render_html(rows, stats))

    print("用例 %d ｜ 通过 %d ｜ 未通过 %d ｜ 通过率 %.1f%%"
          % (stats["total"], stats["passed"], stats["failed"], stats["rate"] * 100))
    print("理论回答覆盖 %d/%d" % (len(THEORY), stats["total"]))
    print("已写出 %s" % args.md)
    print("已写出 %s" % args.html)


if __name__ == "__main__":
    main()
