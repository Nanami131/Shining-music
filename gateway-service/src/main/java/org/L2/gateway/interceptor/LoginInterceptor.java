package org.L2.gateway.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoginInterceptor implements GlobalFilter, Ordered {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().toString())) {
            return chain.filter(exchange);
        } // 预检请求直接放行

        // TODO: 登录校验，刷新 TOKEN
        System.out.println(1);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0; // 设置过滤器优先级
    }
}