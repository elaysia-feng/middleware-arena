package com.mware.order.config.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：覆盖默认 RedisTemplate 的序列化器。
 * <p>
 * 默认 Spring Boot 自带的 RedisTemplate 用 JDK 序列化（JdkSerializationRedisSerializer），
 * 要求 value 实现 {@link java.io.Serializable} 且内容不可读；这里改成
 * key 用字符串、value 用 JSON，幂等 key（create:order:xxx）可读，value 可跨语言存取。
 * <p>
 * 安全：GenericJackson2JsonRedisSerializer 序列化时把真实类型写入 JSON（@class），
 * 反序列化时按该类型实例化。若不限制，任何能写入 Redis 的攻击者都可投毒 @class 指向
 * 任意 gadget 类（反序列化漏洞）。这里通过 {@link BasicPolymorphicTypeValidator}
 * 把可实例化类型白名单限定为本项目 domain / JDK 基础类型。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(typedObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 带类型白名单的 ObjectMapper：只允许反序列化以下类型。
     * 注：NON_FINAL 下 final 类（如 Long、String）不写 @class，白名单不拦截，属正常。
     */
    private ObjectMapper typedObjectMapper() {
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.lang.")              // Long / Integer / String ...
                .allowIfSubType("java.util.")              // List / Map / HashMap ...
                .allowIfSubType("java.time.")              // LocalDateTime（Order.createdAt 等）
                .allowIfSubType("com.mware.order.domain.") // Order 缓存值
                .allowIfSubType("com.mware.order.dto.")    // DTO（如需缓存）
                .build();

        ObjectMapper mapper = new ObjectMapper();
        // Order 含 LocalDateTime 字段，不注册 JSR310 模块序列化会抛 InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
