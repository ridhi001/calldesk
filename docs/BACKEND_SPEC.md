# CallDesk Backend Spec

CallDesk is an AI phone receptionist. A caller dials a Twilio number, Twilio streams the call audio to this backend over a WebSocket, and the backend runs a real-time loop: voice activity detection → speech-to-text → LLM → text-to-speech → audio back to the caller. Every call and turn is stored with latency metrics and exposed over a REST API for a separate Next.js dashboard.

The hard part, and the point of the project, is making the conversation feel fast and natural: barge-in, eager replies, streaming LLM output into TTS sentence by sentence, and a silence watchdog.

## Ground rules

- Write everything from scratch. Do not copy code from any other repository.
- Never commit secrets. Keys come from environment variables only; ship a `.env.example` with names and safe defaults.
- **Everything must run with zero API keys.** Each external provider (STT, LLM, TTS, Twilio call control) sits behind an interface with a mock implementation, selected by config. Mock mode is the default.
- Java 21, Spring Boot 3.5.x, Maven. Package root `com.calldesk`.
- Java/Maven are not installed in your sandbox and there is no network, so you cannot compile or run tests. Write code carefully: correct imports, no invented APIs, consistent names across files. Prefer the JDK (`java.net.http.HttpClient`, `java.net.http.WebSocket`) over extra libraries. Keep dependencies to Spring Boot starters (web, websocket, data-jpa, validation, actuator), H2, PostgreSQL driver, SnakeYAML (comes with Boot), and test starters.
- Code style: small focused classes, constructor injection, records for DTOs/value types, no Lombok. Comments only where the logic is non-obvious (the audio and turn-taking code deserves them).

## Configuration (`CallDeskProperties`, prefix `calldesk`)

- `public-base-url` (e.g. `https://abc.ngrok.app`), used to build the media stream `wss://` URL.
- `providers.stt|llm|tts`: `mock` (default) | `cartesia` (stt, tts) | `openrouter` (llm).
- `cartesia.api-key`, `cartesia.stt-model`, `cartesia.tts-model`, `cartesia.voice-id`, `cartesia.version`. Keep the Cartesia endpoint URLs and message field names in one small class so they are easy to verify against current docs; mark them `// verify against Cartesia docs`.
- `openrouter.api-key`, `openrouter.model`, `openrouter.base-url` (default `https://openrouter.ai/api/v1`).
- `twilio.account-sid`, `twilio.auth-token`, `twilio.validate-signature` (default false).
- `business-profile`: path to a YAML business profile (default `classpath:business/demo-clinic.yaml`).
- `vad`: `energy-threshold`, `speech-start-ms` (default 60), `speech-end-ms` (default 500), frame size 20 ms.
- `silence`: `reprompt-after-ms` (8000), `hangup-after-ms` (20000).
- `eager-reply.enabled` (true), `eager-reply.stable-partial-ms` (250).

## Modules

### `telephony`
- `POST /twilio/voice`: returns TwiML `<Response><Connect><Stream url="wss://{base}/twilio/media"/></Connect></Response>` with `from`/`to`/`callSid` passed as `<Parameter>`s.
- `POST /twilio/status`: records final call status.
- `TwilioSignatureValidator`: HMAC-SHA1 validation of `X-Twilio-Signature` when enabled.
- `TwilioMediaStreamHandler` (Spring `TextWebSocketHandler` on `/twilio/media`): parses Twilio Media Streams JSON events `connected`, `start`, `media` (base64 μ-law 8 kHz, 20 ms frames), `mark`, `stop`. Creates one `CallSession` per stream. Outbound messages: `media` (base64 μ-law), `mark` (to know when playback finished), `clear` (to stop playback on barge-in). All outbound sends go through one serialized sender per session (WebSocket sends must not interleave).
- `CallControl` interface: `transferTo(callSid, phoneNumber)`, `hangup(callSid)`. `TwilioCallControl` uses the Twilio REST API (update call with TwiML `<Dial>`); `MockCallControl` just records the action.

