# Landing AI in Production: Treat Model Output as Untrusted Input

[简体中文](README.md) | **English**

> Four judgments, one acceptance loop, and a complete run-through in a procurement system.

"The demo was stunning; nobody uses it in production" is the most common way AI features die. The cause is rarely a weak model. It is the integration: the model gets treated as a trusted business component and dropped straight into the main flow.

Change one premise and every engineering decision becomes obvious:

> **A model is a new input channel. It improvises, it states falsehoods with confidence, and it can be talked into things. Treat what it produces the way you treat a submitted form: validate first, then use.**

This is not a metaphor. The first rule of web security is "never trust client input." Once a system gains an input channel that speaks natural language, the same rule applies; only the thing being validated changes — from strings to intents, entities, and parameters.

What follows are four judgments. Each comes with a counterexample, a measurement, and one sentence you can take with you.

Every example comes from a procurement management system. But that is only one carrier — a vehicle that happens to satisfy three conditions: there is a source of truth, there are known boundaries, and correctness is decidable. Swap the business and the method still holds. What cannot be swapped out is those three conditions, not the procurement domain.

---

## 1. Four judgments

### Judgment 1: Is this a scenario worth letting AI into?

Not every need should be solved by a model. Three gates before entering:

| Gate | Question | What happens if it fails |
|---|---|---|
| **A source of truth** | Can the correct answer be found in some source? | The model can only invent, and it invents convincingly |
| **Known boundaries** | Are there well-defined cases where it cannot answer, and can those be detected? | It can only hedge; users cannot tell "not found" from "not saying" |
| **Decidable correctness** | Can right and wrong be judged by a machine? | Improvement cannot be measured, only felt |

Counterexamples are everywhere: open-ended creative generation (no source), free-form SQL across a database (no boundary), "write me a plan" (no decidable correctness). These are not impossible to build. They are impossible to **accept** — you can demo them, you cannot ship them.

> **Take this with you:** ask "how would we find out it's wrong," not "can we build it."

### Judgment 2: What goes to the model, and what never does

Split a question-answering request into two halves and draw the line between them:

| Layer | Owner | What it covers |
|---|---|---|
| **Understanding** | Model | What is being asked, which object it concerns, what constraints apply, which time window |
| **Adjudication** | Code | Whether the intent is supported, whether the object exists, whether parameters are valid, whether this user may see it, whether it is read-only |

The critical rule: **a value produced by the model has exactly the same trust level as a value extracted by a regex.** Both pass the same gates. Not "trust the model, fall back to regex," but "whoever produced it, if it does not comply, reject it." That way, swapping models, rewriting prompts, or a model having a bad day never lowers the safety floor.

Three lessons from actual failures explain why this line cannot be relaxed.

**1. A model will helpfully rewrite a business code into a primary key.**
The question contains `M-1001`; the model returns `material_id: 1001`, while the real primary key is `1`. The query runs, returns an empty set, and reports `NoData` — **without raising any error**. The user concludes the material does not exist. In reality the model invented a primary key. So entity parameters never accept values from the model; they accept only business codes or names, and code resolves them against the master data.

**2. Telling a command from a question requires tone, not keywords.**
"Approve" and "confirm receipt" are both action names and state names. With keyword matching, "has this order been approved yet" gets rejected as a command. The test has to be which marker wins: command markers ("please," "go ahead and," "help me") versus information markers ("has it," "any," "which," "what's the status").

**3. A model saying "I shouldn't answer this" is advisory only.**
Its refusals must fall back to rule-based re-checking. Otherwise a prompt edit can silently drop a whole category of questions.

In code this becomes four gates. Failing any one returns an explicit result code; it never degrades into guessing:

```
intent allowlist → entity resolution → parameter extraction → operator_set / enum_values validation
```

> **Take this with you:** don't ask "how accurate is the model," ask "what happens when the model is wrong." The first is tuning; the second is design.

### Judgment 3: Treat "AI is not there" as a first-class citizen

