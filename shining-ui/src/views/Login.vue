<template>
  <div class="login-container">
    <div class="login-box">
      <h2>登录</h2>
      <form @submit.prevent="handleLogin">
        <div class="form-item">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="请输入用户名" required />
        </div>
        <div class="form-item">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" required />
        </div>
        <button type="submit" class="submit-btn">登录</button>
      </form>
      <p class="tip">没有账号？<router-link to="/register">去注册</router-link></p>
    </div>
  </div>
</template>

<script>
import userApi from '@/api/user';

export default {
  name: 'Login',
  data() {
    return {
      form: {
        username: '',
        password: '',
      },
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
          // 新登录成功，重置“登录已过期”提示标记
          window.__LOGIN_EXPIRED_ALERT_SHOWN__ = false;
          // 通知头部组件以及底部栏更新登录状态
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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(to bottom, #4facfe, #e0f7fa);
}
.login-box {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.form-item {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
}
input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}
.submit-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  font-size: 16px;
  cursor: pointer;
}
.submit-btn:hover {
  opacity: 0.9;
}
.tip {
  text-align: center;
  margin-top: 10px;
}
.tip a {
  color: #4facfe;
  text-decoration: none;
}
</style>
