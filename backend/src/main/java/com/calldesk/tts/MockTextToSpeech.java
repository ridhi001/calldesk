package com.calldesk.tts;

import com.calldesk.audio.MuLaw;
import com.calldesk.config.CallDeskProperties;
import com.calldesk.conversation.CancellationToken;

public class MockTextToSpeech implements TextToSpeech {
    private final long firstChunkDelayMs;

    public MockTextToSpeech(CallDeskProperties properties) { this.firstChunkDelayMs = properties.getMock().getTtsFirstChunkDelayMs(); }

    @Override public void stream(String text, AudioListener listener, CancellationToken cancellationToken) {
        if (firstChunkDelayMs > 0) {
            try { Thread.sleep(firstChunkDelayMs); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); cancellationToken.throwIfCancelled(); }
        }
        int frameCount = Math.max(3, Math.min(60, text.length() * 2));
        for (int frame = 0; frame < frameCount; frame++) {
            cancellationToken.throwIfCancelled();
            short[] samples = new short[160];
            for (int index = 0; index < samples.length; index++) {
                double phase = 2.0 * Math.PI * 440.0 * (frame * samples.length + index) / 8000.0;
                samples[index] = (short) (Math.sin(phase) * 5000);
            }
            listener.onAudio(MuLaw.encode(samples));
        }
    }
}
