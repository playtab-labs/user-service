package com.playtab.userservice.service.user.email;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class EmailVerificationRedisRepository {

    private final RedisTemplate<String, EmailVerificationState> redis;

    public EmailVerificationRedisRepository(RedisTemplate<String, EmailVerificationState> redis) {
        this.redis = redis;
    }

    public void save(String key, EmailVerificationState state, Duration ttl) {
        redis.opsForValue().set(key, state, ttl);
    }

    public Optional<EmailVerificationState> find(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    public void delete(String key) {
        redis.delete(key);
    }

    public long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null ? -1 : ttl;
    }
}