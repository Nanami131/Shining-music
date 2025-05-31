<template>
  <div class="header">
    <div class="logo">音乐社区</div>
    <div class="actions">
      <button class="btn" @click="goToLogin">登录</button>
      <button class="btn" @click="goToRegister">注册</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Header',
  methods: {
    goToLogin() {
      this.$router.push('/login');
    },
    goToRegister() {
      this.$router.push('/register');
    },
  },
};
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: linear-gradient(to right, #4facfe, #00f2fe); /* 渐变色 */
  color: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.logo {
  font-size: 24px;
  font-weight: bold;
}
.actions {
  display: flex;
  gap: 10px;
}
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #ff6b6b, #ff8e53); /* 按钮渐变 */
  color: white;
  cursor: pointer;
  transition: transform 0.2s;
}
.btn:hover {
  transform: scale(1.05);
}
</style>