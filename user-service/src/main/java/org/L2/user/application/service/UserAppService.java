package org.L2.user.application.service;

import org.L2.common.R;
import org.L2.user.application.dto.*;
import org.L2.user.domain.model.User;
import org.L2.user.domain.service.JwtService;
import org.L2.user.domain.service.LoginService;
import org.L2.user.domain.service.RegisterService;
import org.L2.user.infrastructure.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserAppService {
    @Autowired
    private LoginService loginService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserMapper userMapper;

    public R register(RegisterRequest registerRequest) {
        if(registerRequest==null){
            return R.error("请求参数为空");
        }
        User user = new User();
        // 由于还没有配置手机和邮箱的验证码服务，所以暂时不扩展其他的登录方式
        user.setUsername(registerRequest.getUsername())
            .setPassword(registerRequest.getPassword());

        return registerService.register(user);
    }

    public R login(LoginRequest loginRequest) {
        User user = new User();
        user.setUsername(loginRequest.getUsername())
            .setPassword(loginRequest.getPassword());

    }

    public R changePassword(String newPassword) {

    }

    public R resetPassword(ResetPasswordRequest resetPasswordRequest) {
        User user = new User();
        user.setUsername(loginRequest.getUsername())
            .setPassword(loginRequest.getPassword());
    }

    public R updateProfile(UpdateProfileRequest updateProfileRequest) {
        User user = new User();
        user.setUsername(loginRequest.getUsername())
            .setPassword(loginRequest.getPassword());
    }
}
