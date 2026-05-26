# Backend status

## Built

- Created the Java 21 / Spring Boot 3.5 Maven backend, environment-backed configuration, default file-based H2 storage, and a PostgreSQL profile.
- Added μ-law encode/decode, frame-based energy VAD, YAML business-profile loading, BM25 FAQ retrieval, and prompt construction.
- Added mock STT, LLM, TTS, and call-control providers for zero-key operation, plus OpenRouter, Cartesia, and Twilio REST provider adapters.
- Added TwiML and status webhooks, optional Twilio signature validation, the `/twilio/media` WebSocket handler, serialized media/mark/clear output, and per-call session state for turn cancellation, barge-in, eager replies, sentence streaming, silence prompts, voicemail, handoff, and latency capture.
- Added JPA call/turn storage, paged call and detail APIs, metrics, SSE events, and the six scripted WebSocket simulator scenarios.
- Added unit and integration test sources, a tagged mock-pipeline latency benchmark, backend README, Dockerfile, Compose setup, CI workflow, `.env.example`, and `.gitignore`.

## Unverified details

- Cartesia WebSocket URLs, authentication headers, model identifiers, request fields, and response event shapes are centralized in `CartesiaApiDetails`. Their current values and payload handling were not checked against Cartesia’s live documentation and need verification before using real Cartesia credentials.
- The Twilio HMAC-SHA1 signing algorithm and URL construction are implemented, but the test computes its expected signature locally; the test vector was not cross-checked against Twilio’s documented example. Public URL and proxy behavior also need verification with the deployed ingress setup.
- Real Twilio call updates, provider streaming, and mark playback have not been exercised against external services. The simulator models mark timing locally.

## Left to do

- Run `mvn -o -B verify` in an environment that permits local socket binds; this sandbox blocks the simulator integration test before its test body. The 29 unit/API tests pass; details are in the Knowledge base section below.
- Verify the external provider details above, then exercise the real Twilio, Cartesia, and OpenRouter paths with credentials in a local tunnel setup.

## Review after generation (compiled and run on JDK 21)

The generated code needed two compile fixes (a missing `java.util.Map` import and a Mockito wildcard stub). Running every simulator scenario with realistic mock delays, not just the fast `sim` profile, surfaced these behavior bugs, now fixed and covered by tests:

- **Agent talked over the caller.** If the caller resumed speaking while a reply was still being generated, the reply was not cancelled and played over them. Now the pending (and speculative) reply is dropped and the next final transcript is answered with both caller messages in history. Test: `callerResumingBeforeAgentSpeaksCancelsThePendingReply`.
- **Wrong text recorded on barge-in.** Only the last sentence sent to TTS was tracked, so an interruption recorded a sentence the caller had not heard yet. Now unplayed sentences are kept in order and the head of the queue (what is playing) is recorded. Test: `bargeInClearsAudioAndDropsStaleTurnOutput` asserts the exact text.
- **Reply lost on hang-up.** A call ending mid-reply dropped the agent turn. It is now persisted as interrupted with what the caller heard. Test: `callerHangingUpMidReplyKeepsWhatTheyHeard`.
- **Every sentence delayed by the handoff filter.** It always held back the last 9 characters, so the end of each sentence waited for more tokens. It now holds back only a tail that could still become `[HANDOFF]`. Median mock turn latency went from 255 ms to about 200 ms. Test: `handoffMarkerSplitAcrossTokensTransfersWithoutBeingSpoken`.
- **Handoff recorded as completed.** Twilio ends the media stream because of the transfer, so `stop` could race the `HANDED_OFF` bookkeeping. A `transferRequested` flag now decides the outcome. Test: `streamStoppingDuringTransferIsStillRecordedAsHandoff` (verified to fail without the fix).
- **Live stream looked disconnected when idle.** `SseEmitter` only commits headers on the first write; it now sends a `:connected` comment on subscribe plus a 25 s heartbeat. Test: `CallEventPublisherTest`.
- **Tests shared the dev database file.** The integration test now uses its own in-memory H2; the dev database lives in `backend/data/` (gitignored).
- **Simulator timing.** Barge-in now waits for the agent's audio to actually start; playback waits only count marks received after the caller finished speaking; the simulated caller lingers 1.5 s before hanging up so the server can end the call itself.
- Added `outcome` filtering to `GET /api/calls` (used by the dashboard). Test: `CallPersistenceServiceTest`.

Result: 22 tests pass (`mvn -B verify`), and all six scenarios end with the expected outcome under realistic mock delays.

## Knowledge base

- Added database-backed `KnowledgeEntry` and `KnowledgeGap` records, seeding FAQs from the configured YAML profile only when the entry table is empty, and a concurrency-safe BM25 index that rebuilds on knowledge changes.
- Added the business, knowledge CRUD/search, and knowledge-gap APIs, custom CORS origins through `CORS_ALLOWED_ORIGINS`, gap SSE events, resolution/dismissal, and ordered source records on agent turns. Source entry usage counts update atomically.
- Updated prompts and the mock LLM to use only confident matches; no-match questions receive the stated fallback. Added question coverage and the last-20-call latency trend to metrics, plus the `unknown-question` simulator scenario.
- Tuned `KNOWLEDGE_MIN_SCORE` to `2.2`: all 12 demo FAQ questions test as confident, while pet food and both unknown-scenario questions test below threshold.
- Updated `backend/README.md` and the root `.env.example` with the API and knowledge configuration.

Verification on JDK 21 with Maven offline: the final `mvn -o -B verify` run passed all 29 unit/API tests, then the one `SimulatedCallIT` failed before its test body because the sandbox denied Tomcat's local socket bind (`java.net.SocketException: Operation not permitted`). Per `KNOWLEDGE_SPEC.md`, `mvn -o -B test` was run separately and passed all 29 tests. Full verification of the WebSocket scenarios, including the new unknown-question scenario, remains unverified in this socket-restricted sandbox. Real Twilio, Cartesia, OpenRouter, and PostgreSQL paths also remain unverified.

## Knowledge base review (after generation)

Codex's knowledge base passed its own tests; the integration test could not run in its sandbox, so it was run here and passed. Probing retrieval with realistic caller questions and running every scenario found these problems, now fixed and tested (`KnowledgeRetrieverTest`, `KnowledgeQuestionClassifierTest`):

- **One shared word made a confident match.** "Is there a female dentist available?" was answered with "Is parking available?". Confidence now also requires IDF-weighted coverage of at least half the question, generic request words ("offer", "provide", "available", ...) are stopwords, and "my"/"there" no longer pull unrelated entries to the top.
- **Filler hid real answers.** "I would like to book an appointment" became a knowledge gap because "like" counted as an unseen, distinctive word. Conversational filler is now ignored.
- **Statements became gaps.** "Tomorrow afternoon would work for me" (the caller answering the agent) was logged as an unanswered question. Gaps are now only recorded for questions and requests.
- **Noisy sources.** The opening-hours answer also cited the emergency entry ("outside opening hours"). Question words now count twice when indexing, and only matches within 60% of the top score are confident.
- Light stemming ("opening" ↔ "open"), FAQ `tags` in the business profile YAML, and a single demo business (Brightsmile Dental) shared by the backend seed and the dashboard's demo data.

Result with twelve simulated calls: 2 open gaps (exactly the two questions the knowledge base can't answer, each asked twice), 69% knowledge coverage, precise sources. 45 tests pass (`./mvnw verify`).
