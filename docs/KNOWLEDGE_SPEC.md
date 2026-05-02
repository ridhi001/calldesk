# Knowledge Base Spec (backend)

Adds a managed knowledge base to CallDesk: the FAQ data the agent answers from lives in the database, can be edited over a REST API, every agent answer records which entries it used, and caller questions the agent could not answer are logged as **knowledge gaps**. A separate Next.js dashboard consumes the API below, so **follow the JSON contract exactly** (field names, nesting, types).

Read `AGENTS.md`, `docs/BACKEND_SPEC.md` and `docs/BACKEND_STATUS.md` first, and keep all existing tests passing.

## Environment

- JDK 21 is at `/opt/homebrew/opt/openjdk@21`. All Maven dependencies are cached in `~/.m2`, so build offline:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -o -B verify` (run from `backend/`).
- Run the build after each meaningful step and fix what breaks. If a new dependency would be needed, don't add it; use what is already on the classpath.
- If the sandbox blocks the integration test's local sockets, run `mvn -o -B test` for unit tests and say so in the status file.

## Data model

### `KnowledgeEntry` (JPA entity, table `knowledge_entries`)
`id` (Long), `question` (String, not blank, ≤ 300), `answer` (String, not blank, ≤ 2000), `tags` (List<String>, `@ElementCollection`), `createdAt`, `updatedAt` (Instant), `timesUsed` (int), `lastUsedAt` (Instant, nullable).

On startup, if the table is empty, seed it from the business profile YAML FAQs (`calldesk.business-profile`). The YAML stays the source for business facts (name, greeting, hours, address, handoff number) and the initial FAQ seed only.

### `KnowledgeGap` (table `knowledge_gaps`)
`id`, `question` (the caller's words), `normalizedQuestion` (lowercase, punctuation stripped, whitespace collapsed; used for de-duplication), `firstCallId` (Long), `lastCallId` (Long), `firstAskedAt`, `lastAskedAt` (Instant), `timesAsked` (int), `bestScore` (Double, nullable; best retrieval score at the time), `status` (`OPEN | RESOLVED | DISMISSED`), `resolvedEntryId` (Long, nullable).

### Turn sources
Each agent `TurnRecord` stores the knowledge entries used to answer it as an ordered `@ElementCollection` of an `@Embeddable TurnSource(entryId, question, score)`. `question` is copied at answer time so transcripts stay readable if an entry is later edited or deleted.

## Retrieval

- `KnowledgeRetriever` now reads entries from the repository. Rebuild the BM25 index whenever entries are created, updated or deleted (keep an in-memory index guarded for concurrent reads; rebuild on change).
- New config `calldesk.knowledge.min-score` (default `1.5`, env `KNOWLEDGE_MIN_SCORE`): a match is **confident** when its BM25 score ≥ this. Tune the default so the demo clinic's own FAQ questions match confidently and unrelated questions ("do you sell pet food?") do not; cover this with a test.
- `PromptBuilder` uses only confident matches (top 3). If there are none, the system prompt tells the model it has no verified information for this question and must say so briefly and offer to take a message or transfer, never invent facts.
- `MockLanguageModel`: answer from the top confident match; if none, reply "I'm not sure about that one. I can take a message or connect you with our receptionist." (This makes gaps show up in mock mode.)

## Recording sources, usage and gaps (in `CallSession` / services)

When an agent answer is generated for a caller turn:
1. Persist the confident matches used (top 3) as the agent turn's `sources`, including for interrupted and cut-off turns.
2. Increment `timesUsed` and set `lastUsedAt` on each source entry.
3. If there was **no** confident match and the caller text is a real question or request (at least 4 words after normalization, not a voicemail/handoff phrase, not small talk such as "thank you", "yes please", "okay"), upsert a gap: if an `OPEN` gap with the same `normalizedQuestion` exists, increment `timesAsked` and update `lastCallId`/`lastAskedAt`; otherwise create one.
4. Publish SSE event `gap-added` with the gap DTO when a gap is created or incremented.

## REST API (JSON; CORS for `http://localhost:3000` as today, plus any origin listed in `CORS_ALLOWED_ORIGINS`, comma-separated)

