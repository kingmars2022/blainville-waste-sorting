package com.bienvenueblainville.security;

import com.bienvenueblainville.user.AppUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AppUserMapper appUserMapper,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                // This chain governs the API and nothing else. Every endpoint
                // in the application is under /api, and since the built
                // frontend is now served from this same application, the
                // alternative is a filter chain that answers 401 to
                // index.html - which is how a single-deployment setup fails:
                // the whole site, not one endpoint.
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new AccessDeniedHandlerImpl())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/collections/**").permitAll()
                        .requestMatchers("/api/notices/active").permitAll()
                        // The sorting guide itself. Public, because it is the
                        // page residents come for.
                        .requestMatchers(HttpMethod.GET, "/api/sorting-items").permitAll()
                        // Public like the rest of the guide, but the only
                        // endpoint that can cost money per call - see
                        // AssistantRateLimiter for what stands in for auth here.
                        .requestMatchers(HttpMethod.POST, "/api/assistant/ask").permitAll()
                        // Photo upload and retrieval are public like the rest
                        // of the sorting guide. The upload endpoint issues a
                        // presigned URL rather than accepting bytes, and shares
                        // the assistant's per-IP quota because each signature
                        // it hands out is storage someone pays for. Only the
                        // processed copy is readable - see PhotoController.
                        .requestMatchers(HttpMethod.POST, "/api/photos/upload-url").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/photos/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/photos/*/identify").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, appUserMapper),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    /**
     * Which origins the browser may call this API from.
     *
     * <p>Was a hard-coded {@code http://localhost:5173}, which made the
     * application undeployable: the first thing a real deployment needs is to
     * be reachable from its own domain, and that is not something to discover
     * after the fact as an opaque CORS error in someone's console. The default
     * is unchanged, so a fresh clone still works with the Vite dev server and
     * nothing to configure.
     *
     * <p>Exact origins, comma separated - not patterns. A pattern is easy to
     * write more loosely than intended, and this list is short enough to spell
     * out. {@code *} is accepted for a fully public deployment; it is safe
     * <em>only</em> because credentials are never allowed here (authentication
     * is a Bearer token, not a cookie), so anything added later that sets
     * {@code allowCredentials} has to revisit this.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            // Refusing to start beats starting with an empty allow-list, which
            // rejects every browser request with an error that names neither
            // the setting nor the value.
            throw new IllegalStateException(
                    "app.cors.allowed-origins is empty. Set CORS_ALLOWED_ORIGINS to the "
                            + "origin the browser loads the site from, for example "
                            + "https://blainville.example.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // The browser has to be allowed to read it, or the retry that waits out
        // an unprocessed photo cannot see how long to wait.
        configuration.setExposedHeaders(List.of("Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
