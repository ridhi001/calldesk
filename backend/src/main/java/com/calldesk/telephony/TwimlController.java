package com.calldesk.telephony;

import com.calldesk.config.CallDeskProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

@RestController
public class TwimlController {
    private final CallDeskProperties properties;
    private final TwilioSignatureValidator validator;

    public TwimlController(CallDeskProperties properties, TwilioSignatureValidator validator) { this.properties = properties; this.validator = validator; }

    @PostMapping(value = "/twilio/voice", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> voice(HttpServletRequest request, @RequestParam MultiValueMap<String, String> form) {
        if (!validRequest(request, form)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        String from = first(form, "From", "");
        String to = first(form, "To", "");
        String callSid = first(form, "CallSid", "");
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        String wsBase = base.startsWith("https://") ? "wss://" + base.substring(8) : "ws://" + base.substring(base.indexOf("://") + 3);
        String streamUrl = xml(wsBase + "/twilio/media");
        String twiml = "<Response><Connect><Stream url=\"" + streamUrl + "\">"
                + parameter("from", from) + parameter("to", to) + parameter("callSid", callSid)
                + "</Stream></Connect></Response>";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(twiml);
    }

    private boolean validRequest(HttpServletRequest request, MultiValueMap<String, String> form) {
        if (!properties.getTwilio().isValidateSignature()) return true;
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        String url = base + request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        Map<String, String> parameters = new TreeMap<>();
        form.forEach((key, values) -> parameters.put(key, values.isEmpty() ? "" : values.getFirst()));
        return validator.validate(request.getHeader("X-Twilio-Signature"), url, parameters);
    }

    private static String first(MultiValueMap<String, String> map, String key, String fallback) { return map.getFirst(key) == null ? fallback : map.getFirst(key); }
    private static String parameter(String name, String value) { return "<Parameter name=\"" + xml(name) + "\" value=\"" + xml(value) + "\"/>"; }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
}
