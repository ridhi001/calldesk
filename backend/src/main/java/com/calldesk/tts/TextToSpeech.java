package com.calldesk.tts;

import com.calldesk.conversation.CancellationToken;

public interface TextToSpeech {
    void stream(String text, AudioListener listener, CancellationToken cancellationToken);
    interface AudioListener { void onAudio(byte[] mulaw); }
}
