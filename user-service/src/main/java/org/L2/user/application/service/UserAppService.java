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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 用户注册
     * @param registerRequest
     * @return
     */
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

    /**
     * 用户登录
     * @param loginRequest
     * @return
     */
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
        user=(User)result.getData();

        UserBaseDTO userBaseDTO = new UserBaseDTO();
        BeanUtils.copyProperties(user,userBaseDTO);
        UserLoginDTO userLoginDTO = new UserLoginDTO().setUserBaseDTO(userBaseDTO);
        try {
            String[] token = jwtService.generateToken(user.getId().toString());
            userLoginDTO.setToken(token[0]);
            userLoginDTO.setDeviceCode(token[1]);
        } catch (Exception e) {
            return R.error("生成token失败"+e.getMessage());
        }

        return R.success("登录成功",userLoginDTO);
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

    public R getUserBaseInfo(Long userId) {
        R result = userProfileService.getUserBaseInfo(userId);
        if(result.getPassed()==false){
            return result;
        }
        UserBaseDTO userBaseDTO = new UserBaseDTO();
        BeanUtils.copyProperties(result.getData(),userBaseDTO);
        return R.success("获取用户信息成功",userBaseDTO);
    }

    public R getUserDetailsInfo(Long userId) {
        R result = userProfileService.getUserDetailsInfo(userId);
        if(result.getPassed()==false){
            return result;
        }
        UserDetailsDTO userDetailsDTO = new UserDetailsDTO();
        BeanUtils.copyProperties(result.getData(),userDetailsDTO);
        return R.success("获取用户详情成功",userDetailsDTO);
    }

    public  R logout(Long userId, String deviceCode) {
        try {
            loginService.logout(userId, deviceCode);
            return R.success("退出登录成功");
        } catch (Exception e) {
            return R.error("退出登录失败"+e.getMessage());
        }
    }

    public R updateAvatar(Long id, MultipartFile avatarFile,String md5) {
        // MD5校验以后再写
        return userProfileService.updateUserAvatar(id,avatarFile);
    }
}
