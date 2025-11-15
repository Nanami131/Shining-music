package org.L2.user.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.annotation.AutoFill;
import org.L2.common.constant.CommonConstants;
import org.L2.common.constant.OperationType;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
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

    @AutoFill(OperationType.UPDATE)
    public R updateUserProfile(User user) {
        user.setAvatarUrl("").setPassword("").setSalt("");
        try {
            userMapper.update(user);
            if (user.getNickName() != null || user.getSignature() != null) {
                stringRedisTemplate.delete("cache:userInfo:" + user.getId());
            }
            return R.success("用户信息修改成功");
        } catch (Exception e) {
            return R.error("用户信息修改失败" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public R resetPassword(Long id, String newPassword, String oldPassword) throws Exception {
        List<User> query = userMapper.query(new User().setId(id));
        if (query == null || query.isEmpty()) {
            return R.error("用户不存在");
        }
        String salt = query.get(0).getSalt();
        String realPassword = query.get(0).getPassword();
        oldPassword = BCrypt.hashpw(oldPassword, salt);
        if (!Objects.equals(realPassword, oldPassword)) {
            return R.error("原密码错误");
        }

        List<PasswordHistory> passwordHistories = passwordHistoryMapper.queryByUserId(id);
        for (PasswordHistory history : passwordHistories) {
            if (Objects.equals(history.getPassword(), BCrypt.hashpw(newPassword, history.getSalt()))) {
                return R.error("新密码不能与历史密码相同");
            }
        }

        String newSalt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(newPassword, newSalt);

        User user = new User()
                .setSalt(newSalt)
                .setPassword(hashedPassword)
                .setId(id);

        PasswordHistory passwordHistory = new PasswordHistory()
                .setPassword(hashedPassword)
                .setSalt(newSalt);
        return saveUserAndPassword(user, passwordHistory);
    }

    @Transactional(rollbackFor = Exception.class)
    public R saveUserAndPassword(User user, PasswordHistory passwordHistory) throws Exception {
        userMapper.update(user);
        passwordHistoryMapper.insert(passwordHistory.setUserId(user.getId()));
        return R.success("密码重置成功");
    }

    public R getUserBaseInfo(Long userId) {
        String key = "cache:userInfo:" + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                User user = objectMapper.readValue(json, User.class);
                return R.success("查询成功", user);
            } catch (JsonProcessingException e) {
                log.warn("反序列化用户基础信息缓存失败, userId={}", userId, e);
            }
        }
        List<User> query = userMapper.query(new User().setId(userId));
        if (query == null || query.isEmpty()) {
            return R.error("用户不存在");
        }
        User user = query.get(0);
        try {
            String jsonStr = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(key, jsonStr, CommonConstants.CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("写入用户基础信息缓存失败, userId={}", userId, e);
        }
        return R.success("查询成功", user);
    }

    public R getUserDetailsInfo(Long userId) {
        List<User> query = userMapper.query(new User().setId(userId));
        if (query == null || query.isEmpty()) {
            return R.error("用户不存在");
        }
        User user = query.get(0);
        return R.success("查询成功", user);
    }

    public R updateUserAvatar(Long id, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String fileName = FileNameGenerateService.defineNamePath(originalFilename, "/user/avator/", id, 5);
        String avatarUrl = minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + fileName;
        User user = new User().setId(id).setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        String result = simpleMinioService.uploadFile(file, fileName);
        if (!"上传成功".equals(result)) {
            return R.error(result);
        }
        try {
            userMapper.update(user);
        } catch (Exception e) {
            return R.error("数据库更新失败" + e.getMessage());
        }

        return R.success("头像修改成功", avatarUrl);
    }
}
