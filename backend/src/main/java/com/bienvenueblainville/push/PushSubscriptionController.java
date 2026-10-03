package com.bienvenueblainville.push;

import com.bienvenueblainville.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Where a browser registers the device it wants notices on.
 *
 * <p>Scoped to the caller: a subscription belongs to the resident who created
 * it, and unsubscribing names an endpoint rather than an id, because the
 * browser knows its endpoint and has no reason to know a database key.
 */
@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {
    private final PushSubscriptionMapper subscriptions;
    private final PushProperties properties;

    public PushSubscriptionController(PushSubscriptionMapper subscriptions, PushProperties properties) {
        this.subscriptions = subscriptions;
        this.properties = properties;
    }

    /**
     * Public: the browser needs this before it can subscribe, and it is a
     * public key. {@code enabled} is here so the page can hide the control
     * rather than offering something that will fail.
     */
    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of(
                "enabled", properties.enabled(),
                "publicKey", properties.enabled() && properties.publicKey() != null ? properties.publicKey() : "");
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        // Decoding here rather than at send time: a key that cannot be parsed
        // should be refused by the request that supplied it, not discovered
        // months later by a notice that fails to go out. This also rejects an
        // off-curve point, which is a key a caller can choose.
        try {
            WebPushCrypto.decodePublicKey(WebPushCrypto.fromBase64Url(request.p256dh()));
        } catch (IllegalArgumentException e) {
            // The value came from the request, so this is the caller's problem.
            // Letting it escape makes a malformed key a 500, which blames the
            // server and files it under outages rather than bad input.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "p256dh is not a usable P-256 public key");
        }

        subscriptions.upsert(new PushSubscription(
                null, principal.id(), request.endpoint(), request.p256dh(), request.auth(),
                request.languageCode() == null ? "fr" : request.languageCode()));
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam String endpoint
    ) {
        subscriptions.deleteByEndpointForUser(endpoint, principal.id());
    }
}
