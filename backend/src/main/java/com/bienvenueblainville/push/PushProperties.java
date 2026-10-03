package com.bienvenueblainville.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled    off by default. Push needs a key pair an operator has to
 *                   generate, and a feature that half-works because a key is
 *                   missing is worse than one that is plainly switched off.
 * @param publicKey  base64url, also handed to the browser as applicationServerKey
 * @param privateKey base64url. A secret: it signs the application server's identity.
 * @param subject    mailto: or https: URL a push service can use to reach whoever
 *                   is sending. Required by RFC 8292.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        boolean enabled,
        String publicKey,
        String privateKey,
        String subject
) {
}
