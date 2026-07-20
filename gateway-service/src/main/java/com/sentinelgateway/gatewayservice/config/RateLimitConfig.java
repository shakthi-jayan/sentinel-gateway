package com.sentinelgateway.gatewayservice.config;

import org.springframework.context.annotation.*;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class RateLimitConfig {

    @Bean
    public RouterFunction<ServerResponse> rateLimitedBackendRoute() {
        return route("backend-route-limited")
                .GET("/api/v1/**", http())
                .before(uri("http://localhost:8081"))
                .filter(rateLimit(c -> c
                        .setCapacity(5)
                        .setPeriod(Duration.ofSeconds(10))
                        .setKeyResolver(request -> "global")))
                .build();
    }
}