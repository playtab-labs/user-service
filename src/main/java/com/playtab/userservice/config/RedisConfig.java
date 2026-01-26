package com.playtab.userservice.config;

import com.playtab.userservice.service.user.email.EmailVerificationState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, EmailVerificationState> emailVerificationRedisTemplate(
            RedisConnectionFactory cf
    ) {
        RedisTemplate<String, EmailVerificationState> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);

        // Key / HashKey: string
        t.setKeySerializer(RedisSerializer.string());
        t.setHashKeySerializer(RedisSerializer.string());

        // Value / HashValue: json (Spring Data Redis 4.x 권장)
        t.setValueSerializer(RedisSerializer.json());
        t.setHashValueSerializer(RedisSerializer.json());

        t.afterPropertiesSet();
        return t;
    }
}