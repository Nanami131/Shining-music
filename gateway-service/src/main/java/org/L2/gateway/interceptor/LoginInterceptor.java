package org.L2.gateway.interceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
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

    // 这里兼容带 /api 和不带 /api 的两种写法
    private static final Set<String> SKIP_PATHS = new HashSet<>(Arrays.asList(
            "/api/user/login",
            "/api/user/register",
            "/api/user/logout",
            "/user/login",
            "/user/register",
            "/user/logout"
    ));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {

        // 1. 预检请求 / OPTIONS 一律放行
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        // 2. 登录、注册、登出这些路径一律跳过校验
        String path = exchange.getRequest().getURI().getPath();
        for (String skip : SKIP_PATHS) {
            // 如果当前请求路径是以这些结尾的，就放行
            if (path.endsWith(skip)) {
                return chain.filter(exchange);
            }
        }

        // 3. 下面才是需要做 token 校验的路径
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
            String timestamp = claims.get("timestamp", String.class);
            if (userId == null) {
                return unauthorizedResponse(exchange, "无效的 Token");
            }

            String key = "jwt:" + userId + ":" + timestamp;
            String storedToken = redisTemplate.opsForValue().get(key);
            if (storedToken == null || !storedToken.equals(jwtToken)) {
                return unauthorizedResponse(exchange, "Token 已失效或不存在");
            }

            // 刷新 Token 有效期到 24 小时
            redisTemplate.expire(key, 24, TimeUnit.HOURS);

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
