package com.calldesk.calls;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class CallApiController {
    private final CallPersistenceService calls;
    private final CallEventPublisher events;

    public CallApiController(CallPersistenceService calls, CallEventPublisher events) { this.calls = calls; this.events = events; }

    @GetMapping("/calls")
    public PageDto<CallSummaryDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) CallOutcome outcome) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        return calls.listCalls(page, size, outcome);
    }

    @GetMapping("/calls/{id}") public CallDetailDto detail(@PathVariable long id) { return calls.getCall(id); }
    @GetMapping("/metrics") public MetricsDto metrics() { return calls.metrics(); }
    @GetMapping("/calls/live") public SseEmitter live() { return events.subscribe(); }

    @ExceptionHandler(CallNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorDto notFound(CallNotFoundException exception) { return new ApiErrorDto(exception.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDto badRequest(IllegalArgumentException exception) { return new ApiErrorDto(exception.getMessage()); }
}