### Business
- `GET /api/business` → `{ "name", "greeting", "hours", "address", "handoffNumber" }`

### Knowledge entries
- `GET /api/knowledge?q=` → `KnowledgeEntryDto[]`, newest `updatedAt` first; optional `q` filters by case-insensitive substring over question, answer and tags.
- `POST /api/knowledge` body `{ "question", "answer", "tags": string[] }` → `201` + `KnowledgeEntryDto`. Validate (not blank, length limits) → `400 { "message" }`.
- `PUT /api/knowledge/{id}` same body → `KnowledgeEntryDto`; `404 { "message" }` if missing.
- `DELETE /api/knowledge/{id}` → `204`.
- `KnowledgeEntryDto`: `{ "id", "question", "answer", "tags": string[], "createdAt", "updatedAt", "timesUsed", "lastUsedAt" }` (instants as ISO-8601 strings).

### Test a question
- `GET /api/knowledge/search?q=...` → `{ "query", "confident": boolean, "matches": [ { "entry": KnowledgeEntryDto, "score": number, "confident": boolean } ] }`, top 5 by score, `confident` at the top level = any confident match. Blank `q` → `400`.

### Gaps
- `GET /api/knowledge/gaps?status=OPEN` (default `OPEN`; also `RESOLVED`, `DISMISSED`, `ALL`) → `KnowledgeGapDto[]`, most `timesAsked` first, then newest `lastAskedAt`.
- `POST /api/knowledge/gaps/{id}/resolve` body `{ "question", "answer", "tags": string[] }` → creates the entry, marks the gap `RESOLVED` with `resolvedEntryId` → `{ "gap": KnowledgeGapDto, "entry": KnowledgeEntryDto }`.
- `POST /api/knowledge/gaps/{id}/dismiss` → `KnowledgeGapDto` with status `DISMISSED`.
- `KnowledgeGapDto`: `{ "id", "question", "timesAsked", "firstAskedAt", "lastAskedAt", "lastCallId", "bestScore", "status", "resolvedEntryId" }`.

### Changes to existing responses
- `TurnDto` gains `"sources": [ { "entryId", "question", "score" } ]` (empty list for caller turns and turns without sources). Keep every existing field.
- `MetricsDto` gains:
  - `"knowledge": { "entries": number, "openGaps": number, "coverageRate": number | null }`, where `coverageRate` = share (0..1) of caller turns that were answered with at least one confident match, counting only caller turns that were real questions per rule 3 above; `null` when there are none.
  - `"latencyTrend": [ { "callId", "startedAt", "p50TurnLatencyMs": number | null } ]`, the last 20 ended calls, oldest first.

## Simulator
- Add scenario `unknown-question.yaml`: the caller asks two things not in the demo FAQ (for example "Do you offer braces for adults?" and "Can I pay in monthly installments?") and then says goodbye; expected outcome `COMPLETED`, producing two open gaps.
- The existing `POST /api/simulate` stays (development and tests only).

## Tests (add; keep all existing ones green)
- `KnowledgeServiceTest` (`@DataJpaTest` + imports): CRUD, validation errors, seeding from YAML only when empty, index rebuild (a newly created entry is immediately searchable, a deleted one is not).
- `KnowledgeRetrieverTest`: confident vs not confident with the default `min-score` on the demo FAQ.
- `KnowledgeGapTest`: gap created for an unanswered question, de-duplicated by normalized text (`timesAsked` = 2), not created for small talk or short utterances, resolve creates an entry and marks the gap resolved, dismiss.
- `CallSessionTest`: an agent answer persists its sources, and a no-match question records a gap.
- `KnowledgeApiTest` (MockMvc): the JSON shapes above, including `sources` on turns and the new metrics fields.
- Extend `SimulatedCallIT` with `unknown-question` → `COMPLETED` and 2 open gaps.

## Docs
- Update `backend/README.md` (API table and a short "Knowledge base" section) and the root `.env.example` (add `KNOWLEDGE_MIN_SCORE`, `CORS_ALLOWED_ORIGINS`).
- Append a "Knowledge base" section to `docs/BACKEND_STATUS.md`: what you built, the final test count from your last `mvn verify` run, and anything unverified.
