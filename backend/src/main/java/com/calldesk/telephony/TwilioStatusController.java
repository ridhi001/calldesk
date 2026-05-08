package com.calldesk.telephony;

import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallPersistenceService;
import com.calldesk.config.CallDeskProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestController
public class TwilioStatusController {
    private final CallDeskProperties properties;
    private final TwilioSignatureValidator validator;
    private final CallPersistenceService calls;
    private final Clock clock;

    public TwilioStatusController(CallDeskProperties properties, TwilioSignatureValidator validator, CallPersistenceService calls, Clock clock) {
        this.properties = properties; this.validator = validator; this.calls = calls; this.clock = clock;
    }

    @PostMapping(value = "/twilio/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(HttpServletRequest request, @RequestParam MultiValueMap<String, String> form) {
        if (!validRequest(request, form)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String sid = form.getFirst("CallSid");
        String status = form.getFirst("CallStatus");
        if (sid != null && isFinal(status)) calls.endCall(sid, outcome(status), Instant.now(clock));
        return ResponseEntity.ok().build();
    }

    private boolean validRequest(HttpServletRequest request, MultiValueMap<String, String> form) {
        if (!properties.getTwilio().isValidateSignature()) return true;
        String url = properties.getPublicBaseUrl().replaceAll("/+$", "") + request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        Map<String, String> parameters = new TreeMap<>();
        form.forEach((key, values) -> parameters.put(key, values.isEmpty() ? "" : values.getFirst()));
        return validator.validate(request.getHeader("X-Twilio-Signature"), url, parameters);
    }

    private static boolean isFinal(String status) { return "completed".equals(status) || "busy".equals(status) || "failed".equals(status) || "no-answer".equals(status) || "canceled".equals(status); }
    private static CallOutcome outcome(String status) { return "completed".equals(status) ? CallOutcome.COMPLETED : CallOutcome.FAILED; }
}
