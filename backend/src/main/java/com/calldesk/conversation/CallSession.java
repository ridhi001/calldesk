package com.calldesk.conversation;

import com.calldesk.audio.EnergyVad;
import com.calldesk.audio.MuLaw;
import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallPersistenceService;
import com.calldesk.calls.TurnRole;
import com.calldesk.calls.TurnSource;
import com.calldesk.config.CallDeskProperties;
import com.calldesk.knowledge.BusinessProfileLoader;
import com.calldesk.knowledge.PromptBuilder;
import com.calldesk.knowledge.KnowledgeQuestionService;
import com.calldesk.llm.ChatMessage;
import com.calldesk.llm.LanguageModel;
import com.calldesk.stt.SpeechToText;
import com.calldesk.telephony.CallControl;
import com.calldesk.telephony.TwilioMediaSender;
import com.calldesk.tts.TextToSpeech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns one call; state mutations are serialized on stateExecutor and provider work runs on virtual threads. */
public final class CallSession implements AutoCloseable {
    private static final String[] VOICEMAIL_PHRASES = {"leave a message", "after the tone", "at the tone", "please leave your message", "mailbox is full", "record your message"};
    private static final String[] HUMAN_PHRASES = {"talk to a person", "speak to a person", "talk to someone", "speak to someone", "human", "receptionist"};

