package com.sentinelgateway.gatewayservice.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public AsyncProxyManager<String> asyncProxyManager() {
        RedisClient redisClient = RedisClient.create(
                RedisURI.builder().withHost("localhost").withPort(6379).build());

        StatefulRedisConnection<String, byte[]> connection =
                redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build()
                .asAsync();
    }

    @Bean
    public RouterFunction<ServerResponse> rateLimitedBackendRoute() {
        return route("backend-route-limited")
                .GET("/api/v1/**", http())
                .before(uri("http://localhost:8081"))
                .filter(rateLimit(c -> c
                        .setCapacity(5)
                        .setPeriod(Duration.ofSeconds(10))
                        .setKeyResolver(request -> {
                            String forwardedFor = request.headers().firstHeader("X-Forwarded-For");
                            if (forwardedFor != null && !forwardedFor.isBlank()) {
                                return forwardedFor.split(",")[0].trim();
                            }
                            return request.servletRequest().getRemoteAddr();
                        })))
                .build();
    }
}