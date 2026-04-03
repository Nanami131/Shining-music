package org.L2.user.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.L2.common.R;
import org.L2.user.domain.model.User;
import org.L2.user.infrastructure.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService 单元测试")
class LoginServiceTest {

    @InjectMocks
    private LoginService loginService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("用户名为null — 返回错误")
        void nullUsername() {
            User user = new User().setUsername(null).setPassword("123");
            R result = loginService.login(user);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("用户名为空"));
        }

        @Test
        @DisplayName("用户名为空字符串 — 返回错误")
        void emptyUsername() {
            User user = new User().setUsername("").setPassword("123");
            R result = loginService.login(user);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("密码为null — 返回没有可用的登录凭证")
        void nullPassword() {
            User user = new User().setUsername("testuser").setPassword(null);
            R result = loginService.login(user);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("没有可用的登录凭证"));
        }

        @Test
        @DisplayName("密码为空字符串 — 返回没有可用的登录凭证")
        void emptyPassword() {
            User user = new User().setUsername("testuser").setPassword("");
            R result = loginService.login(user);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("没有可用的登录凭证"));
        }

        @Test
        @DisplayName("正常登录 — 委托给loginWithPassword")
        void normalLogin() {
            String salt = BCrypt.gensalt();
            String hashed = BCrypt.hashpw("password123", salt);
            User dbUser = new User().setId(1L).setUsername("testuser")
                    .setPassword(hashed).setSalt(salt);

            when(userMapper.query(any(User.class))).thenReturn(List.of(dbUser));

            User loginUser = new User().setUsername("testuser").setPassword("password123");
            R result = loginService.login(loginUser);
            assertTrue(result.getPassed());
            assertEquals("登录成功", result.getMessage());
        }
    }

    @Nested
    @DisplayName("loginWithPassword()")
    class LoginWithPasswordTests {

        @Test
        @DisplayName("用户不存在 — 返回错误")
        void userNotFound() {
            when(userMapper.query(any(User.class))).thenReturn(Collections.emptyList());

            R result = loginService.loginWithPassword(new User().setUsername("ghost"), "pwd");
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("用户不存在"));
        }

        @Test
        @DisplayName("查询返回null — 返回用户不存在")
        void queryReturnsNull() {
            when(userMapper.query(any(User.class))).thenReturn(null);

            R result = loginService.loginWithPassword(new User().setUsername("ghost"), "pwd");
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("用户不存在"));
        }

        @Test
        @DisplayName("密码正确 — 登录成功")
        void correctPassword() {
            String salt = BCrypt.gensalt();
            String hashed = BCrypt.hashpw("correct", salt);
            User dbUser = new User().setId(1L).setUsername("user1")
                    .setPassword(hashed).setSalt(salt);

            when(userMapper.query(any(User.class))).thenReturn(List.of(dbUser));

            R result = loginService.loginWithPassword(new User().setUsername("user1"), "correct");
            assertTrue(result.getPassed());
            assertNotNull(result.getData());
        }

        @Test
        @DisplayName("密码错误 — 返回密码错误")
        void wrongPassword() {
            String salt = BCrypt.gensalt();
            String hashed = BCrypt.hashpw("correct", salt);
            User dbUser = new User().setId(1L).setUsername("user1")
                    .setPassword(hashed).setSalt(salt);

            when(userMapper.query(any(User.class))).thenReturn(List.of(dbUser));

            R result = loginService.loginWithPassword(new User().setUsername("user1"), "wrong");
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("密码错误"));
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("正常登出 — 删除Redis中的JWT键")
        void normalLogout() throws Exception {
            when(stringRedisTemplate.delete("jwt:1:12345")).thenReturn(true);
            loginService.logout(1L, "12345");
            verify(stringRedisTemplate).delete("jwt:1:12345");
        }
    }
}
