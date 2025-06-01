package org.L2.user.controller;

import org.L2.common.R;
import org.L2.user.UserApplication;
import org.L2.user.application.dto.*;
import org.L2.user.application.service.UserAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserAppService userAppService;
    /**
     * 用户注册
     * @param registerRequest 注册信息
     * @return
     */
    @PostMapping("/register")
    public R register(@RequestBody RegisterRequest registerRequest) {
        return userAppService.register(registerRequest);
    }

    /**
     * 用户登录
     * @param loginRequest 登录信息
     * @return
     */
    @PostMapping("/login")
    public R login(@RequestBody LoginRequest loginRequest) {
        return userAppService.login(loginRequest);
    }

//    /**
//     * 修改密码
//     * @param newPassword 新密码
//     * @return
//     */
//    @PostMapping("/change-password")
//    public R changePassword(@RequestBody String newPassword) {
//        return userAppService.changePassword(newPassword);
//    }

    /**
     * 重置密码
     * @param resetPasswordRequest 密码信息
     * @return
     */
    @PostMapping("/reset-password")
    public R resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        return userAppService.resetPassword(resetPasswordRequest);
    }

    /**
     * 获取用户基本信息
     * @param userId 用户id
     * @return
     */
    @GetMapping("/info")
    public R getUserBaseInfo(@RequestParam("userId") Long userId) {
        return userAppService.getUserBaseInfo(userId);
    }

    /**
     * 获取用户详细信息
     * @param userId 用户id
     * @return
     */
    @GetMapping("/update-profile")
    public R getUserDetailsInfo(@RequestParam("userId") Long userId) {
        return userAppService.getUserDetailsInfo(userId);
    }


    /**
     * 更改个人资料
     * @param updateProfileRequest 个人资料信息
     * @return
     */
    @PostMapping("/update-profile")
    public R updateProfile(@RequestBody UpdateProfileRequest updateProfileRequest) {
        return userAppService.updateProfile(updateProfileRequest);
    }

    /**
     * 更新头像
     *
     * @return
     */
    @PostMapping("/update-avatar")
    public R updateAvatar(@RequestParam("id") Long id,
                          @RequestParam("avatarFile") MultipartFile avatarFile,
                          @RequestParam("md5") String md5) {
        return userAppService.updateAvatar(id, avatarFile, md5);
    }


    /**
     * 退出登录
     * @param userId 用户id
     * @param deviceCode 设备码
     * @return
     */
    @PostMapping("/logout")
    public R logout(@RequestParam("userId") Long userId, @RequestParam("deviceCode") String deviceCode) {
        return userAppService.logout(userId, deviceCode);
    }
    /**
     * 项目初期测试前后端时使用的接口，已废弃
     * @return
     */
    @Deprecated
    @GetMapping("/test")
    public R test() {
        return R.success("Test successful", "Hello from user-service!");
    }
}