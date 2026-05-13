package com.calldesk.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyVadTest {
    @Test void onsetAndOffsetUseConfiguredFrameCounts() {
        EnergyVad vad = new EnergyVad(1000, 60, 100);
        short[] speech = frame((short) 4000);
        short[] silence = frame((short) 0);
        assertThat(vad.accept(speech)).isEqualTo(EnergyVad.Event.NONE);
        assertThat(vad.accept(speech)).isEqualTo(EnergyVad.Event.NONE);
        assertThat(vad.accept(speech)).isEqualTo(EnergyVad.Event.SPEECH_STARTED);
        for (int i = 0; i < 4; i++) assertThat(vad.accept(silence)).isEqualTo(EnergyVad.Event.NONE);
        assertThat(vad.accept(silence)).isEqualTo(EnergyVad.Event.SPEECH_ENDED);
    }

    @Test void shortNoiseDoesNotBecomeSpeech() {
        EnergyVad vad = new EnergyVad(1000, 60, 100);
        for (int i = 0; i < 2; i++) assertThat(vad.accept(frame((short) 4000))).isEqualTo(EnergyVad.Event.NONE);
        assertThat(vad.accept(frame((short) 0))).isEqualTo(EnergyVad.Event.NONE);
        assertThat(vad.isSpeaking()).isFalse();
    }

    private static short[] frame(short value) { short[] frame = new short[160]; java.util.Arrays.fill(frame, value); return frame; }
}
