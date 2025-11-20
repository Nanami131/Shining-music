<template>
  <div class="header">
    <div class="logo">Shining</div>
    <div class="nav">
      <span class="nav-item" @click="goToDiscover">发现音乐</span>
      <span class="nav-item" @click="goToMyMusic">我的音乐</span>
      <span class="nav-item" @click="goToForum">讨论区</span>
      <span class="nav-item" @click="goToSingers">歌手</span>
      <span class="nav-item" @click="goToSongs">歌曲</span>
      <span class="nav-item" @click="goToPlaylists">歌单</span>
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
import defaultAvatar from '@/assets/default-avatar.png';

export default {
  name: 'Header',
  data() {
    return {
      userBase: JSON.parse(localStorage.getItem('userBase') || '{}'),
      isLoggedIn: !!localStorage.getItem('token'),
      defaultAvatar,
    };
  },
  created() {
    this.updateLoginState();
    window.addEventListener('storage', this.updateLoginState);
    window.addEventListener('userBaseUpdated', this.updateLoginState); // 监听更新事件
  },
  beforeDestroy() {
    window.removeEventListener('storage', this.updateLoginState);
    window.removeEventListener('userBaseUpdated', this.updateLoginState);
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
      this.$router.push('/');
    },
    goToMyMusic() {
      this.$router.push('/my-music');
    },
    goToForum() {
      this.$router.push('/forum');
    },
    goToSingers() {
      this.$router.push('/singers');
    },
    goToSongs() {
      this.$router.push('/songs');
    },
    goToPlaylists() {
      this.$router.push('/playlists');
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
          // 通知全局（包括底部栏）用户已退出
          window.dispatchEvent(new Event('userBaseUpdated'));
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
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 24px;
  background:
      radial-gradient(circle at 0% 0%, rgba(186, 230, 253, 0.95), transparent 55%),
      radial-gradient(circle at 100% 0%, rgba(244, 219, 255, 0.95), transparent 55%),
      linear-gradient(90deg, rgba(15, 23, 42, 0.92), rgba(15, 23, 42, 0.78));
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.35);
  color: #e5f0ff;
}
.logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #fdf2ff;
  text-shadow:
      0 0 8px rgba(244, 219, 255, 0.9),
      0 0 16px rgba(56, 189, 248, 0.7);
}
.nav {
  display: flex;
  gap: 14px;
  padding: 4px 10px;
  border-radius: 999px;
  background:
      radial-gradient(circle at 0 0, rgba(96, 165, 250, 0.55), transparent 55%),
      radial-gradient(circle at 100% 100%, rgba(45, 212, 191, 0.45), transparent 55%);
  backdrop-filter: blur(18px);
  box-shadow: 0 0 22px rgba(56, 189, 248, 0.65);
}
.nav-item {
  position: relative;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 999px;
  color: #f9fafb;
  transition: color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}
.nav-item::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background:
      radial-gradient(circle at 0 0, rgba(129, 230, 217, 0.95), transparent 55%),
      radial-gradient(circle at 100% 100%, rgba(96, 165, 250, 0.95), transparent 55%);
  opacity: 0;
  transition: opacity 0.25s ease, transform 0.25s ease;
  z-index: -1;
}
.nav-item::after {
  content: '';
  position: absolute;
  left: 18%;
  right: 18%;
  bottom: -4px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(56, 189, 248, 0.0), rgba(96, 165, 250, 0.9), rgba(251, 113, 133, 0.0));
  opacity: 0;
  transform: scaleX(0.6);
  transition: opacity 0.25s ease, transform 0.25s ease;
  pointer-events: none;
}
.nav-item:hover::before {
  opacity: 1;
  transform: scale(1.06);
}
.nav-item:hover::after {
  opacity: 1;
  transform: scaleX(1);
}
.nav-item:hover {
  color: #0f172a;
  transform: translateY(-1px);
  box-shadow: 0 0 18px rgba(59, 130, 246, 0.75);
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  object-fit: cover;
  cursor: pointer;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.55);
}
.nickname {
  font-size: 14px;
  margin-right: 4px;
  color: #e5f0ff;
}
.btn {
  padding: 6px 14px;
  border: 1px solid rgba(248, 250, 252, 0.55);
  border-radius: 999px;
  background: radial-gradient(circle at 0% 0%, rgba(248, 250, 252, 0.45), transparent 60%);
  color: #0f172a;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  backdrop-filter: blur(10px);
  box-shadow: 0 0 12px rgba(248, 250, 252, 0.6);
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}
.btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 0 16px rgba(248, 250, 252, 0.85);
  background: radial-gradient(circle at 0% 0%, rgba(248, 250, 252, 0.7), transparent 60%);
}
</style>
