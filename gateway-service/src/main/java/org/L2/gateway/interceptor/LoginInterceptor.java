package org.L2.gateway.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class LoginInterceptor implements GlobalFilter, Ordered {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Value("${jwt.secret}")
    private String secret;

    // 不需要登录校验的路径集合，目前是登录注册，未来可扩展
    private static final Set<String> SKIP_PATHS = new HashSet<>(Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/logout"
    ));
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().toString())) {
            return chain.filter(exchange);
        }

        // 获取请求路径
        String path = exchange.getRequest().getURI().getPath();

        // 检查是否为需要跳过的路径
        if (SKIP_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "未登录或登录已失效");
        }
        String jwtToken = token.substring("Bearer ".length());
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(jwtToken)
                    .getBody();

            String userId = claims.get("userId", String.class);
            String timestamp = claims.get("timestamp",String.class);
            if (userId == null) {
                return unauthorizedResponse(exchange, "无效的 Token");
            }
            String storedToken = redisTemplate.opsForValue().get("jwt:" + userId+":"+timestamp);
            if (storedToken == null || !storedToken.equals(jwtToken)) {
                return unauthorizedResponse(exchange, "Token 已失效或不存在");
            }
            // 刷新 Token 有效期到 24 小时
            // 令牌设计的过期时间是一个月
            redisTemplate.expire("jwt:" + userId + ":" + timestamp, 24, TimeUnit.HOURS);

            return chain.filter(exchange);
        } catch (JwtException e) {
            return unauthorizedResponse(exchange, "无效的 Token: " + e.getMessage());
        }
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}