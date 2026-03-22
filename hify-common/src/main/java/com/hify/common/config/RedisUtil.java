package com.hify.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) redisTemplate.opsForValue().get(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