Most AI features have exactly one channel: what to do when the model responds. So when the model stalls, the whole feature becomes a long wait. Real failures are far more varied than "an error": timeouts, rate limits, HTTP 200 with an empty body, a response that is not JSON, a code outside the contract. Traditional monitoring counts every one of these as a success.

So define **what counts as a failure** first:

| Signal | Counts as failure | Why |
|---|---|---|
| Timeout / network error / 5xx / rate limit | Yes | Ordinary unavailability |
| Authentication failure | Yes, and trips the breaker immediately | A wrong key will not become right after ten thousand retries |
| Empty response / not JSON | Yes | HTTP 200 but unusable; an ordinary circuit breaker cannot see it |
| Output violates the contract (code outside allowlist) | Yes | Same class as a parse failure |
| **Low confidence** | **No** | A quality problem, not an availability problem. Counting it trips the breaker while the model is healthy, which loses points |

The last row was written after being burned. If "I'm not sure" counts as a failure, a few consecutive cautious answers trip the breaker and push answerable questions onto the fallback path.

Then **derive the budget backwards from the business deadline**, instead of setting the timeout to the average latency. Each of the three call chains gets a budget; when it expires, fall back immediately and never wait out the full HTTP timeout:

| Scenario | Before | After |
|---|---|---|
| First request, peer does not respond for 5,000 ms | Wait the full 5,000 ms | Give up at **231 ms**, use fallback |
| After consecutive failures | Still wait 5,000 ms each time | **0 ms**; no call is made |
| Normal request | No extra cost | No extra cost |

> **Take this with you:** availability does not come from a stable model, it comes from a system that refuses to wait for one.

### Judgment 4: Define the scoring rubric before discussing quality

Optimization without a rubric is just rephrasing. This project turned acceptance into a repeatable, machine-judged run: **88 cases, 12 categories, 100 points, four thresholds** (safety 40, veto-weighted / function 30 / quality 20 / robustness 10).

The categories are not organized by feature but by **failure mode**: prompt injection, privilege escalation, fabrication, failing to refuse, misspelled abbreviations, multi-turn references, step ordering, oversized input. Each one maps to something that really happens.

Scoring does not depend on whether an answer "sounds human." It is structural: an allowed set of result codes, synonym groups for required facts, forbidden regexes, citations that must resolve to a real chunk, numbers that must be traceable to a source, and a database guard (business data must be `unchanged` before and after).

The first payoff of a rubric is **controlled experiments**: the same code, only the model switch flipped.

| Tier | Safety | Structure | P1 | Total |
|---|---|---|---|---|
| Model tier | **100%** | **92.9%** | **90.0%** | **97.4** |
| Rule fallback tier | 84.4% | 71.4% | 36.7% | **0** |

The rule tier is not "slightly worse"; it fails in the wrong way. Its losses concentrate in fabricated answers: "how is an over-budget purchase approved" gets answered with the approval deadline; "sync data to finance" gets answered with inbound confirmation. Rules can catch keywords. They cannot catch what the sentence is asking.

And the model tier reaches 100% safety not because it is better behaved, but because **the rules are written in code**. Put the two together:

> Rules cannot replace the model, and the model cannot replace adjudication.

The second payoff is catching problems **in the rubric itself**. Once, a correct refusal was scored as a leak: refusal text is generated freely by the model, and it wrote "does not include scoring dimensions, metrics, **weights**, or grading rules" — and "weights" was on the forbidden list. Safety dropped from 100% to 96.9% and the total score collapsed to zero.

The fix was not a prompt change but a rubric change: **in the refusal state, only numeric forbidden terms are checked.** A noun appearing inside an explanation of what is missing is not a leak. Left unfixed, this class of "score drifts with wording" turns the evaluation into random grading.

> **Take this with you:** an eval set does not test the model, it tests your rubric. An unclear rubric makes the score meaningless.

---

## 2. From judgments to a closed loop

The four judgments are not four parallel activities. They are four checkpoints on one pipeline:

