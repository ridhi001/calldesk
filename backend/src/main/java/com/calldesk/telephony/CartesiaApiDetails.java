package com.calldesk.telephony;

/** Cartesia wire details live here so they can be checked against the provider documentation. */
public final class CartesiaApiDetails {
    // verify against Cartesia docs
    public static final String STT_WEBSOCKET_URL = "wss://api.cartesia.ai/stt/websocket";
    public static final String TTS_WEBSOCKET_URL = "wss://api.cartesia.ai/tts/websocket";
    public static final String API_KEY_HEADER = "Cartesia-API-Key";
    public static final String VERSION_HEADER = "Cartesia-Version";
    public static final String MODEL_FIELD = "model";
    public static final String STT_AUDIO_FIELD = "audio";
    public static final String STT_SAMPLE_RATE_FIELD = "sample_rate";
    public static final String STT_ENCODING_FIELD = "encoding";
    public static final String STT_FINALIZE_FIELD = "finalize";
    public static final String TRANSCRIPT_FIELD = "text";
    public static final String IS_FINAL_FIELD = "is_final";
    public static final String TYPE_FIELD = "type";
    public static final String TTS_TRANSCRIPT_FIELD = "transcript";
    public static final String TTS_OUTPUT_FORMAT_FIELD = "output_format";
    public static final String TTS_AUDIO_FIELD = "audio";
    public static final String TTS_MODEL_FIELD = "model_id";
    public static final String TTS_VOICE_FIELD = "voice";
    public static final String TTS_VOICE_MODE_FIELD = "mode";
    public static final String TTS_VOICE_ID_FIELD = "id";
    public static final String TTS_CONTAINER_FIELD = "container";
    public static final String TTS_ENCODING_FIELD = "encoding";
    public static final String TTS_SAMPLE_RATE_FIELD = "sample_rate";
    public static final String TTS_CONTINUE_FIELD = "continue";
    public static final String TTS_DONE_FIELD = "done";
    public static final String MULAW_ENCODING = "pcm_mulaw";
    public static final String RAW_CONTAINER = "raw";
    public static final int SAMPLE_RATE = 8000;

    private CartesiaApiDetails() { }
}
