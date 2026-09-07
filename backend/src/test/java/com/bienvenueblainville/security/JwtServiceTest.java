package com.bienvenueblainville.security;

import com.bienvenueblainville.common.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private final JwtProperties properties = new JwtProperties(
            "bienvenue-a-blainville",
            "unit-test-secret-key-must-be-long-enough-for-hmac-sha",
            60
    );
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void generatesTokenThatParsesBackToTheSameClaims() {
        String token = jwtService.generateToken(42L, "resident@example.com", Role.USER);

        Optional<Claims> claims = jwtService.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("42");
        assertThat(claims.get().get("email", String.class)).isEqualTo("resident@example.com");
        assertThat(claims.get().get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedTokens() {
        String token = jwtService.generateToken(1L, "admin@example.com", Role.ADMIN);

        Optional<Claims> claims = jwtService.parseClaims(token + "tampered");

        assertThat(claims).isEmpty();
    }

    @Test
    void rejectsTokensSignedWithADifferentIssuer() {
        JwtService otherIssuer = new JwtService(new JwtProperties(
                "someone-else", properties.secret(), 60));
        String token = otherIssuer.generateToken(1L, "admin@example.com", Role.ADMIN);

        Optional<Claims> claims = jwtService.parseClaims(token);

        assertThat(claims).isEmpty();
    }
}