| Stage | Output | Criterion |
|---|---|---|
| Entry | Session context, including the raw text of the last two turns | Follow-ups connect; standalone questions are not polluted |
| Understanding | Intent + entity references + parameters + normalized time | Output must fall within the contract's enumerations |
| Adjudication | Pass, or the reason for refusal | Allowlist, entity existence, parameter validity, data scope, read-only |
| Execution | A result set or an explicit result code | Read-only; scope injected server-side |
| Expression | Answer + clickable citations | Citations must exist and belong to the document cited |
| Scoring | Score + per-case detail + regression diff | Four thresholds; stable across consecutive runs |

The last step feeds the first. Failures are grouped by failure mode, added to the eval set, and the next round runs again. **That is what the closed loop buys you: every improvement can name which class it fixed, instead of "it feels better overall."**

<p align="center"><img src="docs/diagrams/11-系统运行图.png" alt="System runtime: AI is a side channel; the model can be switched off" width="94%"></p>

<sub>AI is wired in as a side channel: the main flow does not depend on it. A disabled model, a timeout, or output that violates the contract affects only this one conversation, never the business.</sub>

---

## 3. A complete run-through: the procurement system

Four judgments need at least one real end-to-end run to prove they are not just theory. The carrier is manufacturing procurement, chosen for a plain reason: it happens to satisfy all three conditions.

| Condition | What the system provides |
|---|---|
| A source of truth | Policies are documents; answers must land on a clause and point back to article and paragraph |
| Known boundaries | Only five query types are supported; everything else is explicitly unsupported; with no source, the answer must refuse |
| Decidable correctness | 88 cases are machine-scored; business data can be verified as unchanged before and after |

**Any other carrier works the same way**: support tickets, contract review, incident triage. As long as the three conditions hold, this approach transfers. What cannot be substituted is not the business, but the three premises.

### 3.1 The two capabilities

| | **Policy Q&A** | **Governed data query** |
|---|---|---|
| Typical question | "How many days does receiving inspection take?" "How do I submit a purchase request for approval?" | "What state is order CG-DD-20261011-0001 in?" "How many bolts are left?" |
| Where the answer comes from | Knowledge base → chunks → dual retrieval → clause text | Intent allowlist → parameters → parameterized SQL |
| The model does | Decide whether the question is about policy or procedure, compose the answer, cite by clause | Produce intent + entity references + parameters + normalized time in a single call |
| Code does | Retrieval, fusion, refusal decision, citation validity, numeric consistency guard | Allowlist, entity resolution, parameter validation, scope injection, read-only enforcement |
| When it cannot answer | Refuse and state what is missing | One of seven result codes (no data / not found / ambiguous / no permission / unsupported / invalid parameter) |

Both capabilities share one entry point; the front end has two pages (chat, audit). All the complexity lives on the server.

<img src="docs/images/ai-kb.png" alt="Policy Q&A: every answer carries citations" width="100%">
<sub>Policy Q&A — every answer must display its sources (document + section), expandable to the original text</sub>

<img src="docs/images/ai-data.png" alt="Governed query: recognized intent and result set" width="100%">
<sub>Governed query — intent, entities, and filters are all laid out; parameters can be edited and re-run</sub>

Every exchange — successful, rejected, or refused — writes one audit record: question, intent, validated parameters, rejected parameters with reasons, injected data scope, result code, and latency. "Governed" is therefore verifiable, not an adjective:

<img src="docs/images/ai-audit.png" alt="Query audit: result code distribution and per-record detail" width="100%">

### 3.2 Scale of this carrier

| Item | Count |
|---|---|
| Tables / columns | 49 / 718, across eight domains A–H |
| Backend endpoints | 183, all guarded by permission points |
| Core business chain | Purchase request → purchase order → receiving inspection → inbound receipt → stock query (five stages, fully traceable document chain) |
| Backend Java | 349 files / 34,832 lines, of which AI-related: **89 files / 9,759 lines (28%)** |
| Evaluation | 88 cases / 12 categories / 100-point scale, four consecutive full runs identical to the digit |

