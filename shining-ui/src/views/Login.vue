<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="logo-circle">S</div>
        <h2>欢迎回来</h2>
        <p class="auth-subtitle">登录 Shining Music 继续你的音乐旅程</p>
      </div>
      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="field">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
            required
          />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <div class="input-group">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              required
            />
            <button type="button" class="eye-btn" @click="showPassword = !showPassword" tabindex="-1">
              <svg v-if="!showPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>
        <button type="submit" class="submit-btn">登录</button>
      </form>
      <p class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>

<script>
import userApi from '@/api/user';

export default {
  name: 'Login',
  data() {
    return {
      form: { username: '', password: '' },
      showPassword: false,
    };
  },
  methods: {
    async handleLogin() {
      try {
        const response = await userApi.login(this.form);
        if (response.data.passed) {
          const { userBaseDTO, token, deviceCode } = response.data.data;
          localStorage.setItem('token', token || '');
          localStorage.setItem('deviceCode', deviceCode || '');
          localStorage.setItem('userBase', JSON.stringify(userBaseDTO || {}));
          window.__LOGIN_EXPIRED_ALERT_SHOWN__ = false;
          window.dispatchEvent(new Event('userBaseUpdated'));
          alert('登录成功');
          this.$router.push('/');
        } else {
          alert('登录失败：' + response.data.message);
        }
      } catch (error) {
        alert('登录出错：' + error.message);
      }
    },
  },
};
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
}
.auth-page::before {
  content: '';
  position: absolute;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(79,172,254,0.15) 0%, transparent 70%);
  top: -100px;
  right: -100px;
  border-radius: 50%;
}
.auth-page::after {
  content: '';
  position: absolute;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(0,242,254,0.1) 0%, transparent 70%);
  bottom: -80px;
  left: -80px;
  border-radius: 50%;
}
.auth-card {
  position: relative;
  z-index: 1;
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 16px;
  padding: 40px 32px;
  width: 100%;
  max-width: 380px;
  margin: 16px;
}
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-circle {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #4facfe, #00f2fe);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: white;
  margin: 0 auto 16px;
  box-shadow: 0 8px 24px rgba(79,172,254,0.3);
}
.auth-header h2 {
  color: #f1f5f9;
  font-size: 22px;
  margin-bottom: 6px;
}
.auth-subtitle {
  color: #94a3b8;
  font-size: 14px;
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.field label {
  display: block;
  color: #cbd5e1;
  font-size: 13px;
  margin-bottom: 6px;
  font-weight: 500;
}
.field input {
  display: block;
  width: 100%;
  padding: 12px 14px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 10px;
  color: #f1f5f9;
  font-size: 15px;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}
.field input::placeholder {
  color: #64748b;
}
.field input:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgba(79,172,254,0.15);
}
.input-group {
  position: relative;
}
.input-group input {
  padding-right: 44px;
}
.eye-btn {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
}
.eye-btn:hover {
  color: #94a3b8;
}
.submit-btn {
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #4facfe, #00f2fe);
  color: white;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  box-shadow: 0 4px 16px rgba(79,172,254,0.3);
  margin-top: 4px;
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(79,172,254,0.4);
}
.submit-btn:active {
  transform: translateY(0);
}
.auth-footer {
  text-align: center;
  margin-top: 24px;
  color: #94a3b8;
  font-size: 14px;
}
.auth-footer a {
  color: #4facfe;
  text-decoration: none;
  font-weight: 500;
}
.auth-footer a:hover {
  text-decoration: underline;
}
</style>
