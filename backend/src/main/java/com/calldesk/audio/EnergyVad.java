package com.calldesk.audio;

import java.util.Objects;

/** Frame-count based VAD; the caller supplies exactly one 20 ms PCM frame per call. */
public final class EnergyVad {
    public enum Event { NONE, SPEECH_STARTED, SPEECH_ENDED }
    private static final int FRAME_MS = 20;
    private final double threshold;
    private final int startFrames;
    private final int endFrames;
    private boolean speaking;
    private int consecutiveSpeechFrames;
    private int consecutiveSilenceFrames;

    public EnergyVad(double threshold, long speechStartMs, long speechEndMs) {
        if (threshold < 0 || speechStartMs < 0 || speechEndMs < 0) throw new IllegalArgumentException("VAD settings must be non-negative");
        this.threshold = threshold;
        this.startFrames = Math.max(1, (int) Math.ceil((double) speechStartMs / FRAME_MS));
        this.endFrames = Math.max(1, (int) Math.ceil((double) speechEndMs / FRAME_MS));
    }

    public Event accept(short[] pcmFrame) {
        Objects.requireNonNull(pcmFrame, "pcmFrame");
        if (pcmFrame.length == 0) return Event.NONE;
        double sum = 0;
        for (short sample : pcmFrame) sum += (double) sample * sample;
        double rms = Math.sqrt(sum / pcmFrame.length);
        if (!speaking) {
            if (rms >= threshold) consecutiveSpeechFrames++;
            else consecutiveSpeechFrames = 0;
            if (consecutiveSpeechFrames >= startFrames) {
                speaking = true;
                consecutiveSilenceFrames = 0;
                return Event.SPEECH_STARTED;
            }
        } else {
            if (rms < threshold) consecutiveSilenceFrames++;
            else consecutiveSilenceFrames = 0;
            if (consecutiveSilenceFrames >= endFrames) {
                speaking = false;
                consecutiveSpeechFrames = 0;
                consecutiveSilenceFrames = 0;
                return Event.SPEECH_ENDED;
            }
        }
        return Event.NONE;
    }

    public boolean isSpeaking() { return speaking; }

    public void reset() {
        speaking = false;
        consecutiveSpeechFrames = 0;
        consecutiveSilenceFrames = 0;
    }
}
