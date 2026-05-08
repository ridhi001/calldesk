package com.calldesk.telephony;

import com.calldesk.config.CallDeskProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

@Component
public class TwilioSignatureValidator {
    private final CallDeskProperties properties;

    public TwilioSignatureValidator(CallDeskProperties properties) { this.properties = properties; }

    public boolean validate(String signature, String url, Map<String, String> parameters) {
        if (!properties.getTwilio().isValidateSignature()) return true;
        String authToken = properties.getTwilio().getAuthToken();
        if (authToken.isBlank() || signature == null || signature.isBlank()) return false;
        StringBuilder data = new StringBuilder(url);
        new TreeMap<>(parameters).forEach((key, value) -> data.append(key).append(value == null ? "" : value));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Twilio signature", exception);
        }
    }
}
