<template>
  <div class="dev-container">
    <h2>歌手管理（开发者模式）</h2>

    <!-- 创建歌手 -->
    <div class="section">
      <h3>创建歌手</h3>
      <form @submit.prevent="handleCreateSinger">
        <div class="form-item">
          <label>歌手名称</label>
          <input v-model="createForm.name" placeholder="请输入歌手名称" required />
        </div>
        <div class="form-item">
          <label>用户 ID（可选）</label>
          <input v-model="createForm.userId" type="number" placeholder="请输入用户 ID" />
        </div>
        <div class="form-item">
          <label>简介</label>
          <textarea v-model="createForm.profile" placeholder="请输入简介"></textarea>
        </div>
        <div class="form-item">
          <label>音乐流派</label>
          <input v-model="createForm.genre" placeholder="请输入流派" />
        </div>
        <div class="form-item">
          <label>国家/地区</label>
          <input v-model="createForm.country" placeholder="请输入国家/地区" />
        </div>
        <div class="form-item">
          <label>状态</label>
          <select v-model="createForm.status">
            <option :value="0">活跃</option>
            <option :value="1">退役</option>
          </select>
        </div>
        <div class="form-item">
          <label>性别</label>
          <select v-model="createForm.sex">
            <option :value="0">男</option>
            <option :value="1">女</option>
            <option :value="2">其他</option>
          </select>
        </div>
        <button type="submit" class="submit-btn">创建歌手</button>
      </form>
    </div>

    <!-- 更新歌手资料 -->
    <div class="section">
      <h3>更新歌手资料</h3>
      <form @submit.prevent="handleUpdateSinger">
        <div class="form-item">
          <label>歌手 ID</label>
          <input v-model="updateForm.id" type="number" placeholder="请输入歌手 ID" required />
        </div>
        <div class="form-item">
          <label>歌手名称</label>
          <input v-model="updateForm.name" placeholder="请输入歌手名称" />
        </div>
        <div class="form-item">
          <label>用户 ID（可选）</label>
          <input v-model="updateForm.userId" type="number" placeholder="请输入用户 ID" />
        </div>
        <div class="form-item">
          <label>简介</label>
          <textarea v-model="updateForm.profile" placeholder="请输入简介"></textarea>
        </div>
        <div class="form-item">
          <label>音乐流派</label>
          <input v-model="updateForm.genre" placeholder="请输入流派" />
        </div>
        <div class="form-item">
          <label>国家/地区</label>
          <input v-model="updateForm.country" placeholder="请输入国家/地区" />
        </div>
        <div class="form-item">
          <label>状态</label>
          <select v-model="updateForm.status">
            <option :value="0">活跃</option>
            <option :value="1">退役</option>
          </select>
        </div>
        <div class="form-item">
          <label>性别</label>
          <select v-model="updateForm.sex">
            <option :value="0">男</option>
            <option :value="1">女</option>
            <option :value="2">其他</option>
          </select>
        </div>
        <button type="submit" class="submit-btn">更新资料</button>
      </form>
    </div>

    <!-- 更新歌手头像 -->
    <div class="section">
      <h3>更新歌手头像</h3>
      <form @submit.prevent="handleUpdateAvatar">
        <div class="form-item">
          <label>歌手 ID</label>
          <input v-model="avatarForm.id" type="number" placeholder="请输入歌手 ID" required />
        </div>
        <div class="form-item">
          <label>头像文件</label>
          <input type="file" accept="image/*" @change="handleAvatarChange" required />
        </div>
        <button type="submit" class="submit-btn">上传头像</button>
      </form>
    </div>

    <!-- 删除歌手 -->
    <div class="section">
      <h3>删除歌手</h3>
      <form @submit.prevent="handleDeleteSinger">
        <div class="form-item">
          <label>歌手 ID</label>
          <input v-model="deleteSingerId" type="number" placeholder="请输入歌手 ID" required />
        </div>
        <button type="submit" class="submit-btn delete-btn">删除歌手</button>
      </form>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import md5 from 'js-md5';

export default {
  name: 'SingerManage',
  data() {
    return {
      createForm: {
        name: '',
        userId: null,
        profile: '',
        genre: '',
        country: '',
        status: 0,
        sex: 0,
      },
      updateForm: {
        id: null,
        name: '',
        userId: null,
        profile: '',
        genre: '',
        country: '',
        status: 0,
        sex: 0,
      },
      avatarForm: {
        id: null,
        file: null,
        md5: '',
      },
      deleteSingerId: null,
    };
  },
  methods: {
    async handleCreateSinger() {
      try {
        const response = await musicApi.createSinger(this.createForm);
        if (response.data.passed) {
          alert('创建歌手成功');
          this.createForm = { name: '', userId: null, profile: '', genre: '', country: '', status: 0, sex: 0 };
        } else {
          alert('创建歌手失败：' + response.data.message);
        }
      } catch (error) {
        alert('创建歌手出错：' + error.message);
      }
    },
    async handleUpdateSinger() {
      try {
        const response = await musicApi.updateSingerProfile(this.updateForm);
        if (response.data.passed) {
          alert('更新歌手资料成功');
        } else {
          alert('更新歌手资料失败：' + response.data.message);
        }
      } catch (error) {
        alert('更新歌手资料出错：' + error.message);
      }
    },
    handleAvatarChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.avatarForm.file = file;
        const reader = new FileReader();
        reader.onload = () => {
          this.avatarForm.md5 = md5(reader.result);
        };
        reader.readAsArrayBuffer(file);
      }
    },
    async handleUpdateAvatar() {
      try {
        const response = await musicApi.updateSingerAvatar(
            this.avatarForm.id,
            this.avatarForm.file,
            this.avatarForm.md5
        );
        if (response.data.passed) {
          alert('上传头像成功');
          this.avatarForm = { id: null, file: null, md5: '' };
        } else {
          alert('上传头像失败：' + response.data.message);
        }
      } catch (error) {
        alert('上传头像出错：' + error.message);
      }
    },
    async handleDeleteSinger() {
      try {
        const response = await musicApi.deleteSinger(this.deleteSingerId);
        if (response.data.passed) {
          alert('删除歌手成功');
          this.deleteSingerId = null;
        } else {
          alert('删除歌手失败：' + response.data.message);
        }
      } catch (error) {
        alert('删除歌手出错：' + error.message);
      }
    },
  },
};
</script>

<style scoped>
.dev-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
  background: #f9fafb;
}
.section {
  margin-bottom: 20px;
  padding: 15px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
h3 {
  margin-bottom: 10px;
}
.form-item {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
}
input,
textarea,
select {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
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
  cursor: pointer;
}
.submit-btn:hover {
  opacity: 0.9;
}
.delete-btn {
  background: linear-gradient(to right, #ef4444, #f87171);
}
</style>