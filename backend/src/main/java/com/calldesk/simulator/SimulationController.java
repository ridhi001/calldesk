package com.calldesk.simulator;

import com.calldesk.calls.CallPersistenceService;
import com.calldesk.config.CallDeskProperties;
import jakarta.validation.Valid;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {
    private final Environment environment;
    private final CallDeskProperties properties;
    private final ScenarioLoader scenarios;
    private final SimulatedCaller simulatedCaller;
    private final CallPersistenceService calls;
    private final Clock clock;
    private final ObjectProvider<ServletWebServerApplicationContext> server;

    public SimulationController(Environment environment, CallDeskProperties properties, ScenarioLoader scenarios,
                                SimulatedCaller simulatedCaller, CallPersistenceService calls, Clock clock,
                                ObjectProvider<ServletWebServerApplicationContext> server) {
        this.environment = environment; this.properties = properties; this.scenarios = scenarios;
        this.simulatedCaller = simulatedCaller; this.calls = calls; this.clock = clock; this.server = server;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SimulationAccepted simulate(@Valid @RequestBody SimulateRequest request) {
        boolean allMock = properties.getProviders().getStt().equalsIgnoreCase("mock")
                && properties.getProviders().getLlm().equalsIgnoreCase("mock")
                && properties.getProviders().getTts().equalsIgnoreCase("mock");
        if (!environment.acceptsProfiles(Profiles.of("sim")) && !allMock) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Simulation requires the sim profile or mock providers");
        }
        ScenarioDefinition scenario = scenarios.load(request.scenario());
        String callSid = "SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        // Simulated callers get varied, plausible numbers so the dashboard reads like real traffic.
        String from = "+91" + (7 + ThreadLocalRandom.current().nextInt(3)) + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        String to = "+916745550100";
        var call = calls.startCall(callSid, from, to, Instant.now(clock));
        ServletWebServerApplicationContext webServer = server.getIfAvailable();
        if (webServer == null || webServer.getWebServer() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulator requires a running web server");
        }
        int port = webServer.getWebServer().getPort();
        simulatedCaller.start(URI.create("ws://127.0.0.1:" + port + "/twilio/media"), callSid,
                from, to, scenario);
        return new SimulationAccepted(call.getId());
    }
}
