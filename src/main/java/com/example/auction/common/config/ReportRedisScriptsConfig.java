package com.example.auction.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class ReportRedisScriptsConfig {

    /**
     * pending에서 n만큼 빼고(최소 0), accepted에 n만큼 더하는 원자 이동
     * KEYS[1]=pendingKey, KEYS[2]=acceptedKey, ARGV[1]=n
     * return: 이동 후 pending 값
     */
    @Bean("movePendingToAcceptedScript")
    public DefaultRedisScript<Long> movePendingToAcceptedScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
            local pending = redis.call('GET', KEYS[1])
            if not pending then pending = '0' end
            local n = tonumber(ARGV[1])
            local p = tonumber(pending)
            if p < n then n = p end
            if n > 0 then
              redis.call('DECRBY', KEYS[1], n)
              redis.call('INCRBY', KEYS[2], n)
            end
            return tonumber(redis.call('GET', KEYS[1]) or '0')
        """);
        return script;
    }

    /**
     * pending에서 n만큼 빼되 음수 방지(거절 처리용)
     * KEYS[1]=pendingKey, ARGV[1]=n
     * return: 감소 후 pending 값
     */
    @Bean("safeDecrPendingScript")
    public DefaultRedisScript<Long> safeDecrPendingScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText("""
            local pending = redis.call('GET', KEYS[1])
            if not pending then pending = '0' end
            local n = tonumber(ARGV[1])
            local p = tonumber(pending)
            if n > p then n = p end
            if n > 0 then
              redis.call('DECRBY', KEYS[1], n)
            end
            return tonumber(redis.call('GET', KEYS[1]) or '0')
        """);
        return script;
    }
}
