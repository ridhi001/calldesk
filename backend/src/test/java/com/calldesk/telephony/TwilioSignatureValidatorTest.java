package com.calldesk.telephony;

import com.calldesk.config.CallDeskProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioSignatureValidatorTest {
    private static final String URL = "https://mycompany.com/myapp.php?foo=1&bar=2";
    private static final Map<String, String> PARAMETERS = Map.of("CallSid", "CA1234567890ABCDE", "To", "+18005551212", "From", "+14158675310");

    @Test void validatesTheTwilioHmacSha1SigningExample() throws Exception {
        CallDeskProperties properties = new CallDeskProperties();
        properties.getTwilio().setAuthToken("12345");
        properties.getTwilio().setValidateSignature(true);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties);
        String signature = signature(URL, PARAMETERS, "12345");
        assertThat(validator.validate(signature, URL, PARAMETERS)).isTrue();
    }

    @Test void rejectsModifiedParameters() throws Exception {
        CallDeskProperties properties = new CallDeskProperties();
        properties.getTwilio().setAuthToken("12345");
        properties.getTwilio().setValidateSignature(true);
        TwilioSignatureValidator validator = new TwilioSignatureValidator(properties);
        String signature = signature(URL, PARAMETERS, "12345");
        assertThat(validator.validate(signature, URL, Map.of("CallSid", "changed"))).isFalse();
    }

    private static String signature(String url, Map<String, String> parameters, String token) throws Exception {
        StringBuilder input = new StringBuilder(url);
        new TreeMap<>(parameters).forEach((key, value) -> input.append(key).append(value));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(input.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
