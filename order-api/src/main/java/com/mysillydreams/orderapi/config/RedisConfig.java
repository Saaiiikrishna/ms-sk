package com.mysillydreams.orderapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
    RedisTemplate<String, Object> tpl = new RedisTemplate<>();
    tpl.setConnectionFactory(cf);
    tpl.setKeySerializer(new StringRedisSerializer());
    // Using GenericJackson2JsonRedisSerializer for values to handle various object types,
    // especially for the CachedResponse DTO we will create.
    tpl.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    // It's also good practice to set hash key/value serializers if using HASH operations,
    // but for simple opsForValue, key and value serializers are primary.
    // tpl.setHashKeySerializer(new StringRedisSerializer());
    // tpl.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
    tpl.afterPropertiesSet(); // Ensure serializers are initialized
    return tpl;
  }
}
