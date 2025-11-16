<template>
  <div class="profile-container">
    <div class="profile-card">
      <div class="profile-header">
        <div class="avatar-wrapper" @click="$refs.avatarInput.click()">
          <img :src="userDetails.avatarUrl || defaultAvatar" class="avatar" alt="用户头像" />
          <div class="avatar-glow"></div>
          <div class="avatar-edit">修改头像</div>
        </div>
        <input
          type="file"
          accept="image/*"
          @change="handleUpdateAvatar"
          ref="avatarInput"
          style="display: none"
        />
        <div class="title-block">
          <h2>个人信息</h2>
          <p class="subtitle">在这里维护你的专属 Shining 形象</p>
        </div>
      </div>

      <div class="profile-content">
        <div class="info-panel glass">
          <div class="info-row">
            <span class="label">用户名</span>
            <span class="value">{{ userDetails.username || '未设置' }}</span>
          </div>
          <div class="info-row">
            <span class="label">昵称</span>
            <span class="value">{{ userDetails.nickName || '未设置' }}</span>
          </div>
          <div class="info-row">
            <span class="label">签名</span>
            <span class="value">{{ userDetails.signature || '这个人很神秘，还没有签名' }}</span>
          </div>
          <div class="info-row">
            <span class="label">邮箱</span>
            <span class="value">{{ userDetails.email || '未填写' }}</span>
          </div>
          <div class="info-row">
            <span class="label">手机号</span>
            <span class="value">{{ userDetails.phone || '未填写' }}</span>
          </div>
          <div class="info-row">
            <span class="label">简介</span>
            <span class="value multi-line">{{ userDetails.profile || '写一点关于你的故事吧～' }}</span>
          </div>
        </div>

        <div class="forms-panel">
          <div class="section glass">
            <div class="section-header">
              <h3>修改个人信息</h3>
              <button class="pill-btn" @click="toggleProfileForm">
                {{ showProfileForm ? '收起' : '编辑' }}
              </button>
            </div>
            <form v-if="showProfileForm" @submit.prevent="handleUpdateProfile">
              <div class="form-item">
                <label>昵称</label>
                <input v-model="profileForm.nickName" placeholder="请输入昵称" />
              </div>
              <div class="form-item">
                <label>邮箱</label>
                <input v-model="profileForm.email" type="email" placeholder="请输入邮箱" />
              </div>
              <div class="form-item">
                <label>手机号</label>
                <input v-model="profileForm.phone" type="tel" placeholder="请输入手机号" />
              </div>
              <div class="form-item">
                <label>签名</label>
                <input v-model="profileForm.signature" placeholder="写一句个性签名" />
              </div>
              <div class="form-item">
                <label>简介</label>
                <textarea
                  v-model="profileForm.profile"
                  placeholder="可以写一写你的故事、喜爱的音乐风格等～"
                ></textarea>
              </div>
              <button type="submit" class="submit-btn">保存信息</button>
            </form>
          </div>

          <div class="section glass">
            <div class="section-header">
              <h3>修改密码</h3>
              <button class="pill-btn" @click="togglePasswordForm">
                {{ showPasswordForm ? '收起' : '修改' }}
              </button>
            </div>
            <form v-if="showPasswordForm" @submit.prevent="handleResetPassword">
              <div class="form-item">
                <label>原密码</label>
                <input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  placeholder="请输入当前密码"
                  required
                />
              </div>
              <div class="form-item">
                <label>新密码</label>
                <input
                  v-model="passwordForm.newPassword"
                  type="password"
                  placeholder="请输入新密码"
                  required
                />
              </div>
              <button type="submit" class="submit-btn danger">确认修改</button>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import userApi from '@/api/user';
import defaultAvatar from '@/assets/default-avatar.png';
import md5 from 'js-md5';

