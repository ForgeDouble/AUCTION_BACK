package com.example.auction.common.config;


import com.example.auction.common.service.ChatSubscriber;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    public LettuceConnectionFactory redisConnectionFactory(int index) {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);
        redisStandaloneConfiguration.setDatabase(index);
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    @Primary
    LettuceConnectionFactory connectionFactory() {
        return redisConnectionFactory(0);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }

    // key = 1 중복 로그인 방지
    @Bean
    @Qualifier("login")
    LettuceConnectionFactory connectionFactoryLogin() {
        return redisConnectionFactory(1);
    }

    @Bean
    @Qualifier("login")
    public RedisTemplate<String, Object> loginRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactoryLogin());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    // key = 2 신고 동시성 해결
    @Bean
    @Qualifier("report")
    LettuceConnectionFactory connectionFactoryReport() {
        return redisConnectionFactory(2);
    }

    @Bean
    @Qualifier("report")
    public RedisTemplate<String, Object> reportRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactoryReport());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }
    @Bean
    @Qualifier("reportCounter")
    public StringRedisTemplate reportCounterStringRedisTemplate(
            @Qualifier("report") LettuceConnectionFactory connectionFactoryReport
    ) {
        var t = new StringRedisTemplate();
        t.setConnectionFactory(connectionFactoryReport);
        return t;
    }
    // key = 3 product 관련 동시성 해결
    @Bean
    @Qualifier("bid")
    LettuceConnectionFactory connectionFactoryBid() { return  redisConnectionFactory(3);}

    @Bean
    @Qualifier("bid")
    public RedisTemplate<String, Object> BidRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactoryBid());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }

    // value 값을 string 으로 받는 커스텀 bidtemplate
    @Bean
    @Qualifier("bidPrice")
    public RedisTemplate<String, String> bidNumberRedisTemplate(
        @Qualifier("bid") LettuceConnectionFactory connectionFactoryBid
    ) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactoryBid);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    // key = 4
    @Bean
    @Qualifier("chatState")
    LettuceConnectionFactory chatStateConnectionFactory() { return redisConnectionFactory(4); }


    @Bean
    @Qualifier("chatState")
    public StringRedisTemplate chatStateStringRedisTemplate(
            @Qualifier("chatState") LettuceConnectionFactory lettuceConnectionFactory
    ){
        StringRedisTemplate t = new StringRedisTemplate();
        t.setConnectionFactory(lettuceConnectionFactory);
        return t;
    }


    // DB5: 채팅 Pub/Sub 및 (현재 코드 기준) 상태/미읽음 키 저장 템플릿
    @Bean
    @Qualifier("chatRoom")
    LettuceConnectionFactory chatPubSubConnectionFactory() { return redisConnectionFactory(5); }


    @Bean
    @Qualifier("chatRoom")
    public RedisTemplate<String, Object> chatPubSubTemplate(
            @Qualifier("chatRoom") LettuceConnectionFactory lettuceConnectionFactory,
            @Qualifier("chatJson") GenericJackson2JsonRedisSerializer chatJson
    ){
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(lettuceConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);

        redisTemplate.setValueSerializer(chatJson);
        redisTemplate.setHashValueSerializer(chatJson);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }


    // 채팅 이벤트 토픽 (Pub/Sub)
    @Bean
    @Qualifier("chat")
    public ChannelTopic chatEventsTopic(){ return new ChannelTopic("chat:events"); }


    // 멀티 인스턴스용 Redis Pub/Sub 리스너 컨테이너
    @Bean
    public RedisMessageListenerContainer chatMessageListenerContainer(
            @Qualifier("chatRoom") LettuceConnectionFactory lettuceConnectionFactory,
            ChatSubscriber chatSubscriber,
            @Qualifier("chat") ChannelTopic chatTopic
    ){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(lettuceConnectionFactory);

        container.addMessageListener(chatSubscriber, chatTopic);
        return container;
    }

    @Bean
    @Qualifier("chatObjectMapper")
    public ObjectMapper chatObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    @Bean
    @Qualifier("chatJson")
    public GenericJackson2JsonRedisSerializer chatJson(
            @Qualifier("chatObjectMapper") ObjectMapper chatObjectMapper
    ) {
        return new GenericJackson2JsonRedisSerializer(chatObjectMapper);
    }

    @Bean
    @Qualifier("chatRoomPub")
    public StringRedisTemplate chatRoomPubStringRedisTemplate(
            @Qualifier("chatRoom") LettuceConnectionFactory chatRoomLettuce
    ) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(chatRoomLettuce);
        return redisTemplate;
    }

    // presence 전용 (상태 체크)
    @Bean
    @Qualifier("presence")
    LettuceConnectionFactory presenceConnectionFactory() {
        return redisConnectionFactory(6);
    }


    @Bean
    @Qualifier("presence")
    public StringRedisTemplate presenceStringRedisTemplate(
            @Qualifier("presence") LettuceConnectionFactory presenceConnectionFactory
    ) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(presenceConnectionFactory);
        return redisTemplate;
    }
}
