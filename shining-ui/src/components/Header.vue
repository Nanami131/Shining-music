<template>
  <div class="header">
    <div class="logo">音乐社区</div>
    <div class="nav">
      <span class="nav-item" @click="goToDiscover">发现音乐</span>
      <span class="nav-item" @click="goToMyMusic">我的音乐</span>
      <span class="nav-item" @click="goToForum">讨论区</span>
    </div>
    <div class="actions">
      <template v-if="isLoggedIn">
        <img
            :src="userBase.avatarUrl || defaultAvatar"
            class="avatar"
            @click="goToProfile"
            alt="用户头像"
        />
        <span class="nickname">{{ userBase.nickName || '用户' }}</span>
        <button class="btn" @click="handleLogout">退出</button>
      </template>
      <template v-else>
        <button class="btn" @click="goToLogin">登录</button>
        <button class="btn" @click="goToRegister">注册</button>
      </template>
    </div>
  </div>
</template>

<script>
import userApi from '@/api/user';
import defaultAvatar from '@/assets/default-avatar.png'; // 默认头像

export default {
  name: 'Header',
  data() {
    return {
      userBase: JSON.parse(localStorage.getItem('userBase') || '{}'),
      isLoggedIn: !!localStorage.getItem('token'),
      defaultAvatar, // 默认头像路径
    };
  },
  watch: {
    '$route'() {
      this.updateLoginState();
    },
  },
  created() {
    this.updateLoginState();
    window.addEventListener('storage', this.updateLoginState);
  },
  beforeDestroy() {
    window.removeEventListener('storage', this.updateLoginState);
  },
  methods: {
    updateLoginState() {
      this.isLoggedIn = !!localStorage.getItem('token');
      this.userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    },
    goToLogin() {
      this.$router.push('/login');
    },
    goToRegister() {
      this.$router.push('/register');
    },
    goToProfile() {
      this.$router.push('/profile');
    },
    goToDiscover() {
      // 占位，无效点击
    },
    goToMyMusic() {
      // 占位，无效点击
    },
    goToForum() {
      // 占位，无效点击
    },
    async handleLogout() {
      try {
        const userId = this.userBase.id;
        const deviceCode = localStorage.getItem('deviceCode');
        const response = await userApi.logout(userId, deviceCode);
        if (response.data.passed) {
          localStorage.removeItem('token');
          localStorage.removeItem('deviceCode');
          localStorage.removeItem('userBase');
          this.updateLoginState();
          alert('退出成功');
          this.$router.push('/login');
        } else {
          alert('退出失败：' + response.data.message);
        }
      } catch (error) {
        alert('退出出错：' + error.message);
      }
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
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.logo {
  font-size: 24px;
  font-weight: bold;
}
.nav {
  display: flex;
  gap: 20px;
}
.nav-item {
  font-size: 16px;
  cursor: pointer;
  padding: 8px 16px;
  background: linear-gradient(to right, #6b7280, #9ca3af); /* 灰色渐变 */
  border-radius: 4px;
  transition: transform 0.2s;
}
.nav-item:hover {
  transform: scale(1.05);
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
}
.nickname {
  font-size: 16px;
  margin-right: 10px;
}
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #ff6b6b, #ff8e53);
  color: white;
  cursor: pointer;
  transition: transform 0.2s;
}
.btn:hover {
  transform: scale(1.05);
}
</style>