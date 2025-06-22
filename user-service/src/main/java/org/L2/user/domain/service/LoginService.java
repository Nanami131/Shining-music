package org.L2.user.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.user.domain.model.User;
import org.L2.user.infrastructure.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class LoginService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    public R login(User user){
        if(user.getUsername() == null || user.getUsername().isEmpty()) {
            return R.error("用户名为空");
        }
        // 未来要扩展其他登录方式
        if(user.getPassword() != null && !user.getPassword().isEmpty()) {
            return loginWithPassword(new User().setUsername(user.getUsername()),user.getPassword());
        }
        return R.error("没有可用的登录凭证");
    }

    public R loginWithPassword(User user, String password) {
        List<User> query = userMapper.query(user);
        if(query == null||query.isEmpty()) {
            return R.error("用户不存在");
        }
        String salt = query.get(0).getSalt();
        String realPassword = query.get(0).getPassword();
        String hashedPassword = BCrypt.hashpw(password, salt);
        if(Objects.equals(hashedPassword, realPassword)){
            return R.success("登录成功",query.get(0));
        }
        return R.error("密码错误");
    }

    public void logout(Long userId, String deviceCode) throws Exception {
        String redisKey = "jwt:" + userId+":"+deviceCode;
        stringRedisTemplate.delete(redisKey);
    }
}
