package org.L2.user.domain.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.L2.common.constant.CommonConstants;
import org.L2.user.domain.model.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 生成 JWT 并存储到 Redis
    public String[] generateToken(String userId) throws Exception {
        // 生成时间戳
        // 这个时间戳的目的是将用户不同设备的登录做区分 可以扩展功能让用户管理设备登录状态
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        // 生成 JWT 载荷
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("timestamp", timestamp);
        // 使用 jjwt 库生成 JWT
        String token = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000)) // 过期时间
                .signWith(SignatureAlgorithm.HS512, secret) // 使用 HS512 算法和密钥签名
                .compact();

        String redisKey = "jwt:" + userId+":"+timestamp;
        stringRedisTemplate.opsForValue().set(redisKey, token, CommonConstants.CACHE_TTL_HOURS, TimeUnit.HOURS);

        return new String[]{token, timestamp};
    }

}