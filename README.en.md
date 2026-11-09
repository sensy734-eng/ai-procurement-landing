# Procurement AI Workbench · AI Integration into an Existing System

[简体中文](README.md) | **English**

**Integrate AI into existing business workflows with permission checks, verifiable evidence, conservative fallbacks, and measurable retrieval quality.**

This independent engineering portfolio project demonstrates AI application and Java full-stack skills. Procurement supplies a reproducible environment with orders, inventory, policy documents, and role-based data scopes. The primary deliverables are the AI integration and evaluation layers built on that environment.

Natural-language questions are routed to controlled business queries or retrieval-grounded policy Q&A. AI is read-only; approvals and other business changes use the existing business pages. All accounts, records, prices, and policies are fictional demo content.

## Engineering focus

- Controlled access: intent allowlists, entity and parameter checks, and existing business query services.
- Permission boundaries: department and warehouse scopes, with stale conversation context invalidated when access changes.
- Grounded answers: current-source citations, numeric and unit validation, and clear reference-only or refusal modes.
- Failure handling: conservative behavior for disabled models, timeouts, invalid responses, and failed validation.
- Retrieval evaluation: BM25, hashing, semantic vectors, and RRF comparisons with separate development and holdout sets.
- Reproducible delivery: containers, automated checks, and reports tied to code and dataset versions.

The business modules provide data and constraints for these demonstrations. See [scope and contribution boundaries](docs/product-overview.md) and [verified results and outstanding work](docs/implementation-status.md).

## Demo

Two task assistants are available locally. Sign in as `ai_buyer / 123456`. **Order review** combines scoped facts, price/delivery checks and cited interpretations; use `AI-DEMO-ORDER-RISK`. **Request drafting** extracts source spans, resolves catalog ambiguity and appends user-confirmed rows to the unsaved form. Models are disabled by default: facts-only review and offline templates are explicitly labeled. Five materials, eight orders and two projects cover missing evidence, ambiguity and access boundaries. See [implementation and validation](docs/ai-task-assistants.md). Real-model quality remains unevaluated without credentials.

The home page prioritizes **Orders**, **Stock**, **Policies**, recent AI conversations, retrieval experiments, and query audits. Business metrics and maintenance links sit in a collapsed verification section. Samples fill the input only; the user submits them. Policy responses distinguish generated answers, reference-only excerpts, and refusals.

Follow the demo through natural-language queries, role-based scope checks, cited policy answers, model failure handling, and retrieval evidence. Cloud models are disabled by default; rule-based data queries and conservative references remain available. Real generation and semantic experiments require credentials.

## Start locally

Prerequisite: Docker Desktop with a running engine.

```sh
docker compose up --build -d
```

Open [http://127.0.0.1:18080](http://127.0.0.1:18080). The web service binds only to localhost. MySQL uses a dedicated named volume; initialization runs only for an empty volume. Restarting preserves data. To explicitly reset this demo project: `docker compose down -v`, then start it again. Never run `db/00_create_database.sql` against an existing database.

Demo credentials are listed on the login page; the initial password is `123456`. For real model calls, use an ignored root `.env` file with `LLM_API_KEY` and `EMBEDDING_API_KEY`, and explicitly set `LLM_ENABLED=true` for chat. Build the semantic index explicitly before enabling semantic retrieval, as described in [local release](docs/release.md). Calls may incur provider charges. By default the app uses BM25 plus local hashing vectors without cloud calls.

Local development targets JDK 17, Maven Wrapper 3.9.9, Node 22, Python 3.12 and MySQL 8.4. Run backend tests with `server/mvnw` or `server/mvnw.cmd`; run frontend checks with `npm run typecheck` and `npm run build` from `web`.

## Architecture

Vue 3 / TypeScript / Element Plus → Spring Boot 3 / Java 17 → MySQL. Existing permission codes and business routes remain compatible. The model output is validated as untrusted input: only actual cited passages can justify numeric claims, references must be integer indices in the current prompt, and failed generations yield labeled source excerpts or a refusal.

Retrieval supports BM25, BM25 + hashing vectors, semantic vectors and BM25 + semantic RRF. Semantic embeddings default to `text-embedding-v4`, 1024 dimensions; endpoint, key, model and dimensions are environment configuration. Vectors are stored separately with configuration and content hashes. Cloud requests occur outside database transactions; short commits recheck the source content.

## Evaluation and limits

The original 88-case reports remain historical baseline evidence. The new retrieval dataset has 60 questions split into 30 development and 30 frozen holdout cases; paraphrases stay in the same split. The added fictional corpus contains 8 documents and 64 active chunks; existing active documents remain in the serving corpus manifest. `scripts/rag_eval_runner.py` runs strict comparisons and records commit, dataset/corpus hashes, actual strategy, per-question evidence, Recall@5, MRR and latency. Use the development split while tuning and run holdout only after freezing the configuration.

No real-model quality or semantic-retrieval improvement is claimed until experiments are run with valid credentials. Numeric validation does not establish semantic completeness, exceptions or negation fidelity. Vector retrieval uses a full scan suitable for this demo scale. Local container startup and database integration have been verified; real-model evaluation, the complete four-strategy comparison, and the final recording remain outstanding.

See [implementation status and outstanding acceptance items](docs/implementation-status.md), [AI integration scope](docs/product-overview.md), [architecture](docs/architecture.md), [testing](docs/testing.md), [local release](docs/release.md), [demo script](docs/demo-script.md), and [retrieval evaluation](docs/retrieval-evaluation.md).
