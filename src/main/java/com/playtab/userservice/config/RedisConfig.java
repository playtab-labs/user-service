package com.playtab.userservice.config;

import com.playtab.userservice.service.user.email.EmailVerificationState;
import com.playtab.userservice.service.user.passwordreset.PasswordResetState;
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
        t.setKeySerializer(RedisSerializer.string());
        t.setHashKeySerializer(RedisSerializer.string());
        t.setValueSerializer(RedisSerializer.json());
        t.setHashValueSerializer(RedisSerializer.json());
        t.afterPropertiesSet();
        return t;
    }

    @Bean
    public RedisTemplate<String, PasswordResetState> passwordResetRedisTemplate(
            RedisConnectionFactory cf
    ) {
        RedisTemplate<String, PasswordResetState> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(RedisSerializer.string());
        t.setHashKeySerializer(RedisSerializer.string());
        t.setValueSerializer(RedisSerializer.json());
        t.setHashValueSerializer(RedisSerializer.json());
        t.afterPropertiesSet();
        return t;
    }
}