Scale serves exactly one purpose here: **to give the word "real" some weight.** The cross-table chains, three orthogonal status fields, and department- and warehouse-scoped visibility that 49 tables bring were not designed to make life hard for a model. They were already there.

---

## 4. Does this transfer?

The **structure** transfers. The **knowledge** does not. Being clear about which is which is what stops you from rebuilding everything for the next scenario.

**Five things you can reuse directly**

1. The four judgments themselves: the three gates, the understanding/adjudication split, the "AI is absent" channel design, and rubric-first.
2. The skeleton of the four gates: allowlist → entity resolution → parameter validation → pre-execution re-check. In another domain the contents change; the positions do not.
3. The channel guard (circuit breaker + budget + semantic failure counting). It has nothing to do with the business, only with "an external model."
4. The eval runner: case format, scorers, 100-point scale, regression diff. Swap the dataset.
5. The audit schema: question, intent, parameters, rejection reasons, data scope, result code, latency.

**Three things you must rebuild**

1. The intent allowlist and entity alias table. This is domain knowledge; there is no shortcut.
2. The refusal policy. What counts as unanswerable differs by domain. In contract review, "no issues found" and "cannot check this" are entirely different statements.
3. Expected values for scoring: required facts, forbidden terms, citation anchors — all domain-specific.

**The most common mistake when switching domains**: dragging the previous intent table along. Intents are the language of the business, not of the technology. Change the business and the way users carve up their questions changes completely.

---

## 5. Known limitations

Writing them down is more useful than hiding them. None of the three exceptions is a one-line fix:

| Symptom | Nature |
|---|---|
| Should "has this order been signed yet" automatically filter to approved? | A policy question; both readings are defensible; undecided |
| "Show deliveries by material" → unsupported | A capability boundary; needs a new intent |
| "What about compared to the previous supplier?" → judged ambiguous | Insufficient information: the previous answer had no comparison target, so the model's judgment is correct |

There are also two pieces of engineering debt: refusal details are only written to the message table, while the reserved rejection-log table has no code referencing it; and semantic caching was not built — the eval set is all standalone questions, so the hit rate was zero (if it does get built, see 7.3 for the preconditions).

---

## 6. Reproducing

### 6.1 Running it

Requires JDK 17, Maven 3.6+, Node 22, MySQL 8 or MariaDB 10.4.

```bash
# 1. Create the database (order matters; the final step prints 19 result sets;
#    the 10 violation checks must all be 0)
cd db
for f in 00_create_database.sql 01_schema_a_base.sql 02_schema_b_bill.sql \
         03_schema_c_stock.sql 04_schema_d_approval.sql 05_schema_e_price.sql \
         06_schema_f_platform.sql 07_schema_g_kb.sql 08_schema_h_ai.sql \
         10_init_dict.sql 11_init_platform.sql 12_init_ai.sql 13_init_demo.sql \
         14_init_kb_demo.sql 99_verify.sql; do mysql -u root < $f; done
cd ..

# 2. Backend (8080)
cd server
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# fill in database credentials; leave app.llm empty to get the "AI is absent" channel
mvn -DskipTests package
java -jar target/procurement-server-1.0.0.jar

# 3. Frontend (5173, /api proxied to 8080)
cd web && npm install && npm run dev
```

Demo accounts all use the password `123456`: `admin` (administrator), `caigou01` (buyer), `zhuguan01` (procurement manager), `cangku01` (warehouse clerk). Use `admin` to see everything; use `caigou01` to watch data scoping take effect.

### 6.2 Switching between the two channels

```yaml
app:
  llm:
    enabled: true                 # false = force the fallback channel, no code change needed
    base-url: https://api.deepseek.com
    model: deepseek-flash
    api-key: ${LLM_API_KEY:}      # empty is the same as "upstream unavailable"
    thinking-type: disabled       # deepseek-flash enables chain-of-thought by default; short
                                  # tasks burn the token budget on reasoning and return an empty body
    budget-route-ms: 1500
    budget-plan-ms: 2500
    budget-answer-ms: 2500
```

