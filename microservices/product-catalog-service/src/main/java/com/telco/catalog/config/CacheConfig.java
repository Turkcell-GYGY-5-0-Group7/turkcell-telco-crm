package com.telco.catalog.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration TARIFF_TTL = Duration.ofMinutes(10);
    private static final Duration ADDON_TTL = Duration.ofMinutes(5);

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // LaissezFaireSubTypeValidator is unsafe: it trusts any @class name in cached JSON,
        // enabling deserialization gadget attacks if Redis is compromised (CVE-2017-7525 class).
        // BasicPolymorphicTypeValidator restricts type resolution to this service's own DTOs,
        // the shared platform pagination envelope, and safe JDK value types — anything else
        // throws JsonTypeDefinitionException.
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.telco.catalog.application.dto.")
                .allowIfSubType("com.telco.platform.common.api.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();

        // Cached DTOs are Java records, which are implicitly final. DefaultTyping.NON_FINAL
        // never writes "@class" type metadata for final classes, so a value serialized on a
        // cache PUT loses its concrete type and a subsequent cache HIT fails to deserialize
        // with InvalidTypeIdException (missing type id). DefaultTyping.EVERYTHING writes type
        // metadata for all values — including final classes/records — so every cache HIT can
        // round-trip correctly. The PolymorphicTypeValidator above keeps this safe by rejecting
        // any type outside this service's own DTOs, the platform pagination envelope, and
        // well-known JDK value types.
        ObjectMapper om = new ObjectMapper()
                .findAndRegisterModules()
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);

        RedisSerializer<Object> valueSerializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) throws SerializationException {
                if (value == null) return null;
                try { return om.writeValueAsBytes(value); }
                catch (JsonProcessingException e) {
                    throw new SerializationException("Cannot serialize cache value", e);
                }
            }
            @Override
            public Object deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try { return om.readValue(bytes, Object.class); }
                catch (IOException e) {
                    throw new SerializationException("Cannot deserialize cache value", e);
                }
            }
        };

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        "tariffs", defaults.entryTtl(TARIFF_TTL),
                        "addons", defaults.entryTtl(ADDON_TTL)
                ))
                .build();
    }
}
