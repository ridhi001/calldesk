package com.calldesk.telephony;

import com.calldesk.config.CallDeskProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TwilioCallControl implements CallControl {
    private final HttpClient client;
    private final CallDeskProperties properties;

    public TwilioCallControl(HttpClient client, CallDeskProperties properties) { this.client = client; this.properties = properties; }

    @Override public void transferTo(String callSid, String phoneNumber) {
        String twiml = "<Response><Dial>" + xml(phoneNumber) + "</Dial></Response>";
        update(callSid, "Twiml", twiml);
    }

    @Override public void hangup(String callSid) { update(callSid, "Status", "completed"); }

    private void update(String callSid, String key, String value) {
        String accountSid = properties.getTwilio().getAccountSid();
        String token = properties.getTwilio().getAuthToken();
        if (accountSid.isBlank() || token.isBlank()) throw new IllegalStateException("Twilio credentials are required for call control");
        String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + token).getBytes(StandardCharsets.UTF_8));
        String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Calls/" + encodePath(callSid) + ".json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(key) + "=" + encodeForm(value))).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Twilio call control returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio call control interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Twilio call control request failed", exception);
        }
    }

    private static String encodeForm(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String encodePath(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
}