`GET /api/assistant/llm/health` returns the status, budget, failure count, and breaker snapshot for all three channels. Point `base-url` at an unreachable address to demo the "AI is absent" channel live: the UI still answers, only `generator` flips from `llm` to `rule`.

### 6.3 Running the evaluation

```bash
python scripts/ai_eval_runner.py                    # all 88 cases, Markdown report
python scripts/ai_eval_runner.py --layer P0         # P0 only
python scripts/ai_eval_runner.py --case C2-04       # reproduce a single case
python scripts/ai_eval_runner.py --no-db            # skip the database guard (remote DB)
python scripts/ai_eval_runner.py --check            # validate dataset format
python scripts/ai_eval_runner.py --compare a.json   # diff against a previous run: fixed / regressed
```

### 6.4 Where the code lives

```
server/src/main/java/com/zufe/procurement/
  llm/            model gateway and channel governance — AiChannelGuard (breaker + budget),
                  OpenAiCompatibleGateway (JDK HttpClient, zero dependencies)
  m7/assistant/   conversation orchestration — single entry, routing, sessions, audit,
                  AiContext (carries the raw text of the last two turns)
  m8/qa/          policy Q&A — dual retrieval → RRF → refusal decision → composition →
                  citation validation → numeric guard
  m9/query/       governed query — LlmQueryPlanner (intent + entities + parameters in one call),
                  the four gates, executor
  m10/kb/         knowledge base — parsing (md/docx/pdf, zero dependencies), chunking,
                  inverted + vector index, retrieval bench
  m1~m6/m11/m12   the procurement domain itself — master data, requests, orders, receiving,
                  inbound and stock, pricing, permissions, dashboards

web/src/views/business/ai/
  AssistantChat.vue    chat (multi-turn, parameter card, result table, citation block)
  AiAnswerCard.vue     answer rendering (the single mapping from result code to UI state)
  AssistantAudit.vue   query audit

scripts/
  ai_eval_runner.py    eval runner (5 scorers + 100-point scale + Markdown report)
  ai_eval_dataset.json 88 cases (v2.2, machine-judgeable)
  ai_eval_*.json       six archived runs, for diffing "what changed, how many cases moved"
```

### 6.5 Documentation

| File | Contents |
|---|---|
| `docs/AI_DUAL_CHANNEL.md` | Dual-channel research, seven options evaluated, where each landed, measurements (including why B/C/F/G were rejected) |
| `docs/AI_EVAL_PLAN.md` | Evaluation design: case design, scorers, thresholds, five rounds of measurements |
| `docs/AI_EVAL_REPORT.md` | Latest full evaluation report (scorecard, per-category pass rate, failure detail) |
| `docs/AI_EVAL_CASE_TABLE.md` | All 88 cases listed individually (input, expectation, scoring method, note) |
| `docs/diagrams/` | Eleven diagrams; #10 covers the dual-channel design, #11 is the system runtime view |
| `DESIGN_OVERVIEW.md` | Architecture layers, state machines, data flow, AI paths |
| `DATA_DICTIONARY.md` / `API_LIST.md` | 49 tables / 718 columns, 183 endpoints, generated from code and schema; `--check` verifies consistency |

All linked documents are written in Chinese.

---

## 7. What comes next

Everything above is already done. This section is about what is not — four layers, ordered by distance, each with what to do and how we will know it worked.

### 7.1 Near term: the fallback tier's goal is "never mislead," not "answer more"

The rule fallback tier currently scores 84.4% on safety and 0 overall — safety is a veto item, so a shortfall there voids every other point.
But it loses differently from the model tier: not by answering shallowly, but by **fabricating** — "how is an over-budget purchase approved" answered with the approval deadline, "sync data to finance" answered with inbound confirmation. Rules can catch keywords; they cannot catch what the sentence is asking.

So the right goal for the fallback tier is not "get closer to the model," but **never mislead**:

- Answer only questions the rules can structure (single entity, single condition, known intent)
- Refuse everything else explicitly, saying "this question cannot be understood right now," instead of guessing something plausible
- Trade functional score for safety: push safety past 95% even if P1 does not move

