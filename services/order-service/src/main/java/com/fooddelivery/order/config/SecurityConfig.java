package com.fooddelivery.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Order Service.
 *
 * Auth is handled at Kong gateway level:
 * - Kong validates JWT and forwards X-User-Id + X-User-Role headers.
 * - Internal endpoints (/api/internal/**) are protected by K8s NetworkPolicy.
 * - Spring Security simply disables session/CSRF for stateless REST.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health/**", "/actuator/**", "/api/internal/**").permitAll()
                .anyRequest().permitAll()   // Kong handles JWT; we trust X-User-* headers
            );
        return http.build();
    }
}
