package org.L2.user.application.service;

import org.L2.common.R;
import org.L2.user.application.dto.*;
import org.L2.user.domain.model.User;
import org.L2.user.domain.service.JwtService;
import org.L2.user.domain.service.LoginService;
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
    private UserMapper userMapper;

    public R register(RegisterRequest registerRequest) {
        if(registerRequest==null){
            return R.error("请求参数为空");
        }
        R result=loginService.register(registerRequest);
        if(result.getPassed()!=true){
            return result;
        }
        User user=(User)result.getData();
        try {
            userMapper.save(user);
        } catch (Exception e) {
            return R.error("数据库操作失败");
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        return R.success("注册成功",userDTO);
    }

    public R login(LoginRequest loginRequest) {
    }

    public R changePassword(String newPassword) {
    }

    public R resetPassword(ResetPasswordRequest resetPasswordRequest) {
    }

    public R updateProfile(UpdateProfileRequest updateProfileRequest) {
    }
}
