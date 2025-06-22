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
    public R register(User user) throws Exception {
        if(user.getUsername() == null || user.getUsername().isBlank()) {

            return R.error("用户名为空");
        }
        if(user.getPassword() == null || user.getPassword().isBlank()) {
            return R.error("密码为空");
        }
        if(user.getEmail() == null && user.getPhone() == null){
            return R.error("邮箱和手机号码不能均");
        }
        if(!isValidEmail(user.getEmail())) {
            return R.error("邮箱格式不正确");
        }
        if(!isValidPhoneNumber(user.getPhone())){
            return R.error("手机号码格式不正确");
        }
        List<User> query = userMapper.query(new User().setUsername(user.getUsername()));
        if(query!=null&&!query.isEmpty()){
            return R.error("用户名已存在");
        }

        String salt = BCrypt.gensalt(); // 生成随机盐值
        String hashedPassword = BCrypt.hashpw(user.getPassword(), salt);//加密

        user.setSalt(salt)
            .setPassword(hashedPassword);

        PasswordHistory passwordHistory = new PasswordHistory()
                .setPassword(hashedPassword)
                .setSalt(salt);
        // TODO： 默认头像、默认昵称等
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
     *  校验中国大陆手机号
     * @param phone
     * @return
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        // 中国大陆手机号：11位数字，以1开头，第二位是3,4,5,6,7,8,9
        String phoneRegex = "^1[3-9]\\d{9}$";
        return phone.matches(phoneRegex);
    }

    /**
     *  校验邮箱
     * @param email
     * @return
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
}