Success criterion: fallback safety ≥95%, and no fabricated answer may pass. **This matters more than accuracy.** A user misled once by a fabricated answer never comes back; a refusal only means this one attempt did not help.

### 7.2 Medium term: let the loop turn on its own

The loop is still missing one segment. Today, when something fails in production, a human has to read the audit log and hand-write a case before it reaches the eval set. That segment should be automatic.

- Sample three kinds of records from the audit table weekly: non-success result codes, parameters rejected by the gates, and repeated questions within one session (a sign the first answer missed)
- Auto-draft the case (question plus the intent, parameters, and result code at the time); a human only fills in the expectation
- Criterion: a newly discovered production failure reaches the eval set and reproduces within two weeks

Two supporting changes:

- **Grow the eval set past 240 cases.** The smallest category has only 4 cases, so one wrong answer is 25 percentage points and "did this category improve" cannot be seen at all. Rebalance by failure mode: at least 20 cases per category, injection and privilege escalation at 20+ each, and P1 from 30 to 80.
- **Switch the refusal threshold to idf weighting.** The current test is "top vector score < 0.35 and fewer than 2 token hits," and generic words like "procurement" or "policy" can supply those 2 hits on a question with no evidence behind it. With idf weighting, generic words approach zero weight and only informative terms count.

### 7.3 Medium term: latency and cost

p95 is 1,925 ms — inside budget, but still slow for an interactive exchange. Three levers:

| Lever | Approach | Risk |
|---|---|---|
| Semantic cache | Return on hit, skip both model calls | The cache key must include `user + role + data scope`, or you answer A's data to B; cache read-only results only, with a short TTL |
| Streaming | Emit the first token, then the body | Citation and numeric guards need the full text, so during streaming the UI can only show an "unverified" state |
| Smaller model for understanding | Swap only the planner; adjudication untouched | Bad understanding is stopped by the four gates; the cost is more refusals, not a safety incident |

The third one is the dividend of this architecture: **you can move to a cheaper or even weaker model without lowering the safety floor**, because the floor is not in the model. In day-to-day operation this argues the case better than any evaluation score.

### 7.4 Further out: from "look it up" to "get it done"

Data access is read-only today, and every write is refused — deliberately. To open it up, three preconditions are non-negotiable:

1. **Draft state**: the model may only produce a "pending confirmation" document draft, never a committed record, aligned with the existing draft state in the lifecycle
2. **Second confirmation of parameters**: quantity, unit price, and delivery date must be laid out for the user to confirm before taking effect
3. **Idempotency key**: a sentence can be resent or retried concurrently, so writes must carry one

The point is not the gimmick of "building an order by talking." It is **cutting form-filling time for high-frequency writes from minutes to seconds**, on the precondition of **zero erroneous writes** — if that cannot be guaranteed, it does not ship.

### 7.5 What we want to achieve

| Dimension | Now | Target | Why this number |
|---|---|---|---|
| Model tier total | 97.4 | ≥98 | Two of the three remaining failures close with a policy decision and a new intent; the third is under-specified, and the model was right |
| Fallback tier safety | 84.4% | ≥95% | The fallback tier exists to avoid misleading, not to answer a lot |
| Eval set size | 88 cases | ≥240 cases | 12 categories × 20 cases; P1 from 30 to 80 |
| Impact of one case on a category score | 8–25 points | 5 points | With 4 cases in a category, one failure wipes the category out |
| Latency p95 | 1,925 ms | ≤1,200 ms | The perceptual threshold; cache plus earlier first token |
| Time to first token | Full response only | ≤400 ms | Streaming; the user sees the opening immediately |
| Semantic cache hit rate | 0 (not built) | ≥15% | Real questions repeat, but the key must carry scope |
| Production failure → eval set | Manual, weeks | ≤2 weeks, automatic | The last segment of the loop |

In one sentence: **make safety something the system cannot get wrong later, not something this version happened to get right.**

The first comes from one review. The second only comes from a loop that turns by itself.
