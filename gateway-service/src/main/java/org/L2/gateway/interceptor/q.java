package org.L2.gateway.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }//预检请求直接放行

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已失效");
            return false;
        }

        String jwtToken = token.substring("Bearer ".length());

        try {
//            String userId = JwtUtil.getUserIdFromToken(jwtToken);
//            if (userId == null) {
//                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的 Token");
//                return false;
//            }
//            String storedToken = redisTemplate.opsForValue().get("token:" + userId);
//
//            if (storedToken == null || !storedToken.equals(jwtToken)) {
//                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 已失效或不存在");
//                return false;
//            }

            // 刷新 Token 有效期到 24 小时
            redisTemplate.expire("token:" + userId, 24, TimeUnit.HOURS);

            return true;
        } catch (JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的 Token: " + e.getMessage());
            return false;
        }
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {


    }
}