### `audio`
- `MuLaw`: G.711 μ-law decode table → PCM16 and encode PCM16 → μ-law.
- `EnergyVad`: per 20 ms frame, RMS of PCM16. State machine SILENCE → SPEECH after `speech-start-ms` above threshold; SPEECH → SILENCE after `speech-end-ms` below. Emits `onSpeechStart` / `onSpeechEnd`. Pure and unit-testable (no clocks inside; frame count drives time).

### `stt`
- `SpeechToText` creates an `SttStream` per call: `sendAudio(byte[] mulaw)`, `close()`, with a listener `onPartial(text)` / `onFinal(text)`.
- `CartesiaSpeechToText`: streaming over WebSocket.
- `MockSpeechToText`: emits the next line from a per-call script queue when the VAD reports end of speech, with a configurable delay. The simulator fills that queue, which is how full calls run without real speech.

### `llm`
- `LanguageModel.stream(List<ChatMessage>, TokenListener, CancellationToken)`.
- `OpenRouterLanguageModel`: OpenAI-compatible `/chat/completions` with `stream:true`, parsing server-sent events.
- `MockLanguageModel`: answers from the business profile's FAQ via the retriever, streams words with a configurable per-token delay, and emits `[HANDOFF]` when asked for a human.

### `tts`
- `TextToSpeech.stream(String text, AudioListener, CancellationToken)` producing μ-law 8 kHz chunks.
- `CartesiaTextToSpeech`: WebSocket streaming, output format `pcm_mulaw` 8000 Hz.
- `MockTextToSpeech`: generates a short tone whose length is proportional to the text, with a configurable first-chunk delay.

### `knowledge`
- `BusinessProfile` loaded from YAML: `name`, `greeting`, `hours`, `address`, `handoffNumber`, `faqs: [{question, answer}]`.
- `KnowledgeRetriever`: BM25 over FAQ question+answer (tokenize, lowercase, drop stopwords); returns top-k with scores.
- `PromptBuilder`: system prompt with business facts, top FAQ matches, and rules (short spoken sentences, no markdown, ask one question at a time, emit `[HANDOFF]` to transfer).
- Ship `src/main/resources/business/demo-clinic.yaml`, a fictional dental clinic with around 12 FAQs.

### `conversation` (the core; test it thoroughly)
`CallSession` owns one call. Turn-taking rules:
1. **Greeting** when the stream starts.
2. **Turn IDs**: every user turn increments a turn id; any work (LLM, TTS, sending) belonging to an older turn is cancelled and its output dropped.
3. **Barge-in**: if VAD reports speech start while the bot is speaking, send Twilio `clear`, cancel the current turn, and mark the bot turn `interrupted=true` with the text actually spoken so far.
4. **Eager reply**: when a partial transcript stays unchanged for `stable-partial-ms`, start generating the reply speculatively. If the final transcript normalizes to the same text, reuse it; otherwise cancel it and start fresh.
5. **Streaming pipeline**: `SentenceChunker` splits the LLM token stream at sentence boundaries (`.`, `?`, `!`, and long comma clauses), sending each sentence to TTS as soon as it completes, so audio starts before the LLM finishes.
6. **Silence watchdog**: no caller speech for `reprompt-after-ms` → "Are you still there?"; for `hangup-after-ms` → goodbye and hang up (outcome `ABANDONED`).
7. **Voicemail detection**: the first final transcript matches voicemail phrases ("leave a message", "after the tone", ...) → hang up, outcome `VOICEMAIL`.
8. **Handoff**: `[HANDOFF]` in the LLM output or explicit phrases ("talk to a person", "human", "receptionist") → say a short transfer line, then `CallControl.transferTo(handoffNumber)`, outcome `HANDED_OFF`.
9. **Latency metrics per turn** (milliseconds, measured with an injectable `Clock`): end of speech → final transcript (`sttMs`), → first LLM token (`llmFirstTokenMs`), → first TTS audio (`ttsFirstAudioMs`), → first audio frame sent to Twilio (`turnLatencyMs`, the number that matters).

