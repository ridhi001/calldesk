package com.calldesk.knowledge;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class KnowledgeApiController {
    private final KnowledgeService knowledge;
    private final KnowledgeGapService gaps;
    private final BusinessProfileLoader profileLoader;

    public KnowledgeApiController(KnowledgeService knowledge, KnowledgeGapService gaps, BusinessProfileLoader profileLoader) {
        this.knowledge = knowledge;
        this.gaps = gaps;
        this.profileLoader = profileLoader;
    }

    @GetMapping("/business")
    public BusinessDto business() { return BusinessDto.from(profileLoader.getProfile()); }

    @GetMapping("/knowledge")
    public List<KnowledgeEntryDto> entries(@RequestParam(required = false) String q) { return knowledge.list(q); }

    @PostMapping("/knowledge")
    public ResponseEntity<KnowledgeEntryDto> create(@Valid @RequestBody KnowledgeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledge.create(request));
    }

    @PutMapping("/knowledge/{id}")
    public KnowledgeEntryDto update(@PathVariable long id, @Valid @RequestBody KnowledgeEntryRequest request) {
        return knowledge.update(id, request);
    }

    @DeleteMapping("/knowledge/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { knowledge.delete(id); }

    @GetMapping("/knowledge/search")
    public KnowledgeSearchDto search(@RequestParam(required = false) String q) { return knowledge.search(q); }

    @GetMapping("/knowledge/gaps")
    public List<KnowledgeGapDto> gaps(@RequestParam(defaultValue = "OPEN") String status) { return gaps.list(status); }

    @PostMapping("/knowledge/gaps/{id}/resolve")
    public KnowledgeGapResolutionDto resolve(@PathVariable long id, @Valid @RequestBody KnowledgeEntryRequest request) {
        return gaps.resolve(id, request);
    }

    @PostMapping("/knowledge/gaps/{id}/dismiss")
    public KnowledgeGapDto dismiss(@PathVariable long id) { return gaps.dismiss(id); }

    @ExceptionHandler(KnowledgeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public KnowledgeErrorDto notFound(KnowledgeNotFoundException exception) { return new KnowledgeErrorDto(exception.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public KnowledgeErrorDto badRequest(IllegalArgumentException exception) { return new KnowledgeErrorDto(exception.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public KnowledgeErrorDto validation(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return new KnowledgeErrorDto(error == null ? "Request validation failed" : error.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public KnowledgeErrorDto unreadable(HttpMessageNotReadableException exception) { return new KnowledgeErrorDto("Request body is invalid"); }
}
