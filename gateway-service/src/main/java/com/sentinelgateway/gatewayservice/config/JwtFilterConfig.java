package com.sentinelgateway.gatewayservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtFilterConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public HandlerFilterFunction<ServerResponse, ServerResponse> jwtAuthFilter() {
        return (request, next) -> {
            String authHeader = request.headers().firstHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .body("Missing or malformed Authorization header");
            }

            String token = authHeader.substring(7);

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token);
            } catch (JwtException e) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid or expired token: " + e.getMessage());
            }

            return next.handle(request);
        };
    }
}