Use virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for per-call work. Keep all session state behind a single-threaded executor or explicit locking. No data races.

### `calls` (persistence + API)
- JPA entities `CallRecord` (id, callSid, fromNumber, toNumber, startedAt, endedAt, outcome `COMPLETED|HANDED_OFF|VOICEMAIL|ABANDONED|FAILED`, turnCount, bargeInCount) and `TurnRecord` (call, index, role `CALLER|AGENT`, text, startedAt, the latency fields above, interrupted).
- H2 file DB by default; `postgres` profile for PostgreSQL.
- REST (all JSON, CORS allowed for `http://localhost:3000`):
  - `GET /api/calls?page=&size=`: paged summaries, newest first.
  - `GET /api/calls/{id}`: call and its turns.
  - `GET /api/metrics`: total calls, outcome breakdown, handoff rate, barge-in count, and p50/p95/avg `turnLatencyMs`.
  - `GET /api/calls/live` (SSE): pushes `call-started`, `turn-added`, `call-ended` events for the dashboard.
  - `POST /api/simulate` (only in the `sim` profile or when providers are mock): starts a simulated call from a named scenario and returns its call id.

### `simulator`
- `SimulatedCaller`: a WebSocket client that behaves exactly like Twilio Media Streams against `/twilio/media`. It sends `connected` and `start`, streams 20 ms μ-law frames (tone for "speech", silence between utterances), pushes each utterance's text into the mock STT script, records the agent's audio and `mark`s, and supports a barge-in mode (starts talking while the agent is speaking).
- Scenarios in `src/main/resources/scenarios/*.yaml`: `book-appointment`, `ask-hours`, `interrupt-agent`, `ask-for-human`, `silent-caller`, `voicemail`.

## Tests (JUnit 5, AssertJ, Spring Boot Test)
- `MuLawTest`: known values, round trip within tolerance.
- `EnergyVadTest`: onset/offset timing, short noise ignored.
- `KnowledgeRetrieverTest`: correct FAQ ranked first.
- `SentenceChunkerTest`.
- `TwilioSignatureValidatorTest` using Twilio's documented example.
- `TwimlControllerTest`: stream URL and parameters.
- `CallSessionTest` with mocks and a fake clock: greeting; normal turn persisted with latency fields; barge-in sends `clear` and marks the turn interrupted; stale-turn output dropped; eager reply reused and discarded; silence reprompt and hang up; voicemail; handoff.
- `SimulatedCallIT`: boots the app on a random port and runs every scenario end to end through the real WebSocket handler, asserting outcomes and stored turns.
- `LatencyBenchmark` (tag `benchmark`, excluded by default): runs N simulated calls with mock providers configured with realistic delays and prints p50/p95 per latency field. **Label its output clearly as mock-provider pipeline overhead, not real-world latency.**

## Deliverables
- `backend/`: Maven project (`pom.xml`, sources, tests, `application.yml`, `application-postgres.yml`, `application-sim.yml`).
- `backend/Dockerfile` (multi-stage) and root `docker-compose.yml` (backend + postgres).
- `.env.example` at the root.
- `.github/workflows/backend.yml`: `mvn -B verify` on JDK 21.
- `backend/README.md`: architecture diagram (Mermaid), how to run in mock mode, how to run the simulator, how to plug in real Twilio/Cartesia/OpenRouter keys with ngrok, and the API reference.
- `.gitignore` covering Maven, IDE files, `.env`, and H2 data files.

When finished, write `docs/BACKEND_STATUS.md` listing what you built, anything you were unsure about (especially external API details you could not verify), and anything left undone.
