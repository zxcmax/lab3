package ru.sfu.testing.subscriptions.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.AuthenticationEntryPoint;
import ru.sfu.testing.subscriptions.data.AccountRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.sfu.testing.subscriptions.service.AuthService;

import java.util.List;
import java.util.Map;

@Configuration
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AccountRepository accounts, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(email -> accounts.findByEmail(email)
                .map(account -> User.withUsername(account.getId().toString())
                        .password(account.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found")));
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    OpaqueTokenIntrospector opaqueTokenIntrospector(AuthService auth) {
        return token -> auth.authenticate(token).map(session -> new DefaultOAuth2AuthenticatedPrincipal(
                        session.accountId().toString(), Map.of("session", session), List.of()))
                .orElseThrow(() -> new BadOpaqueTokenException("Invalid or expired token"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, OpaqueTokenIntrospector introspector) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401}
                    """);
        };
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized))
                .oauth2ResourceServer(server -> server.authenticationEntryPoint(unauthorized)
                        .opaqueToken(opaque -> opaque.introspector(introspector)))
                .authorizeHttpRequests(routes -> routes
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/openapi.json", "/swagger-ui.html",
                                "/swagger-ui/**", "/v3/api-docs/swagger-config").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