export default {
  name: 'Profile',
  data() {
    return {
      userDetails: {},
      profileForm: {
        id: null,
        nickName: '',
        email: '',
        phone: '',
        signature: '',
        profile: '',
      },
      passwordForm: {
        id: null,
        oldPassword: '',
        newPassword: '',
      },
      avatarForm: {
        id: null,
        avatarFile: null,
        md5: '',
      },
      defaultAvatar,
      showProfileForm: false,
      showPasswordForm: false,
    };
  },
  created() {
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    if (!userBase.id) {
      alert('请先登录再查看个人信息');
      this.$router.push('/login');
      return;
    }
    this.profileForm.id = userBase.id;
    this.passwordForm.id = userBase.id;
    this.avatarForm.id = userBase.id;
    this.loadUserDetails();
  },
  methods: {
    async loadUserDetails() {
      try {
        const response = await userApi.getUserDetailsInfo(this.profileForm.id);
        if (response.data.passed) {
          this.userDetails = response.data.data;
          this.profileForm = {
            id: this.profileForm.id,
            nickName: response.data.data.nickName || '',
            email: response.data.data.email || '',
            phone: response.data.data.phone || '',
            signature: response.data.data.signature || '',
            profile: response.data.data.profile || '',
          };
        } else {
          alert('获取用户信息失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取用户信息异常：' + error.message);
      }
    },
    async handleUpdateProfile() {
      try {
        const response = await userApi.updateProfile(this.profileForm);
        if (response.data.passed) {
          const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
          userBase.nickName = this.profileForm.nickName;
          userBase.email = this.profileForm.email;
          localStorage.setItem('userBase', JSON.stringify(userBase));
          window.dispatchEvent(new Event('userBaseUpdated'));
          alert('资料更新成功');
          this.loadUserDetails();
          this.showProfileForm = false;
        } else {
          alert('资料更新失败：' + response.data.message);
        }
      } catch (error) {
        alert('资料更新异常：' + error.message);
      }
    },
    async handleResetPassword() {
      try {
        const response = await userApi.resetPassword(this.passwordForm);
        if (response.data.passed) {
          alert('密码修改成功');
          this.passwordForm.oldPassword = '';
          this.passwordForm.newPassword = '';
          this.showPasswordForm = false;
        } else {
          alert('密码修改失败：' + response.data.message);
        }
      } catch (error) {
        alert('密码修改异常：' + error.message);
      }
    },
    async handleUpdateAvatar(event) {
      const file = event.target.files[0];
      if (!file) return;
      try {
        const reader = new FileReader();
        reader.onload = async () => {
          const fileMd5 = md5(reader.result);
          const response = await userApi.updateAvatar(this.avatarForm.id, file, fileMd5);
          if (response.data.passed) {
            const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
            userBase.avatarUrl = response.data.data;
            localStorage.setItem('userBase', JSON.stringify(userBase));
            window.dispatchEvent(new Event('userBaseUpdated'));
            alert('头像更新成功');
            this.loadUserDetails();
            this.$refs.avatarInput.value = '';
          } else {
            alert('头像更新失败：' + response.data.message);
          }
        };
        reader.readAsArrayBuffer(file);
      } catch (error) {
        alert('头像更新异常：' + error.message);
      }
    },
    toggleProfileForm() {
      this.showProfileForm = !this.showProfileForm;
    },
    togglePasswordForm() {
      this.showPasswordForm = !this.showPasswordForm;
    },
  },
};
</script>

<style scoped>
.profile-container {
  display: flex;
  justify-content: center;
  padding: 40px 20px 60px;
  background: radial-gradient(circle at 0% 0%, rgba(186, 230, 253, 0.9), transparent 55%),
              radial-gradient(circle at 100% 0%, rgba(244, 219, 255, 0.95), transparent 55%),
              linear-gradient(to bottom right, #0f172a, #020617);
}
.profile-card {
  width: 100%;
  max-width: 900px;
  border-radius: 24px;
  padding: 24px 26px 26px;
  background: radial-gradient(circle at 0 0, rgba(56, 189, 248, 0.18), transparent 60%),
              radial-gradient(circle at 100% 100%, rgba(129, 230, 217, 0.16), transparent 60%),
              rgba(15, 23, 42, 0.82);
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.75);
  color: #e5f0ff;
  backdrop-filter: blur(22px);
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 22px;
}
.title-block h2 {
  margin: 0 0 4px;
  font-size: 22px;
}
.subtitle {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
}
.avatar-wrapper {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  cursor: pointer;
}
.avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  position: relative;
  z-index: 2;
  box-shadow: 0 0 18px rgba(59, 130, 246, 0.7);
}
.avatar-glow {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.7), transparent 65%);
  filter: blur(2px);
  z-index: 1;
}
.avatar-edit {
  position: absolute;
  left: 50%;
  bottom: -6px;
  transform: translateX(-50%);
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.85);
  color: #e5f0ff;
  font-size: 12px;
  border: 1px solid rgba(148, 163, 184, 0.7);
  backdrop-filter: blur(10px);
}
.profile-content {
  display: grid;
  grid-template-columns: 1.1fr 1.2fr;
  gap: 20px;
}
.glass {
  background: radial-gradient(circle at 0 0, rgba(15, 23, 42, 0.85), transparent 70%),
              rgba(15, 23, 42, 0.83);
  border-radius: 18px;
  padding: 16px 18px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.55);
}
.info-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.35);
}
.info-row:last-child {
  border-bottom: none;
}
.label {
  font-size: 13px;
  color: #9ca3af;
}
.value {
  font-size: 13px;
  color: #e5f0ff;
  text-align: right;
}
.value.multi-line {
  max-width: 240px;
  white-space: pre-wrap;
}
.forms-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.section-header h3 {
  margin: 0;
  font-size: 16px;
}
.pill-btn {
  padding: 4px 12px;
  border-radius: 999px;
  border: none;
  background: rgba(148, 163, 184, 0.24);
  color: #e5f0ff;
  font-size: 12px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}
.pill-btn:hover {
  background: rgba(96, 165, 250, 0.65);
}
.form-item {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
  font-size: 14px;
}
input,
textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 8px;
  font-size: 14px;
  background: rgba(15, 23, 42, 0.8);
  color: #e5f0ff;
}
textarea {
  height: 100px;
  resize: vertical;
}
.submit-btn {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(to right, #4facfe, #22d3ee);
  color: white;
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(56, 189, 248, 0.6);
}
.submit-btn.danger {
  background: linear-gradient(to right, #fb7185, #f97316);
  box-shadow: 0 12px 30px rgba(248, 113, 113, 0.65);
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 40px rgba(56, 189, 248, 0.8);
}
@media (max-width: 768px) {
  .profile-card {
    padding: 18px 16px 20px;
  }
  .profile-content {
    grid-template-columns: 1fr;
  }
}
</style>

