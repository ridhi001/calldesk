package com.calldesk.conversation;

import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallPersistenceService;
import com.calldesk.calls.TurnRole;
import com.calldesk.calls.TurnSource;
import com.calldesk.config.CallDeskProperties;
import com.calldesk.knowledge.BusinessProfile;
import com.calldesk.knowledge.BusinessProfileLoader;
import com.calldesk.knowledge.PromptBuilder;
import com.calldesk.knowledge.KnowledgeQuestionContext;
import com.calldesk.knowledge.KnowledgeQuestionService;
import com.calldesk.llm.LanguageModel;
import com.calldesk.stt.SpeechToText;
import com.calldesk.telephony.CallControl;
import com.calldesk.telephony.TwilioMediaSender;
import com.calldesk.tts.TextToSpeech;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallSessionTest {
    @Test void greetsAndPersistsCallerAndAgentTurnMetrics() {
        Fixture fixture = new Fixture((messages, listener, token) -> listener.onToken("Our hours are weekdays."));
        try {
            fixture.startAndWaitForGreeting();
            fixture.stt.listener.onFinal("What are your hours?");
            verify(fixture.persistence, timeout(3000)).addTurn(eq("CA_TEST"), eq(1), eq(TurnRole.CALLER),
                    eq("What are your hours?"), any(Instant.class), eq(0L), any(), any(), any(), eq(false));
            verify(fixture.persistence, timeout(3000)).addAgentTurn(eq("CA_TEST"), eq(1),
                    eq("Our hours are weekdays."), any(Instant.class), any(), any(), any(), any(), eq(false),
                    argThat(sources -> sources.size() == 1 && sources.getFirst().getEntryId().equals(41L)
                            && sources.getFirst().getQuestion().equals("Test question?") && sources.getFirst().getScore().equals(2.25)));
            assertThat(fixture.sender.audioSent).isGreaterThan(0);
        } finally { fixture.close(); }
    }

    @Test void sendsNoMatchQuestionToGapRecorderAndPersistsAnEmptySourceList() {
        Fixture fixture = new Fixture((messages, listener, token) -> listener.onToken("I'm not sure about that one."));
        when(fixture.knowledgeQuestions.prepareAnswer("Do you offer adult braces?", "CA_TEST"))
                .thenReturn(new KnowledgeQuestionContext(List.of()));
        try {
            fixture.startAndWaitForGreeting();
            fixture.stt.listener.onFinal("Do you offer adult braces?");
            verify(fixture.knowledgeQuestions, timeout(3000)).prepareAnswer("Do you offer adult braces?", "CA_TEST");
            verify(fixture.persistence, timeout(3000)).addAgentTurn(eq("CA_TEST"), eq(1),
                    eq("I'm not sure about that one."), any(Instant.class), any(), any(), any(), any(), eq(false),
                    argThat(List::isEmpty));
        } finally { fixture.close(); }
    }

    @Test void bargeInClearsAudioAndDropsStaleTurnOutput() throws Exception {
        CountDownLatch modelPaused = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        LanguageModel model = (messages, listener, token) -> {
            listener.onToken("The answer is ready. This part has several extra words. ");
            modelPaused.countDown();
            try { releaseModel.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            token.throwIfCancelled();
            listener.onToken(" stale output.");
        };
        Fixture fixture = new Fixture(model);
        try {
            fixture.startAndWaitForGreeting();
            fixture.sender.firstAudio = new CountDownLatch(1);
            fixture.sender.autoAckMarks = false;
            fixture.stt.listener.onFinal("Where are you located?");
            assertThat(modelPaused.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.sender.firstAudio.await(3, TimeUnit.SECONDS)).isTrue();
            int framesAtInterrupt = fixture.sender.audioSent;
            short[] speech = new short[160];
            java.util.Arrays.fill(speech, (short) 5000);
            byte[] frame = com.calldesk.audio.MuLaw.encode(speech);
            fixture.session.onAudio(frame);
            fixture.session.onAudio(frame);
            fixture.session.onAudio(frame);
            verify(fixture.persistence, timeout(3000)).incrementBargeIn("CA_TEST");
            long clearDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (fixture.sender.clears == 0 && System.nanoTime() < clearDeadline) Thread.sleep(10);
            assertThat(fixture.sender.clears).isGreaterThan(0);
            // Both sentences were already sent, but neither playback mark came back: the caller was hearing the first one.
            verify(fixture.persistence, timeout(3000)).markAgentTurnInterrupted(eq("CA_TEST"), eq(1), eq("The answer is ready."), any(Instant.class), any(), any(), any(), any(),
                    argThat(sources -> sources.size() == 1 && sources.getFirst().getEntryId().equals(41L)));
            releaseModel.countDown();
            Thread.sleep(100);
            assertThat(fixture.sender.audioSent).isEqualTo(framesAtInterrupt);
        } finally {
            releaseModel.countDown();
            fixture.close();
        }
    }

    @Test void callerResumingBeforeAgentSpeaksCancelsThePendingReply() throws Exception {
        CountDownLatch modelStarted = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        LanguageModel model = (messages, listener, token) -> {
            modelStarted.countDown();
            try { releaseModel.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            token.throwIfCancelled();
            listener.onToken("This reply would talk over the caller.");
        };
        Fixture fixture = new Fixture(model);
        try {
            fixture.startAndWaitForGreeting();
            int framesAfterGreeting = fixture.sender.audioSent;
            fixture.stt.listener.onFinal("I want to book a cleaning");
            assertThat(modelStarted.await(3, TimeUnit.SECONDS)).isTrue();

            short[] speech = new short[160];
            java.util.Arrays.fill(speech, (short) 5000);
            byte[] frame = com.calldesk.audio.MuLaw.encode(speech);
            for (int i = 0; i < 4; i++) fixture.session.onAudio(frame);
            Thread.sleep(100);
            releaseModel.countDown();
            Thread.sleep(200);

            assertThat(fixture.sender.audioSent).as("agent must not speak over the caller").isEqualTo(framesAfterGreeting);
            verify(fixture.persistence, org.mockito.Mockito.never()).incrementBargeIn(anyString());
            verify(fixture.persistence, org.mockito.Mockito.never()).addAgentTurn(eq("CA_TEST"), eq(1),
                    anyString(), any(Instant.class), any(), any(), any(), any(), eq(false), anyList());
        } finally {
            releaseModel.countDown();
            fixture.close();
        }
    }

    @Test void callerHangingUpMidReplyKeepsWhatTheyHeard() throws Exception {
        CountDownLatch releaseModel = new CountDownLatch(1);
        LanguageModel model = (messages, listener, token) -> {
            listener.onToken("We open at nine. ");
            try { releaseModel.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            token.throwIfCancelled();
            listener.onToken("We close at six.");
        };
        Fixture fixture = new Fixture(model);
        try {
            fixture.startAndWaitForGreeting();
            fixture.sender.firstAudio = new CountDownLatch(1);
            fixture.sender.autoAckMarks = false;
            fixture.stt.listener.onFinal("When are you open?");
            assertThat(fixture.sender.firstAudio.await(3, TimeUnit.SECONDS)).isTrue();

            fixture.session.onStreamStopped();

            verify(fixture.persistence, timeout(3000)).markAgentTurnInterrupted(eq("CA_TEST"), eq(1), eq("We open at nine."),
                    any(Instant.class), any(), any(), any(), any(),
                    argThat(sources -> sources.size() == 1 && sources.getFirst().getEntryId().equals(41L)));
            verify(fixture.persistence, timeout(3000)).endCall(eq("CA_TEST"), eq(CallOutcome.COMPLETED), any(Instant.class));
        } finally {
            releaseModel.countDown();
            fixture.close();
        }
    }

    @Test void handoffMarkerSplitAcrossTokensTransfersWithoutBeingSpoken() {
        Fixture fixture = new Fixture((messages, listener, token) -> {
            listener.onToken("Let me get someone for you. [HAND");
            listener.onToken("OFF]");
        });
        try {
            fixture.startAndWaitForGreeting();
            fixture.stt.listener.onFinal("My bill looks wrong");
            verify(fixture.callControl, timeout(3000)).transferTo("CA_TEST", "+12075550142");
            assertThat(fixture.texts).contains("Let me get someone for you.");
            assertThat(fixture.texts).noneMatch(text -> text.contains("[") || text.contains("HAND"));
        } finally { fixture.close(); }
    }

    @Test void streamStoppingDuringTransferIsStillRecordedAsHandoff() {
        Fixture fixture = new Fixture((messages, listener, token) -> listener.onToken("One moment please. [HANDOFF]"));
        // Twilio ends the media stream because of the transfer, so `stop` can arrive while transferTo is still running.
        org.mockito.Mockito.doAnswer(invocation -> { fixture.session.onStreamStopped(); return null; })
                .when(fixture.callControl).transferTo(anyString(), anyString());
        try {
            fixture.startAndWaitForGreeting();
            fixture.stt.listener.onFinal("There is a problem with my bill");
            verify(fixture.persistence, timeout(3000)).endCall(eq("CA_TEST"), eq(CallOutcome.HANDED_OFF), any(Instant.class));
            verify(fixture.persistence, org.mockito.Mockito.never()).endCall(eq("CA_TEST"), eq(CallOutcome.COMPLETED), any(Instant.class));
        } finally { fixture.close(); }
    }

    @Test void silenceRepromptsAndEventuallyEndsAsAbandoned() throws Exception {
        Fixture fixture = new Fixture((messages, listener, token) -> listener.onToken("A regular answer."));
        try {
            fixture.startAndWaitForGreeting();
            fixture.clock.advanceMillis(9000);
            fixture.session.checkSilenceNow();
            long promptDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!fixture.texts.contains("Are you still there?") && System.nanoTime() < promptDeadline) Thread.sleep(10);
            assertThat(fixture.texts).contains("Are you still there?");
            verify(fixture.persistence, timeout(3000)).addTurn(eq("CA_TEST"), eq(1), eq(TurnRole.AGENT),
                    eq("Are you still there?"), any(Instant.class), any(), any(), any(), any(), eq(false));
            fixture.clock.advanceMillis(12000);
            fixture.session.checkSilenceNow();
            verify(fixture.callControl, timeout(3000)).hangup("CA_TEST");
            verify(fixture.persistence, timeout(3000)).endCall(eq("CA_TEST"), eq(CallOutcome.ABANDONED), any(Instant.class));
        } finally { fixture.close(); }
    }

    @Test void detectsVoicemailAndExplicitHandoff() {
        Fixture voicemail = new Fixture((messages, listener, token) -> listener.onToken("Okay."));
        try {
            voicemail.startAndWaitForGreeting();
            voicemail.stt.listener.onFinal("Please leave a message after the tone.");
            verify(voicemail.callControl, timeout(3000)).hangup("CA_TEST");
            verify(voicemail.persistence, timeout(3000)).endCall(eq("CA_TEST"), eq(CallOutcome.VOICEMAIL), any(Instant.class));
        } finally { voicemail.close(); }

        Fixture handoff = new Fixture((messages, listener, token) -> listener.onToken("I can help."));
        try {
            handoff.startAndWaitForGreeting();
            handoff.stt.listener.onFinal("Can I speak to a person?");
            verify(handoff.callControl, timeout(3000)).transferTo("CA_TEST", "+12075550142");
            verify(handoff.persistence, timeout(3000)).endCall(eq("CA_TEST"), eq(CallOutcome.HANDED_OFF), any(Instant.class));
        } finally { handoff.close(); }
    }

    @Test void eagerReplyIsReusedWhenTextMatchesAndCancelledWhenItChanges() throws Exception {
        AtomicInteger reusedCalls = new AtomicInteger();
        CountDownLatch eagerModelDone = new CountDownLatch(1);
        Fixture reused = new Fixture((messages, listener, token) -> {
            reusedCalls.incrementAndGet();
            listener.onToken("Our hours are weekdays.");
            eagerModelDone.countDown();
        });
        try {
            reused.startAndWaitForGreeting();
            reused.stt.listener.onPartial("What are your hours?");
            reused.runLatestDelayedTask();
            assertThat(eagerModelDone.await(3, TimeUnit.SECONDS)).isTrue();
            reused.stt.listener.onFinal("What are your hours?");
            verify(reused.persistence, timeout(3000)).addAgentTurn(eq("CA_TEST"), eq(1),
                    eq("Our hours are weekdays."), any(Instant.class), any(), any(), any(), any(), eq(false), anyList());
            assertThat(reusedCalls.get()).isEqualTo(1);
        } finally { reused.close(); }

        CountDownLatch modelStarted = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        AtomicReference<CancellationToken> speculativeToken = new AtomicReference<>();
        AtomicInteger discardedCalls = new AtomicInteger();
        Fixture discarded = new Fixture((messages, listener, token) -> {
            if (discardedCalls.incrementAndGet() == 1) {
                speculativeToken.set(token);
                modelStarted.countDown();
                try { releaseModel.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                token.throwIfCancelled();
                listener.onToken("This output must be dropped.");
            } else {
                listener.onToken("Current answer.");
            }
        });
        try {
            discarded.startAndWaitForGreeting();
            discarded.stt.listener.onPartial("What are your hours?");
            discarded.runLatestDelayedTask();
            assertThat(modelStarted.await(3, TimeUnit.SECONDS)).isTrue();
            discarded.stt.listener.onFinal("Where are you located?");
            long cancelDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while ((speculativeToken.get() == null || !speculativeToken.get().isCancelled()) && System.nanoTime() < cancelDeadline) Thread.sleep(10);
            assertThat(speculativeToken.get().isCancelled()).isTrue();
            releaseModel.countDown();
            verify(discarded.persistence, timeout(3000)).addAgentTurn(eq("CA_TEST"), eq(1),
                    eq("Current answer."), any(Instant.class), any(), any(), any(), any(), eq(false), anyList());
            assertThat(discarded.texts).doesNotContain("This output must be dropped.");
        } finally {
            releaseModel.countDown();
            discarded.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final CallDeskProperties properties = new CallDeskProperties();
        private final CallPersistenceService persistence = mock(CallPersistenceService.class);
        private final RecordingStt stt = new RecordingStt();
        private final CallControl callControl = mock(CallControl.class);
        private final KnowledgeQuestionService knowledgeQuestions = mock(KnowledgeQuestionService.class);
        private final FakeSender sender = new FakeSender();
        private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final List<Runnable> delayedTasks = java.util.Collections.synchronizedList(new ArrayList<>());
        private final List<String> texts = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CallSession session;

        private Fixture(LanguageModel model) {
            properties.getVad().setEnergyThreshold(700);
            properties.getVad().setSpeechStartMs(60);
            properties.getVad().setSpeechEndMs(100);
            properties.getSilence().setRepromptAfterMs(8000);
            properties.getSilence().setHangupAfterMs(20000);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            doReturn(future).when(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            org.mockito.Mockito.when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                delayedTasks.add(invocation.getArgument(0));
                return future;
            });
            BusinessProfileLoader profile = mock(BusinessProfileLoader.class);
            when(profile.getProfile()).thenReturn(new BusinessProfile("Test Dental", "Thanks for calling. How can I help?", "weekdays", "1 Main St", "+12075550142", List.of()));
            PromptBuilder prompts = mock(PromptBuilder.class);
            when(prompts.build(anyString())).thenReturn("test system prompt");
            when(knowledgeQuestions.prepareAnswer(anyString(), anyString())).thenReturn(
                    new KnowledgeQuestionContext(List.of(new TurnSource(41L, "Test question?", 2.25))));
            TextToSpeech tts = (text, listener, token) -> {
                texts.add(text);
                listener.onAudio(new byte[]{(byte) 0xff});
            };
            session = new CallSession("CA_TEST", "+100", "+200", properties, persistence, stt, model, tts,
                    prompts, knowledgeQuestions, profile, callControl, sender, clock, scheduler);
            sender.session.set(session);
        }

        private void startAndWaitForGreeting() {
            session.start();
            verify(persistence, timeout(3000)).addTurn(eq("CA_TEST"), eq(0), eq(TurnRole.AGENT),
                    eq("Thanks for calling. How can I help?"), any(Instant.class), any(), any(), any(), any(), eq(false));
        }

        private void runLatestDelayedTask() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (delayedTasks.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(delayedTasks).isNotEmpty();
            delayedTasks.getLast().run();
        }

        @Override public void close() { session.close(); }
    }

    private static final class RecordingStt implements SpeechToText {
        private Listener listener;
        @Override public SttStream openStream(String callSid, Listener listener) {
            this.listener = listener;
            return new SttStream() {
                @Override public void sendAudio(byte[] mulaw) { }
                @Override public void endUtterance() { }
                @Override public void close() { }
            };
        }
    }

    private static final class FakeSender implements TwilioMediaSender {
        private final AtomicReference<CallSession> session = new AtomicReference<>();
        private volatile boolean autoAckMarks = true;
        private volatile CountDownLatch firstAudio = new CountDownLatch(1);
        private volatile int audioSent;
        @Override public void sendMedia(byte[] mulaw) { audioSent++; firstAudio.countDown(); }
        @Override public void sendMark(String name) { if (autoAckMarks) session.get().onMark(name); }
        private volatile int clears;
        @Override public void clear() { clears++; }
        @Override public void close() { }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
        private void advanceMillis(long millis) { current = current.plusMillis(millis); }
    }
}
