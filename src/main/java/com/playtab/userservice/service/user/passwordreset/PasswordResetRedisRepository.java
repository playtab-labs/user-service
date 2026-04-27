package com.playtab.userservice.service.user.passwordreset;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class PasswordResetRedisRepository {

    private final RedisTemplate<String, PasswordResetState> redis;

    public PasswordResetRedisRepository(RedisTemplate<String, PasswordResetState> passwordResetRedisTemplate) {
        this.redis = passwordResetRedisTemplate;
    }

    public void save(String key, PasswordResetState state, Duration ttl) {
        redis.opsForValue().set(key, state, ttl);
    }

    public Optional<PasswordResetState> find(String key) {
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
