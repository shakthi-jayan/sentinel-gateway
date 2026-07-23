package com.sentinelgateway.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class CircuitBreakerConfig {

    @Bean
    public RouterFunction<ServerResponse> downstreamBWithBreaker() {
        return route("downstream-b-protected")
                .route(path("/api/v2/**"), http())
                .before(uri("http://downstream-b:8082"))
                .filter(circuitBreaker("downstreamBBreaker", URI.create("forward:/fallback/downstream-b")))
                .build()
                .and(route("downstream-b-fallback")
                        .route(path("/fallback/downstream-b"), request ->
                                ServerResponse.status(503).body("downstream-b is temporarily unavailable. Please try again shortly."))
                        .build());
    }
}