package com.calldesk.calls;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class CallEventPublisherTest {

    @Test void liveStreamOpensImmediatelyAndDeliversNamedEvents() throws Exception {
        CallEventPublisher events = new CallEventPublisher();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CallApiController(mock(CallPersistenceService.class), events)).build();
        try {
            MvcResult result = mvc.perform(get("/api/calls/live")).andExpect(request().asyncStarted()).andReturn();
            assertThat(result.getResponse().getContentAsString()).as("sent before any call happens").contains(":connected");

            events.publish("call-ended", "{\"id\":7}");
            assertThat(result.getResponse().getContentAsString()).contains("event:call-ended").contains("\"id\":7");
        } finally {
            events.shutdown();
        }
    }
}
