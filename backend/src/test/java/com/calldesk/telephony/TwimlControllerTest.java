package com.calldesk.telephony;

import com.calldesk.config.CallDeskProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwimlControllerTest {
    @Test void returnsMediaStreamUrlAndCallerParameters() {
        CallDeskProperties properties = new CallDeskProperties();
        properties.setPublicBaseUrl("https://clinic.ngrok.app");
        TwilioSignatureValidator validator = mock(TwilioSignatureValidator.class);
        when(validator.validate(anyString(), anyString(), anyMap())).thenReturn(true);
        TwimlController controller = new TwimlController(properties, validator);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/twilio/voice");
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", "+15551234567");
        form.add("To", "+15557654321");
        form.add("CallSid", "CA123");

        String twiml = controller.voice(request, form).getBody();
        assertThat(twiml).contains("wss://clinic.ngrok.app/twilio/media")
                .contains("<Parameter name=\"from\" value=\"+15551234567\"/>")
                .contains("<Parameter name=\"to\" value=\"+15557654321\"/>")
                .contains("<Parameter name=\"callSid\" value=\"CA123\"/>");
    }
}
