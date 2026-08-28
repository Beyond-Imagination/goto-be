package kr.bi.go_to.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import kr.bi.go_to.properties.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private final RedisConnectionFactory connectionFactory;
    private final CacheProperties cacheProperties;

    public RedisCacheConfig(RedisConnectionFactory connectionFactory, CacheProperties cacheProperties) {
        this.connectionFactory = connectionFactory;
        this.cacheProperties = cacheProperties;
    }

    @Override
    @Bean
    public CacheManager cacheManager() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();

        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheProperties.getDefaultTtl())
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> initialCacheConfigurations = new HashMap<>();
        cacheProperties.getCaches().values().forEach(setting -> {
            if (setting.getName() != null) {
                Duration ttl = setting.getTtl() != null ? setting.getTtl() : cacheProperties.getDefaultTtl();
                initialCacheConfigurations.put(setting.getName(), defaultConfiguration.entryTtl(ttl));
            }
        });

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(initialCacheConfigurations)
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
