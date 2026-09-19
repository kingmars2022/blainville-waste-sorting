package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.assistant.dto.AssistantRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService service;
    private final AssistantRateLimiter rateLimiter;

    public AssistantController(AssistantService service, AssistantRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/ask")
    public AssistantAnswer ask(@Valid @RequestBody AssistantRequest request, HttpServletRequest http) {
        rateLimiter.check(clientId(http));
        return service.ask(request);
    }

    /**
     * The remote address, and only the remote address.
     *
     * <p>Deliberately not X-Forwarded-For: that header is attacker-controlled
     * unless a trusted proxy is known to rewrite it, and a rate limit keyed on
     * a value the caller picks is not a rate limit. Behind a real proxy this
     * needs Spring's ForwardedHeaderFilter plus a trusted-proxy list — a
     * deployment decision, made once, not a default.
     */
    private String clientId(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
