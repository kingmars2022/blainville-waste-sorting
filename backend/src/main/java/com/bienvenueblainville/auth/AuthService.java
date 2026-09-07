package com.bienvenueblainville.auth;

import com.bienvenueblainville.auth.dto.AuthResponse;
import com.bienvenueblainville.auth.dto.LoginRequest;
import com.bienvenueblainville.auth.dto.RegisterRequest;
import com.bienvenueblainville.common.Role;
import com.bienvenueblainville.preference.UserPreferenceMapper;
import com.bienvenueblainville.security.JwtService;
import com.bienvenueblainville.user.AppUser;
import com.bienvenueblainville.user.AppUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final AppUserMapper appUserMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AppUserMapper appUserMapper,
            UserPreferenceMapper userPreferenceMapper,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.appUserMapper = appUserMapper;
        this.userPreferenceMapper = userPreferenceMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (appUserMapper.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email.");
        }

        try {
            appUserMapper.insert(normalizedEmail, passwordEncoder.encode(request.password()), Role.USER);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email.");
        }

        AppUser created = appUserMapper.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User was not persisted"));

        userPreferenceMapper.insertDefault(created.id());

        String token = jwtService.generateToken(created.id(), created.email(), created.role());
        return new AuthResponse(token, created.id(), created.email(), created.role());
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        AppUser user = appUserMapper.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        String token = jwtService.generateToken(user.id(), user.email(), user.role());
        return new AuthResponse(token, user.id(), user.email(), user.role());
    }
}
