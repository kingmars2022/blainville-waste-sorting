package com.bienvenueblainville.preference;

import com.bienvenueblainville.preference.dto.UserPreferenceRequest;
import com.bienvenueblainville.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public UserPreference get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.getForUser(principal.id());
    }

    @PutMapping
    public UserPreference update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UserPreferenceRequest request
    ) {
        return service.update(principal.id(), request);
    }
}
