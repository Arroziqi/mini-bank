package com.bank.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationInMs;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    public void blacklist(String token) {
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + token,
                "true",
                jwtExpirationInMs,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