    private final String callSid;
    private final String from;
    private final String to;
    private final CallDeskProperties properties;
    private final CallPersistenceService persistence;
    private final SpeechToText speechToText;
    private final LanguageModel languageModel;
    private final TextToSpeech textToSpeech;
    private final PromptBuilder promptBuilder;
    private final KnowledgeQuestionService knowledgeQuestions;
    private final BusinessProfileLoader profileLoader;
    private final CallControl callControl;
    private final TwilioMediaSender sender;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService stateExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("calldesk-state-", 0).factory());
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final EnergyVad vad;
    private final List<ChatMessage> history = new ArrayList<>();
    private final Map<String, PendingMark> pendingMarks = new ConcurrentHashMap<>();
    private final AtomicInteger outputSequence = new AtomicInteger();
    private final SpeechToText.SttStream sttStream;
    private volatile ActiveTurn activeTurn;
    private volatile boolean ended;
    private volatile boolean speaking;
    // Set before asking Twilio to transfer. The transfer itself makes Twilio end the media stream, so a `stop`
    // event can race the HANDED_OFF bookkeeping; this flag keeps that call recorded as a handoff.
    private volatile boolean transferRequested;
    private Instant lastCallerActivity;
    private Instant speechEndedAt;
    private int turnSequence;
    private long partialRevision;
    private String latestPartial = "";
    private ActiveTurn speculativeTurn;
    private boolean reprompted;
    private boolean hangupStarted;
    private boolean voicemailChecked;
    private volatile ScheduledFuture<?> watchdog;

    public CallSession(String callSid, String from, String to, CallDeskProperties properties,
                       CallPersistenceService persistence, SpeechToText speechToText, LanguageModel languageModel,
                       TextToSpeech textToSpeech, PromptBuilder promptBuilder, KnowledgeQuestionService knowledgeQuestions,
                       BusinessProfileLoader profileLoader,
                       CallControl callControl, TwilioMediaSender sender, Clock clock, ScheduledExecutorService scheduler) {
        this.callSid = callSid;
        this.from = from;
        this.to = to;
        this.properties = properties;
        this.persistence = persistence;
        this.speechToText = speechToText;
        this.languageModel = languageModel;
        this.textToSpeech = textToSpeech;
        this.promptBuilder = promptBuilder;
        this.knowledgeQuestions = knowledgeQuestions;
        this.profileLoader = profileLoader;
        this.callControl = callControl;
        this.sender = sender;
        this.clock = clock;
        this.scheduler = scheduler;
        this.vad = new EnergyVad(properties.getVad().getEnergyThreshold(), properties.getVad().getSpeechStartMs(), properties.getVad().getSpeechEndMs());
        this.sttStream = speechToText.openStream(callSid, new SpeechToText.Listener() {
            @Override public void onPartial(String text) { submitState(() -> handlePartial(text)); }
            @Override public void onFinal(String text) { submitState(() -> handleFinal(text)); }
            @Override public void onError(Throwable error) { submitState(() -> fail(error)); }
        });
    }

    public void start() {
        submitState(() -> {
            if (ended) return;
            lastCallerActivity = clock.instant();
            String greeting = profileLoader.getProfile().greeting();
            sayAgentLine(0, greeting, CallOutcome.COMPLETED, false);
        });
        watchdog = scheduler.scheduleAtFixedRate(() -> submitState(this::checkSilenceWatchdog), 1, 1, TimeUnit.SECONDS);
    }

    public void onAudio(byte[] mulaw) {
        byte[] frame = mulaw.clone();
        submitState(() -> processAudio(frame));
    }

    public void onMark(String name) {
        submitState(() -> {
            PendingMark pending = pendingMarks.remove(name);
            if (pending == null) return;
            if (!pending.turn.interrupted) synchronized (pending.turn.spokenText) { pending.turn.spokenText.append(pending.sentence).append(' '); }
            pending.turn.unplayedSentences.remove(pending.sentence);
            if (name.equals(pending.turn.lastMark)) pending.turn.lastMarkAcked = true;
            pending.acknowledged.complete(null);
            if (pending.finalMark && activeTurn == pending.turn) {
                speaking = false;
                activeTurn = null;
            }
        });
    }

    public void onStreamStopped() { submitState(() -> finishCall(transferRequested ? CallOutcome.HANDED_OFF : CallOutcome.COMPLETED)); }

    public void checkSilenceNow() { submitState(this::checkSilenceWatchdog); }

    private void processAudio(byte[] frame) {
        if (ended) return;
        boolean wasSpeaking = vad.isSpeaking();
        EnergyVad.Event event = vad.accept(MuLaw.decode(frame));
        if (event == EnergyVad.Event.SPEECH_STARTED) handleSpeechStart();
        if (wasSpeaking || vad.isSpeaking() || event == EnergyVad.Event.SPEECH_ENDED) {
            sttStream.sendAudio(frame);
            lastCallerActivity = clock.instant();
        }
        if (event == EnergyVad.Event.SPEECH_ENDED) {
            speechEndedAt = clock.instant();
            sttStream.endUtterance();
        }
    }

    private void handleSpeechStart() {
        lastCallerActivity = clock.instant();
        reprompted = false;
        speechEndedAt = null;
        latestPartial = "";
        if (!speaking) {
            // The caller kept talking before the agent said anything. Drop the pending reply so the agent never
            // talks over them; the next final transcript is answered with both caller messages in the history.
            if (activeTurn != null) cancelActiveTurn();
            if (speculativeTurn != null) speculativeTurn.cancellationToken.cancel();
            speculativeTurn = null;
            return;
        }
        if (activeTurn == null) return;
        ActiveTurn interrupted = activeTurn;
        interrupted.interrupted = true;
        interrupted.cancellationToken.cancel();
        sender.clear();
        speaking = false;
        persistence.incrementBargeIn(callSid);
        String spoken = spokenSoFar(interrupted);
        persistence.markAgentTurnInterrupted(callSid, interrupted.turnId, spoken,
                interrupted.speechEndedAt == null ? clock.instant() : interrupted.speechEndedAt, interrupted.sttMs,
                interrupted.llmFirstTokenMs(), interrupted.ttsFirstAudioMs(), interrupted.turnLatencyMs, interrupted.sources);
        pendingMarks.entrySet().removeIf(entry -> {
            if (entry.getValue().turn != interrupted) return false;
            entry.getValue().acknowledged.complete(null);
            return true;
        });
        activeTurn = null;
    }

    private void handlePartial(String text) {
        if (ended || text == null || text.isBlank()) return;
        latestPartial = text;
        long revision = ++partialRevision;
        if (!properties.getEagerReply().isEnabled()) return;
        scheduleEagerReply(text, revision);
    }

    private void scheduleEagerReply(String text, long revision) {
        scheduler.schedule(() -> submitState(() -> {
            if (ended || revision != partialRevision || !sameText(latestPartial, text)) return;
            if (vad.isSpeaking()) {
                scheduleEagerReply(text, revision);
                return;
            }
            if (speculativeTurn != null && !speculativeTurn.cancellationToken.isCancelled()) return;
            speculativeTurn = beginGeneration(text, turnSequence + 1, true, speechEndedAt);
        }), properties.getEagerReply().getStablePartialMs(), TimeUnit.MILLISECONDS);
    }

    private void handleFinal(String text) {
        if (ended || text == null || text.isBlank()) return;
        partialRevision++;
        Instant finalAt = clock.instant();
        Instant endedAt = speechEndedAt == null ? finalAt : speechEndedAt;
        long sttMs = Math.max(0, Duration.between(endedAt, finalAt).toMillis());
        boolean firstFinal = !voicemailChecked;
        voicemailChecked = true;
        if (firstFinal && containsAny(text.toLowerCase(Locale.ROOT), VOICEMAIL_PHRASES)) {
            history.add(new ChatMessage("user", text));
            turnSequence++;
            persistence.addTurn(callSid, turnSequence, TurnRole.CALLER, text, endedAt, sttMs, null, null, null, false);
            callControl.hangup(callSid);
            finishCall(CallOutcome.VOICEMAIL);
            return;
        }

        turnSequence++;
        persistence.addTurn(callSid, turnSequence, TurnRole.CALLER, text, endedAt, sttMs, null, null, null, false);
        history.add(new ChatMessage("user", text));
        if (containsAny(text.toLowerCase(Locale.ROOT), HUMAN_PHRASES)) {
            cancelActiveTurn();
            sayAgentLine(turnSequence, "I’ll connect you with our receptionist now.", CallOutcome.HANDED_OFF, true, endedAt, sttMs);
            return;
        }

        List<TurnSource> sources = knowledgeQuestions.prepareAnswer(text, callSid).sources();

        if (speculativeTurn != null && sameText(speculativeTurn.userText, text) && !speculativeTurn.cancellationToken.isCancelled()) {
            ActiveTurn reused = speculativeTurn;
            speculativeTurn = null;
            reused.turnId = turnSequence;
            reused.userText = text;
            reused.speechEndedAt = endedAt;
            reused.sttMs = sttMs;
            reused.sources = sources;
            activeTurn = reused;
            workers.submit(() -> flushSpeculativeOutput(reused));
            return;
        }
        cancelActiveTurn();
        if (speculativeTurn != null) speculativeTurn.cancellationToken.cancel();
        speculativeTurn = null;
        activeTurn = beginGeneration(text, turnSequence, false, endedAt, sources);
        activeTurn.sttMs = sttMs;
    }

    private ActiveTurn beginGeneration(String userText, int turnId, boolean speculative, Instant endedAt) {
        return beginGeneration(userText, turnId, speculative, endedAt, List.of());
    }

    private ActiveTurn beginGeneration(String userText, int turnId, boolean speculative, Instant endedAt, List<TurnSource> sources) {
        ActiveTurn context = new ActiveTurn(turnId, userText, endedAt, speculative);
        context.sources = List.copyOf(sources);
        ActiveTurn previous = activeTurn;
        if (previous != null) previous.cancellationToken.cancel();
        activeTurn = context;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", promptBuilder.build(userText)));
        messages.addAll(history);
        if (speculative) messages.add(new ChatMessage("user", userText));
        workers.submit(() -> runGeneration(context, List.copyOf(messages)));
        return context;
    }

    private void runGeneration(ActiveTurn turn, List<ChatMessage> messages) {
        StringBuilder rawOutput = new StringBuilder();
        SentenceChunker chunker = new SentenceChunker(sentence -> sendSentence(turn, sentence));
        HandoffFilter filter = new HandoffFilter(chunker);
        try {
            languageModel.stream(messages, token -> {
                if (!isCurrent(turn)) throw new CancellationException("Stale turn");
                if (turn.firstLlmTokenAt == null) turn.firstLlmTokenAt = clock.instant();
                rawOutput.append(token);
                filter.accept(token);
            }, turn.cancellationToken);
            filter.finish();
            chunker.finish();
            if (!isCurrent(turn)) return;
            String answer = rawOutput.toString().replace("[HANDOFF]", "").trim();
            boolean handoff = filter.handoffDetected;
            if (answer.isBlank() && handoff) answer = "I’ll connect you with our receptionist now.";
            final String finalAnswer = answer;
            final boolean shouldHandoff = handoff;
            submitState(() -> {
                turn.generationComplete = true;
                turn.generatedAnswer = finalAnswer;
                turn.handoffDetected = shouldHandoff;
                if (!turn.speculative) finishGeneration(turn, finalAnswer, shouldHandoff);
            });
        } catch (CancellationException ignored) {
            // A newer caller turn owns the session output now.
        } catch (Throwable error) {
            submitState(() -> { if (isCurrent(turn)) fail(error); });
        }
    }

    private void sendSentence(ActiveTurn turn, String sentence) {
        if (sentence.isBlank()) return;
        synchronized (turn.outputLock) {
            turn.cancellationToken.throwIfCancelled();
            if (!isCurrent(turn)) return;
            if (turn.speculative) {
                turn.bufferedSentences.add(sentence);
                return;
            }
            speakSentence(turn, sentence);
        }
    }

    private void flushSpeculativeOutput(ActiveTurn turn) {
        try {
            synchronized (turn.outputLock) {
                if (!isCurrent(turn)) return;
                turn.speculative = false;
                List<String> buffered = List.copyOf(turn.bufferedSentences);
                turn.bufferedSentences.clear();
                for (String sentence : buffered) speakSentence(turn, sentence);
            }
            submitState(() -> {
                if (turn.generationComplete && isCurrent(turn)) finishGeneration(turn, turn.generatedAnswer, turn.handoffDetected);
            });
        } catch (CancellationException ignored) {
            // The caller changed turns while buffered speech was being released.
        } catch (Throwable error) {
            submitState(() -> { if (isCurrent(turn)) fail(error); });
        }
    }

    private void speakSentence(ActiveTurn turn, String sentence) {
        turn.cancellationToken.throwIfCancelled();
        AtomicInteger audioFrames = new AtomicInteger();
        textToSpeech.stream(sentence, audio -> {
            turn.cancellationToken.throwIfCancelled();
            if (!isCurrent(turn)) return;
            if (turn.firstTtsAudioAt == null) turn.firstTtsAudioAt = clock.instant();
            if (turn.firstAudioSentAt == null) {
                turn.firstAudioSentAt = clock.instant();
                turn.turnLatencyMs = turn.speechEndedAt == null ? null : elapsedFromSpeechEnd(turn, turn.firstAudioSentAt);
            }
            if (audioFrames.get() == 0) turn.unplayedSentences.addLast(sentence);
            sender.sendMedia(audio);
            speaking = true;
            audioFrames.incrementAndGet();
        }, turn.cancellationToken);
        if (audioFrames.get() == 0 || !isCurrent(turn)) return;
        boolean finalMark = false;
        String mark = "turn-" + turn.turnId + "-" + outputSequence.incrementAndGet();
        PendingMark pending = new PendingMark(turn, sentence, new CompletableFuture<>(), finalMark);
        pendingMarks.put(mark, pending);
        turn.lastMark = mark;
        sender.sendMark(mark);
    }

    private void finishGeneration(ActiveTurn turn, String answer, boolean handoff) {
        if (!isCurrent(turn) || turn.finalized) return;
        turn.finalized = true;
        if (!turn.speculative && !turn.persisted && !answer.isBlank()) {
            history.add(new ChatMessage("assistant", answer));
            persistence.addAgentTurn(callSid, turn.turnId, answer, turn.speechEndedAt == null ? clock.instant() : turn.speechEndedAt,
                    turn.sttMs, turn.llmFirstTokenMs(), turn.ttsFirstAudioMs(), turn.turnLatencyMs, false, turn.sources);
            turn.persisted = true;
        }
        if (turn.lastMark != null) {
            PendingMark old = pendingMarks.get(turn.lastMark);
            if (old != null) {
                pendingMarks.put(turn.lastMark, new PendingMark(turn, old.sentence, old.acknowledged, true));
            } else if (turn.lastMarkAcked) {
                speaking = false;
                activeTurn = null;
            }
        } else {
            speaking = false;
            activeTurn = null;
        }
        if (!turn.speculative) speculativeTurn = null;
        if (handoff) {
            workers.submit(() -> {
                awaitLastMark(turn);
                if (!ended && !turn.cancellationToken.isCancelled()) {
                    transferRequested = true;
                    callControl.transferTo(callSid, profileLoader.getProfile().handoffNumber());
                    submitState(() -> finishCall(CallOutcome.HANDED_OFF));
                }
            });
        }
    }

    private void sayAgentLine(int turnId, String text, CallOutcome outcomeAfter, boolean transfer) {
        sayAgentLine(turnId, text, outcomeAfter, transfer, null, null);
    }

    private void sayAgentLine(int turnId, String text, CallOutcome outcomeAfter, boolean transfer, Instant speechEndedAt, Long sttMs) {
        ActiveTurn turn = new ActiveTurn(turnId, "", speechEndedAt, false);
        turn.sttMs = sttMs;
        activeTurn = turn;
        workers.submit(() -> {
            try {
                sendSentence(turn, text);
                if (turn.lastMark != null) awaitLastMark(turn);
                submitState(() -> {
                    if (ended || turn.cancellationToken.isCancelled()) return;
                    persistence.addTurn(callSid, turnId, TurnRole.AGENT, text, clock.instant(), turn.sttMs,
                            turn.llmFirstTokenMs(), turn.ttsFirstAudioMs(), turn.turnLatencyMs, false);
                    history.add(new ChatMessage("assistant", text));
                    speaking = false;
                    activeTurn = null;
                    if (transfer) {
                        transferRequested = true;
                        callControl.transferTo(callSid, profileLoader.getProfile().handoffNumber());
                        finishCall(outcomeAfter);
                    } else if (outcomeAfter != CallOutcome.COMPLETED) {
                        callControl.hangup(callSid);
                        finishCall(outcomeAfter);
                    }
                });
            } catch (CancellationException ignored) { }
            catch (Throwable error) { submitState(() -> fail(error)); }
        });
    }

    private void awaitLastMark(ActiveTurn turn) {
        String name = turn.lastMark;
        if (name == null) return;
        PendingMark pending = pendingMarks.get(name);
        if (pending == null) return;
        try { pending.acknowledged.get(15, TimeUnit.SECONDS); }
        catch (Exception ignored) { }
    }

    private void checkSilenceWatchdog() {
        if (ended || lastCallerActivity == null || vad.isSpeaking() || speaking || activeTurn != null || hangupStarted) return;
        long silentMs = Duration.between(lastCallerActivity, clock.instant()).toMillis();
        if (silentMs >= properties.getSilence().getHangupAfterMs()) {
            hangupStarted = true;
            sayAgentLine(++turnSequence, "Thanks for calling. Goodbye.", CallOutcome.ABANDONED, false);
        } else if (silentMs >= properties.getSilence().getRepromptAfterMs() && !reprompted) {
            reprompted = true;
            sayAgentLine(++turnSequence, "Are you still there?", CallOutcome.COMPLETED, false);
        }
    }

    private void cancelActiveTurn() {
        ActiveTurn current = activeTurn;
        if (current != null) current.cancellationToken.cancel();
        speaking = false;
        activeTurn = null;
    }

    private void fail(Throwable error) {
        if (ended) return;
        cancelActiveTurn();
        finishCall(CallOutcome.FAILED);
    }

    private void finishCall(CallOutcome outcome) {
        if (ended) return;
        ended = true;
        if (watchdog != null) watchdog.cancel(false);
        ActiveTurn current = activeTurn;
        if (current != null) {
            current.cancellationToken.cancel();
            persistCutOffReply(current);
        }
        try { sttStream.close(); } catch (Exception ignored) { }
        persistence.endCall(callSid, outcome, clock.instant());
        sender.close();
        workers.shutdownNow();
        stateExecutor.shutdown();
    }

    // The call ended while the agent was mid-reply (for example the caller hung up). Keep what the caller actually
    // heard in the transcript, flagged as interrupted, instead of silently dropping the turn.
    private void persistCutOffReply(ActiveTurn turn) {
        if (turn.speculative || turn.persisted) return;
        String heard = spokenSoFar(turn);
        if (heard.isEmpty()) return;
        persistence.markAgentTurnInterrupted(callSid, turn.turnId, heard,
                turn.speechEndedAt == null ? clock.instant() : turn.speechEndedAt, turn.sttMs,
                turn.llmFirstTokenMs(), turn.ttsFirstAudioMs(), turn.turnLatencyMs, turn.sources);
        turn.persisted = true;
    }

    private static String spokenSoFar(ActiveTurn turn) {
        synchronized (turn.spokenText) {
            String playing = turn.unplayedSentences.peekFirst();
            return (turn.spokenText + (playing == null ? "" : playing)).trim();
        }
    }

    private boolean isCurrent(ActiveTurn turn) { return !ended && activeTurn == turn && !turn.cancellationToken.isCancelled(); }
    private long elapsedFromSpeechEnd(ActiveTurn turn, Instant at) { return turn.speechEndedAt == null ? 0 : Math.max(0, Duration.between(turn.speechEndedAt, at).toMillis()); }
    private static boolean sameText(String first, String second) { return normalize(first).equals(normalize(second)); }
    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim().replaceAll("\\s+", " ");
    }
    private static boolean containsAny(String text, String[] phrases) { for (String phrase : phrases) if (text.contains(phrase)) return true; return false; }
    private void submitState(Runnable action) { if (ended) return; try { stateExecutor.execute(action); } catch (java.util.concurrent.RejectedExecutionException ignored) { } }
    @Override public void close() { submitState(() -> finishCall(CallOutcome.COMPLETED)); }

    private static final class ActiveTurn {
        private volatile int turnId;
        private volatile String userText;
        private volatile Instant speechEndedAt;
        private volatile boolean speculative;
        private final CancellationToken cancellationToken = new CancellationToken();
        private final StringBuilder spokenText = new StringBuilder();
        private final Object outputLock = new Object();
        private final List<String> bufferedSentences = new ArrayList<>();
        private volatile Instant firstLlmTokenAt;
        private volatile Instant firstTtsAudioAt;
        private volatile Instant firstAudioSentAt;
        private volatile Long sttMs;
        private volatile Long turnLatencyMs;
        private volatile String lastMark;
        // Sentences sent to Twilio whose playback mark has not come back yet, oldest first. Twilio plays audio in
        // order, so the head of this queue is the sentence the caller is hearing right now.
        private final Deque<String> unplayedSentences = new ConcurrentLinkedDeque<>();
        private volatile boolean interrupted;
        private volatile boolean persisted;
        private volatile List<TurnSource> sources = List.of();
        private volatile boolean lastMarkAcked;
        private volatile boolean generationComplete;
        private volatile String generatedAnswer = "";
        private volatile boolean handoffDetected;
        private volatile boolean finalized;
        private ActiveTurn(int turnId, String userText, Instant speechEndedAt, boolean speculative) {
            this.turnId = turnId; this.userText = userText; this.speechEndedAt = speechEndedAt; this.speculative = speculative;
        }
        private Long llmFirstTokenMs() { return firstLlmTokenAt == null || speechEndedAt == null ? null : Math.max(0, Duration.between(speechEndedAt, firstLlmTokenAt).toMillis()); }
        private Long ttsFirstAudioMs() { return firstTtsAudioAt == null || speechEndedAt == null ? null : Math.max(0, Duration.between(speechEndedAt, firstTtsAudioAt).toMillis()); }
    }

    private record PendingMark(ActiveTurn turn, String sentence, CompletableFuture<Void> acknowledged, boolean finalMark) { }

    private static final class HandoffFilter {
        private static final String MARKER = "[HANDOFF]";
        private final StringBuilder pending = new StringBuilder();
        private final SentenceChunker chunker;
        private boolean handoffDetected;
        private HandoffFilter(SentenceChunker chunker) { this.chunker = chunker; }
        private void accept(String token) {
            pending.append(token);
            int markerIndex;
            while ((markerIndex = pending.indexOf(MARKER)) >= 0) {
                if (markerIndex > 0) chunker.accept(pending.substring(0, markerIndex));
                pending.delete(0, markerIndex + MARKER.length());
                handoffDetected = true;
            }
            // Only hold back a tail that could still grow into the marker (e.g. "[", "[HAND"). Holding back a fixed
            // number of characters would delay the end of every sentence until more tokens arrive.
            int safeLength = pending.length() - partialMarkerSuffixLength();
            if (safeLength > 0) {
                chunker.accept(pending.substring(0, safeLength));
                pending.delete(0, safeLength);
            }
        }

        private int partialMarkerSuffixLength() {
            for (int length = Math.min(MARKER.length() - 1, pending.length()); length > 0; length--) {
                if (pending.substring(pending.length() - length).equals(MARKER.substring(0, length))) return length;
            }
            return 0;
        }
        private void finish() {
            if (pending.indexOf(MARKER) >= 0) {
                handoffDetected = true;
                pending.replace(pending.indexOf(MARKER), pending.indexOf(MARKER) + MARKER.length(), "");
            }
            if (pending.length() > 0) chunker.accept(pending.toString());
            pending.setLength(0);
        }
    }
}
