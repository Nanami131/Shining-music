<template>
  <div class="register-container">
    <div class="register-box">
      <h2>注册</h2>
      <form @submit.prevent="handleRegister">
        <div class="form-item">
          <label>用户名</label>
          <input v-model="form.username" type="text" placeholder="请输入用户名" required />
        </div>
        <div class="form-item">
          <label>密码</label>
          <input v-model="form.password" type="password" placeholder="请输入密码" required />
        </div>
        <div class="form-item">
          <label>手机号</label>
          <input v-model="form.phone" type="tel" placeholder="请输入手机号" />
        </div>
        <div class="form-item">
          <label>邮箱</label>
          <input v-model="form.email" type="email" placeholder="请输入邮箱" />
        </div>
        <button type="submit" class="submit-btn">注册</button>
      </form>
      <p class="tip">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<script>
import userApi from '@/api/user';

export default {
  name: 'Register',
  data() {
    return {
      form: {
        username: '',
        password: '',
        phone: '',
        email: '',
      },
    };
  },
  methods: {
    async handleRegister() {
      try {
        const response = await userApi.register(this.form);
        if (response.data.passed) {
          alert('注册成功，请登录');
          this.$router.push('/login');
        } else {
          alert('注册失败：' + response.data.message);
        }
      } catch (error) {
        alert('注册出错：' + error.message);
      }
    },
  },
};
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(to bottom, #4facfe, #e0f7fa);
}
.register-box {
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