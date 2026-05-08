package com.calldesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "calldesk")
public class CallDeskProperties {
    private String publicBaseUrl = "http://localhost:8080";
    private String businessProfile = "classpath:business/demo-clinic.yaml";
    private final Providers providers = new Providers();
    private final Cartesia cartesia = new Cartesia();
    private final OpenRouter openrouter = new OpenRouter();
    private final Twilio twilio = new Twilio();
    private final Vad vad = new Vad();
    private final Silence silence = new Silence();
    private final EagerReply eagerReply = new EagerReply();
    private final Mock mock = new Mock();
    private final Knowledge knowledge = new Knowledge();

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public String getBusinessProfile() { return businessProfile; }
    public void setBusinessProfile(String businessProfile) { this.businessProfile = businessProfile; }
    public Providers getProviders() { return providers; }
    public Cartesia getCartesia() { return cartesia; }
    public OpenRouter getOpenrouter() { return openrouter; }
    public Twilio getTwilio() { return twilio; }
    public Vad getVad() { return vad; }
    public Silence getSilence() { return silence; }
    public EagerReply getEagerReply() { return eagerReply; }
    public Mock getMock() { return mock; }
    public Knowledge getKnowledge() { return knowledge; }

    public static class Knowledge {
        private double minScore = 2.2;
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
    }

    public static class Providers {
        private String stt = "mock";
        private String llm = "mock";
        private String tts = "mock";
        public String getStt() { return stt; }
        public void setStt(String stt) { this.stt = stt; }
        public String getLlm() { return llm; }
        public void setLlm(String llm) { this.llm = llm; }
        public String getTts() { return tts; }
        public void setTts(String tts) { this.tts = tts; }
    }

    public static class Cartesia {
        private String apiKey = "";
        private String sttModel = "ink-whisper";
        private String ttsModel = "sonic-2";
        private String voiceId = "";
        private String version = "2025-04-16";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSttModel() { return sttModel; }
        public void setSttModel(String sttModel) { this.sttModel = sttModel; }
        public String getTtsModel() { return ttsModel; }
        public void setTtsModel(String ttsModel) { this.ttsModel = ttsModel; }
        public String getVoiceId() { return voiceId; }
        public void setVoiceId(String voiceId) { this.voiceId = voiceId; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public static class OpenRouter {
        private String apiKey = "";
        private String model = "openai/gpt-4o-mini";
        private String baseUrl = "https://openrouter.ai/api/v1";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private boolean validateSignature;
        public String getAccountSid() { return accountSid; }
        public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
        public boolean isValidateSignature() { return validateSignature; }
        public void setValidateSignature(boolean validateSignature) { this.validateSignature = validateSignature; }
    }

    public static class Vad {
        private double energyThreshold = 700.0;
        private long speechStartMs = 60;
        private long speechEndMs = 500;
        public double getEnergyThreshold() { return energyThreshold; }
        public void setEnergyThreshold(double energyThreshold) { this.energyThreshold = energyThreshold; }
        public long getSpeechStartMs() { return speechStartMs; }
        public void setSpeechStartMs(long speechStartMs) { this.speechStartMs = speechStartMs; }
        public long getSpeechEndMs() { return speechEndMs; }
        public void setSpeechEndMs(long speechEndMs) { this.speechEndMs = speechEndMs; }
    }

    public static class Silence {
        private long repromptAfterMs = 8000;
        private long hangupAfterMs = 20000;
        public long getRepromptAfterMs() { return repromptAfterMs; }
        public void setRepromptAfterMs(long repromptAfterMs) { this.repromptAfterMs = repromptAfterMs; }
        public long getHangupAfterMs() { return hangupAfterMs; }
        public void setHangupAfterMs(long hangupAfterMs) { this.hangupAfterMs = hangupAfterMs; }
    }

    public static class EagerReply {
        private boolean enabled = true;
        private long stablePartialMs = 250;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getStablePartialMs() { return stablePartialMs; }
        public void setStablePartialMs(long stablePartialMs) { this.stablePartialMs = stablePartialMs; }
    }

    public static class Mock {
        private long sttDelayMs = 0;
        private long llmTokenDelayMs = 20;
        private long ttsFirstChunkDelayMs = 80;
        public long getSttDelayMs() { return sttDelayMs; }
        public void setSttDelayMs(long sttDelayMs) { this.sttDelayMs = sttDelayMs; }
        public long getLlmTokenDelayMs() { return llmTokenDelayMs; }
        public void setLlmTokenDelayMs(long llmTokenDelayMs) { this.llmTokenDelayMs = llmTokenDelayMs; }
        public long getTtsFirstChunkDelayMs() { return ttsFirstChunkDelayMs; }
        public void setTtsFirstChunkDelayMs(long ttsFirstChunkDelayMs) { this.ttsFirstChunkDelayMs = ttsFirstChunkDelayMs; }
    }
}
