package com.bienvenueblainville.auth;

import com.bienvenueblainville.auth.dto.AuthResponse;
import com.bienvenueblainville.auth.dto.LoginRequest;
import com.bienvenueblainville.auth.dto.RegisterRequest;
import com.bienvenueblainville.common.Role;
import com.bienvenueblainville.preference.UserPreferenceMapper;
import com.bienvenueblainville.security.JwtProperties;
import com.bienvenueblainville.security.JwtService;
import com.bienvenueblainville.user.AppUser;
import com.bienvenueblainville.user.AppUserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private UserPreferenceMapper userPreferenceMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtService = new JwtService(new JwtProperties(
                "bienvenue-a-blainville-test",
                "unit-test-secret-key-must-be-long-enough-for-hmac-sha",
                60
        ));
        authService = new AuthService(appUserMapper, userPreferenceMapper, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerCreatesUserWithDefaultPreferencesAndReturnsToken() {
        AppUser created = new AppUser(7L, "new@example.com", "hashed", Role.USER, Instant.now(), Instant.now());
        when(appUserMapper.findByEmail("new@example.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        AuthResponse response = authService.register(new RegisterRequest("new@example.com", "password123"));

        Optional<Claims> claims = jwtService.parseClaims(response.token());
        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("7");
        assertThat(claims.get().get("email", String.class)).isEqualTo("new@example.com");
        assertThat(claims.get().get("role", String.class)).isEqualTo("USER");
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.role()).isEqualTo(Role.USER);
        verify(appUserMapper).insert("new@example.com", "hashed", Role.USER);
        verify(userPreferenceMapper).insertDefault(7L);
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyRegistered() {
        AppUser existing = new AppUser(1L, "taken@example.com", "hash", Role.USER, Instant.now(), Instant.now());
        when(appUserMapper.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void loginRejectsBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("someone@example.com", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
