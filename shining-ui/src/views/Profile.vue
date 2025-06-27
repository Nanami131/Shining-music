<template>
  <div class="profile-container">
    <div class="profile-box">
      <h2>个人信息</h2>
      <div class="profile-info">
        <img :src="userDetails.avatarUrl || defaultAvatar" class="avatar" alt="用户头像" />
        <button class="change-avatar-btn" @click="$refs.avatarInput.click()">更改头像</button>
        <input
            type="file"
            accept="image/*"
            @change="handleUpdateAvatar"
            ref="avatarInput"
            style="display: none"
        />
        <p><strong>用户名：</strong>{{ userDetails.username || '未设置' }}</p>
        <p><strong>昵称：</strong>{{ userDetails.nickName || '未设置' }}</p>
        <p><strong>签名：</strong>{{ userDetails.signature || '未设置' }}</p>
        <p><strong>邮箱：</strong>{{ userDetails.email || '未设置' }}</p>
        <p><strong>手机号：</strong>{{ userDetails.phone || '未设置' }}</p>
        <p><strong>简介：</strong>{{ userDetails.profile || '未设置' }}</p>
      </div>

      <h3>修改个人资料</h3>
      <button class="toggle-btn" @click="toggleProfileForm">
        {{ showProfileForm ? '取消' : '编辑资料' }}
      </button>
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
          <input v-model="profileForm.signature" placeholder="请输入签名" />
        </div>
        <div class="form-item">
          <label>简介</label>
          <textarea v-model="profileForm.profile" placeholder="请输入简介"></textarea>
        </div>
        <button type="submit" class="submit-btn">保存资料</button>
      </form>

      <h3>修改密码</h3>
      <button class="toggle-btn" @click="togglePasswordForm">
        {{ showPasswordForm ? '取消' : '修改密码' }}
      </button>
      <form v-if="showPasswordForm" @submit.prevent="handleResetPassword">
        <div class="form-item">
          <label>旧密码</label>
          <input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" required />
        </div>
        <div class="form-item">
          <label>新密码</label>
          <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" required />
        </div>
        <button type="submit" class="submit-btn">修改密码</button>
      </form>
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
    this.loadUserDetails();
    const userBase = JSON.parse(localStorage.getItem('userBase') || '{}');
    this.profileForm.id = userBase.id;
    this.passwordForm.id = userBase.id;
    this.avatarForm.id = userBase.id;
  },
  methods: {
    async loadUserDetails() {
      try {
        const userId = JSON.parse(localStorage.getItem('userBase') || '{}').id;
        const response = await userApi.getUserDetailsInfo(userId);
        if (response.data.passed) {
          this.userDetails = response.data.data;
          this.profileForm = {
            id: userId,
            nickName: response.data.data.nickName || '', // 修复：确保 nickName 预加载
            email: response.data.data.email || '',
            phone: response.data.data.phone || '',
            signature: response.data.data.signature || '',
            profile: response.data.data.profile || '',
          };
        } else {
          alert('获取用户信息失败：' + response.data.message);
        }
      } catch (error) {
        alert('获取用户信息出错：' + error.message);
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
          window.dispatchEvent(new Event('userBaseUpdated')); // 触发事件
          alert('资料更新成功');
          this.loadUserDetails();
          this.showProfileForm = false;
        } else {
          alert('资料更新失败：' + response.data.message);
        }
      } catch (error) {
        alert('资料更新出错：' + error.message);
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
        alert('密码修改出错：' + error.message);
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
            window.dispatchEvent(new Event('userBaseUpdated')); // 触发事件
            alert('头像更新成功');
            this.loadUserDetails();
            this.$refs.avatarInput.value = '';
          } else {
            alert('头像更新失败：' + response.data.message);
          }
        };
        reader.readAsArrayBuffer(file);
      } catch (error) {
        alert('头像更新出错：' + error.message);
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
  justify-content: flex-start;
  padding: 40px 20px;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
.profile-box {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}
h2 {
  margin-bottom: 20px;
}
h3 {
  margin: 20px 0 10px;
}
.profile-info {
  margin-bottom: 20px;
}
.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.change-avatar-btn {
  margin: 0;
  padding: 8px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #4facfe, #00f2fe);
  color: white;
  cursor: pointer;
  font-size: 14px;
  width: 80px; /* 与头像同宽 */
}
.change-avatar-btn:hover {
  opacity: 0.9;
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
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}
textarea {
  height: 100px;
  resize: vertical;
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
.toggle-btn {
  width: 100%;
  padding: 8px;
  border: none;
  border-radius: 4px;
  background: linear-gradient(to right, #6b7280, #9ca3af);
  color: white;
  font-size: 14px;
  cursor: pointer;
}
.toggle-btn:hover {
  opacity: 0.9;
}
</style>