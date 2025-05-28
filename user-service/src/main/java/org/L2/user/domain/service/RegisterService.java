package org.L2.user.domain.service;

import org.L2.common.R;
import org.L2.user.domain.model.PasswordHistory;
import org.L2.user.domain.model.User;
import org.L2.user.infrastructure.mapper.PasswordHistoryMapper;
import org.L2.user.infrastructure.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegisterService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordHistoryMapper passwordHistoryMapper;


    /**
     *  用户注册
     * @param user
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public R register(User user) {
        if(user.getUsername() == null || "".equals(user.getUsername())) {
            return R.error("用户名为空");
        }
        if(user.getPassword() == null || "".equals(user.getPassword())) {
            return R.error("密码为空");
        }
        List<User> query = userMapper.query(new User().setUsername(user.getUsername()));
        if(query!=null&&!query.isEmpty()){
            return R.error("用户名已存在");
        }

        String salt = BCrypt.gensalt(); // 生成随机盐值
        String hashedPassword = BCrypt.hashpw(user.getPassword(), salt);

        user.setSalt(salt)
            .setPassword(hashedPassword);

        PasswordHistory passwordHistory = new PasswordHistory()
                .setPassword(hashedPassword)
                .setSalt(salt);
        try {
            return saveUserAndHistory(user,passwordHistory);
        } catch (Exception e) {
            return R.error("注册失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public R saveUserAndHistory(User user, PasswordHistory passwordHistory) throws Exception {
        // 插入用户信息
        Long id=userMapper.insert(user);

        // 插入密码历史记录
        passwordHistoryMapper.insert(passwordHistory.setUserId(id));

        return R.success("注册成功");
    }
}
