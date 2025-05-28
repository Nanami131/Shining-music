package org.L2.user.application.service;

import org.L2.common.R;
import org.L2.user.application.dto.*;
import org.L2.user.domain.model.User;
import org.L2.user.domain.service.JwtService;
import org.L2.user.domain.service.LoginService;
import org.L2.user.domain.service.RegisterService;
import org.L2.user.domain.service.UserProfileService;
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
    private UserProfileService userProfileService;

    public R register(RegisterRequest registerRequest) {
        if(registerRequest==null){
            return R.error("请求参数为空");
        }
        User user = new User();
        // 还没有配置手机和邮箱的验证码服务
        user.setUsername(registerRequest.getUsername())
            .setPassword(registerRequest.getPassword())
            .setEmail(registerRequest.getEmail())
            .setPhone(registerRequest.getPhone());

        try {
            return registerService.register(user);
        } catch (Exception e) {
            return R.error("数据库操作失败"+e.getMessage());
        }
    }

    public R login(LoginRequest loginRequest) {
        User user = new User();
        // 由于还没有配置手机和邮箱的验证码服务，所以暂时不扩展其他的登录方式
        user.setUsername(loginRequest.getUsername())
            .setPassword(loginRequest.getPassword())
            .setEmail(loginRequest.getEmail())
            .setPhone(loginRequest.getPhone());
        R result=loginService.login(user);
        if(result.getPassed()==false){
            return result;
        }
        // TODO:设置token
        return result;
    }

    public R resetPassword(ResetPasswordRequest resetPasswordRequest) {
        try {
            return userProfileService.resetPassword(resetPasswordRequest.getId(),
                               resetPasswordRequest.getNewPassword(),
                               resetPasswordRequest.getOldPassword());
        } catch (Exception e) {
            return R.error("数据库操作失败"+e.getMessage());
        }
    }

    public R updateProfile(UpdateProfileRequest updateProfileRequest) {
        User user = new User();
        BeanUtils.copyProperties(updateProfileRequest, user);
        return userProfileService.updateUserProfile(user);
    }
}
