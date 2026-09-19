package com.bienvenueblainville.notification;

import com.bienvenueblainville.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * A resident's notice inbox.
 *
 * <p>Authenticated, and scoped to the caller everywhere — the id in the path
 * is not a capability. Requires no external push service, which is why the
 * notification consumer can finally do something real.
 */
@RestController
@RequestMapping("/api/notifications")
public class ResidentNotificationController {
    private static final int MAX = 50;

    private final ResidentNotificationMapper mapper;

    public ResidentNotificationController(ResidentNotificationMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<ResidentNotification> inbox(@AuthenticationPrincipal AppUserPrincipal principal) {
        return mapper.findForUser(principal.id(), MAX);
    }

    /** Drives the badge, so the page does not have to fetch the whole inbox to draw a number. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("unread", mapper.countUnread(principal.id()));
    }

    @PostMapping("/{id}/read")
    public Map<String, Long> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        if (mapper.markRead(principal.id(), id) == 0) {
            // Covers "already read", "not yours" and "does not exist" with one
            // answer, so the response cannot be used to discover which.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No unread notification with that id.");
        }
        return Map.of("unread", mapper.countUnread(principal.id()));
    }
}
