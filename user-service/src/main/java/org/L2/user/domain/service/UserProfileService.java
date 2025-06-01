package org.L2.user.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.common.constant.CommonConstants;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.user.application.dto.UserBaseDTO;
import org.L2.user.domain.model.PasswordHistory;
import org.L2.user.domain.model.User;
import org.L2.user.infrastructure.mapper.PasswordHistoryMapper;
import org.L2.user.infrastructure.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class UserProfileService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordHistoryMapper passwordHistoryMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private SimpleMinioService simpleMinioService;

    public R updateUserProfile(User user) {
        // 在这里再校验一次，这个接口只修改用户个性化信息
        user.setAvatarUrl("").setPassword("").setSalt("");
        try {
            userMapper.update(user);
            if(user.getNickName()!=null||user.getSignature()!=null){
                // 缓存需要更新
                stringRedisTemplate.delete("cache:userInfo:"+user.getId());
            }
            return R.success("用户信息修改成功");
        } catch (Exception e) {
            return R.error("用户信息修改失败："+e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public R resetPassword(Long id, String newPassword ,String oldPassword) throws Exception {
        List<User> query = userMapper.query(new User().setId(id));
        if(query == null||query.isEmpty()) {
            return R.error("用户不存在");
        }
        String salt = query.get(0).getSalt();
        String realPassword = query.get(0).getPassword();
        oldPassword = BCrypt.hashpw(oldPassword, salt);
        if(!Objects.equals(realPassword,oldPassword)) {
            return R.error("旧密码错误");
        }


        List<PasswordHistory> passwordHistories = passwordHistoryMapper.queryByUserId(id);
        for(int i=0;i<passwordHistories.size();i++) {
            if(Objects.equals(passwordHistories.get(i).getPassword(),BCrypt.hashpw(newPassword, passwordHistories.get(i).getSalt()))){
                // 应该用旧的盐值加密，然后判断是否新密码是否和旧密码相同
                return R.error("新密码不能与旧密码相同");
            }
        }

        // 新密码没有问题
        String newSalt = BCrypt.gensalt(); // 生成随机盐值
        String hashedPassword = BCrypt.hashpw(newPassword, newSalt);//加密

        User user=new User().setSalt(newSalt)
             .setPassword(hashedPassword)
             .setId(id);

        PasswordHistory passwordHistory = new PasswordHistory()
            .setPassword(hashedPassword)
            .setSalt(newSalt);
        return saveUserAndPassword(user,passwordHistory);
    }

    /**
     *  保存用户信息和密码历史记录
     * @param user
     * @param passwordHistory
     * @return
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public R saveUserAndPassword(User user, PasswordHistory passwordHistory) throws Exception {
        // 更新用户密码
        userMapper.update(user);

        // 插入密码历史记录
        passwordHistoryMapper.insert(passwordHistory.setUserId(user.getId()));

        return R.success("重置密码成功");
    }

    /**
     * 获取用户基本信息
     * @param userId
     * @return
     */
    public R getUserBaseInfo(Long userId) {
        String json = stringRedisTemplate.opsForValue().get("cache:userInfo:" + userId);
        if(json!=null){
            try {
                User user = objectMapper.readValue(json, User.class);
                return R.success("查询成功",user);
            } catch (JsonProcessingException e) {
                // TODO:日志记录
            }
        }
        List<User> query = userMapper.query(new User().setId(userId));
        if(query == null||query.isEmpty()) {
            return R.error("用户不存在");
        }
        User user = query.get(0);
        try {
            String jsonStr = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set("cache:userInfo:" + userId, jsonStr,  CommonConstants.CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            // TODO:日志记录；缓存存入失败不影响返回
        }
        return R.success("查询成功",user);
    }

    /**
     * 获取用户详细信息
     * @param userId
     * @return
     */
    public R getUserDetailsInfo(Long userId) {
        List<User> query = userMapper.query(new User().setId(userId));
        if(query == null||query.isEmpty()) {
            return R.error("用户不存在");
        }
        User user = query.get(0);
        return R.success("查询成功",user);
    }

    public R updateUserAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName= FileNameGenerateService.defineNamePath(originalFilename,"/user/avator/",id,5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        User user = new User().setId(id).setAvatarUrl(avatarUrl);
        String s = simpleMinioService.uploadFile(file,fileName);
        if(!s.equals("上传成功")){
            return R.error(s);
        }
        try {
            userMapper.update(user);
        } catch (Exception e) {
            return R.error("数据库操作失败："+e.getMessage());
        }

        return R.success("头像修改成功",avatarUrl);
    }
}
