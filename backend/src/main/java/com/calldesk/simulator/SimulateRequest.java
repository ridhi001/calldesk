package com.calldesk.simulator;

import jakarta.validation.constraints.NotBlank;

public record SimulateRequest(@NotBlank String scenario) { }
