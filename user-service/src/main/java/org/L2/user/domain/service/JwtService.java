package org.L2.user.domain.service;

import org.L2.user.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtService {

    // TODO:配置
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // TODO
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 生成 JWT 并存储到 Redis
    public String generateToken(User user) {
        // 创建一个 Map 来存储 JWT 的自定义声明（claims）
        // claims 是 JWT 的 payload 部分，包含用户相关信息
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());

        // 使用 jjwt 库生成 JWT
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000)) // 过期时间
                .signWith(SignatureAlgorithm.HS512, secret) // 使用 HS512 算法和密钥签名
                .compact();

        String redisKey = "user-service:jwt:" + user.getId();
        redisTemplate.opsForValue().set(redisKey, token, expiration, TimeUnit.SECONDS);

        return token;
    }

    // 验证 JWT 是否有效
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            // 如果解析失败（例如签名错误、过期等），返回 null
            return null;
        }
    }

    // 从 Redis 获取指定用户的 JWT
    public String getTokenFromRedis(String userId) {
        String redisKey = "user-service:jwt:" + userId;
        return redisTemplate.opsForValue().get(redisKey);
    }

    // 删除 Redis 中的 JWT
    public void removeTokenFromRedis(String userId) {
        String redisKey = "user-service:jwt:" + userId;
        redisTemplate.delete(redisKey);
    }
}