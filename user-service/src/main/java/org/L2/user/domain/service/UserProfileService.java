package org.L2.user.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
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

import java.util.List;
import java.util.Objects;

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
        if(!Objects.equals(realPassword,oldPassword)) {
            return R.error("旧密码错误");
        }
        List<PasswordHistory> passwordHistories = passwordHistoryMapper.queryByUserId(id);
        for(PasswordHistory historyPassword:passwordHistories) {
            if(Objects.equals(historyPassword.getPassword(),newPassword)){
                return R.error("新密码不能与旧密码相同");
            }
        }
        // 新密码没有问题，开始加盐加密
        String newSalt = BCrypt.gensalt(); // 生成随机盐值
        String hashedPassword = BCrypt.hashpw(newPassword, newSalt);//加密
        User user=new User().setSalt(newSalt)
             .setPassword(hashedPassword);

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
        // 插入用户信息
        Long id=userMapper.insert(user);

        // 插入密码历史记录
        passwordHistoryMapper.insert(passwordHistory.setUserId(id));

        return R.success("注册成功");
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
                return R.success("查询成功"+user);
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
            stringRedisTemplate.opsForValue().set("cache:userInfo:" + userId, jsonStr, 60*60*24);
        } catch (JsonProcessingException e) {
            // TODO:日志记录；缓存存入失败不影响返回
        }
        return R.success("查询成功"+user);
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
        return R.success("查询成功"+user);
    }
}
