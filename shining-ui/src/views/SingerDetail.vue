<template>
  <div class="singer-detail-container">
    <div v-if="isLoaded">
      <h2>{{ singer.name || '未知歌手' }}</h2>
      <div class="singer-content">
        <img :src="singer.avatarUrl || defaultAvatar" class="singer-avatar" alt="歌手头像" />
        <div class="singer-info">
          <p><strong>风格：</strong>{{ singer.genre || '未知' }}</p>
          <p><strong>国家：</strong>{{ singer.country || '未知' }}</p>
          <p><strong>简介：</strong>{{ singer.profile || '暂无简介' }}</p>
          <p><strong>状态：</strong>{{ singer.status === 0 ? '活跃' : '停更' }}</p>
          <p><strong>性别：</strong>{{ singer.sex === 0 ? '男' : singer.sex === 1 ? '女' : '其他' }}</p>
        </div>
      </div>
      <div class="songs-header">
        <h3>歌曲列表</h3>
        <button
          v-if="singer.songs && singer.songs.length"
          class="play-all-btn"
          :disabled="songOperating"
          @click="playAllSongs"
        >
          {{ songOperating ? '处理中...' : '播放全部' }}
        </button>
      </div>
      <div class="songs-list">
        <div
          v-for="(song, idx) in singer.songs"
          :key="song.id"
          class="song-card"
        >
          <img :src="song.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" @click="goToSong(song.id)" />
          <div class="song-info" @click="goToSong(song.id)">
            <h4>{{ song.title || '未知歌曲' }}</h4>
          </div>
          <button class="play-single-btn" @click="playSingleSong(song.id, idx)" title="播放">&#9654;</button>
        </div>
      </div>
    </div>
    <div v-else-if="hasError">
      <h2>歌手信息加载失败</h2>
      <p>请稍后重试。</p>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import defaultAvatar from '@/assets/default-avatar.png';
import defaultCover from '@/assets/default-cover.png';

export default {
  name: 'SingerDetail',
  data() {
    return {
      singer: null,
      defaultAvatar,
      defaultCover,
      isLoaded: false,
      hasError: false,
      userId: null,
      songOperating: false,
    };
  },
  created() {
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    this.loadSingerDetails();
  },
  watch: {
    '$route.params.id'() {
      this.loadSingerDetails();
    },
  },
  methods: {
    async loadSingerDetails() {
      this.isLoaded = false;
      this.hasError = false;
      try {
        const singerId = this.$route.params.id;
        const response = await musicApi.getSingerDetailsInfo(singerId);
        if (response.data.passed) {
          this.singer = response.data.data;
          this.isLoaded = true;
          if (this.userId) {
            statisticsApi.reportEvent({
              userId: this.userId,
              eventType: 'BROWSE',
              targetType: 'singer',
              targetId: Number(singerId),
            }).catch(() => {});
          }
        } else {
          this.hasError = true;
          alert('获取歌手信息失败：' + response.data.message);
        }
      } catch (error) {
        this.hasError = true;
        alert('获取歌手信息出错：' + error.message);
      }
    },
    goToSong(songId) {
      this.$router.push(`/song/${songId}`);
    },
    async playAllSongs() {
      const songs = this.singer?.songs;
      if (!songs || !songs.length) return;

      this.songOperating = true;
      try {
        if (this.userId) {
          const clearResp = await musicApi.clearCurrentPlaylist(this.userId);
          if (!clearResp.data?.passed) {
            alert('清空当前播放列表失败：' + (clearResp.data?.message || '未知错误'));
            return;
          }
          const curResp = await musicApi.getCurrentPlaylist(this.userId);
          if (!curResp.data?.passed || !curResp.data?.data?.id) {
            alert('获取当前播放列表失败：' + (curResp.data?.message || '未知错误'));
            return;
          }
          const playlistId = curResp.data.data.id;
          for (const song of songs) {
            const addResp = await musicApi.managePlaylistSong({
              playlistId,
              songId: song.id,
              action: 'add',
            });
            if (!addResp.data?.passed) {
              alert('加入播放列表失败：' + (addResp.data?.message || '未知错误'));
              return;
            }
          }
          this.$bus.emit('refreshCurrentPlaylist');
        }

        this.$bus.emit('playSong', {
          songId: songs[0].id,
          playlist: songs.map(s => s.id),
          index: 0,
          source: 'singerDetail',
        });
      } catch (error) {
        alert('播放全部异常：' + error.message);
      } finally {
        this.songOperating = false;
      }
    },
    playSingleSong(songId, index) {
      const songs = this.singer?.songs;
      if (!songs) return;
      this.$bus.emit('playSong', {
        songId,
        playlist: songs.map(s => s.id),
        index,
        source: 'singerDetail',
      });
    },
  },
};
</script>

<style scoped>
.singer-detail-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h2 {
  text-align: center;
  margin-bottom: 20px;
}
.singer-content {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}
.singer-avatar {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
}
.singer-info p {
  margin: 10px 0;
  font-size: 16px;
}
.songs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.songs-header h3 {
  margin: 0;
}
.play-all-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #42a5f5, #1e88e5);
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
}
.play-all-btn:hover:not(:disabled) {
  transform: scale(1.05);
  opacity: 0.9;
}
.play-all-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.songs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}
.song-card {
  position: relative;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 15px;
  transition: transform 0.2s;
}
.song-card:hover {
  transform: scale(1.05);
}
.song-cover {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 10px;
  cursor: pointer;
}
.song-info {
  cursor: pointer;
}
.song-info h4 {
  margin: 0;
  font-size: 16px;
  padding-right: 36px;
}
.play-single-btn {
  position: absolute;
  bottom: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #42a5f5, #1e88e5);
  color: white;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s, transform 0.15s;
}
.song-card:hover .play-single-btn {
  opacity: 1;
}
.play-single-btn:hover {
  transform: scale(1.15);
}
</style>

