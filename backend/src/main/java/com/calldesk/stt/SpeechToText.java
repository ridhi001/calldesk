package com.calldesk.stt;

public interface SpeechToText {
    SttStream openStream(String callSid, Listener listener);

    interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        default void onError(Throwable error) { }
    }

    interface SttStream extends AutoCloseable {
        void sendAudio(byte[] mulaw);
        void endUtterance();
        @Override void close();
    }
}
