<template>
  <div
      class="bottom-bar"
      :class="{ expanded: showLyrics, resizing: isResizingLyrics }"
      :style="bottomBarStyle"
  >
    <div class="fixed-bar">
      <div class="song-info">
        <img :src="currentSong.coverUrl || defaultCover" class="song-cover" alt="歌曲封面" />
        <div class="song-details">
          <span>{{ currentSong.title || '未知歌曲' }}</span>
          <span class="artist">歌手：{{ getArtistName(currentSong.artistId) }}</span>
        </div>
        <button
            class="favorite-btn"
            :class="{ active: currentSong && currentSong.favorite }"
            @click.stop="toggleFavoriteFromPlayer"
            :title="currentSong && currentSong.favorite ? '取消收藏' : '收藏歌曲'"
        >
          <span class="heart-icon"></span>
        </button>
        <button
            class="player-share-btn"
            type="button"
            title="分享"
            :disabled="!currentSong || !currentSong.id"
            @click.stop="openSongShare"
        >
          ↗
        </button>
      </div>

      <div class="player-controls">
        <div class="progress-bar">
          <span>{{ formatTime(currentTime) }}</span>
          <input
              type="range"
              v-model="currentTime"
              :max="duration"
              @input="seek"
              class="progress-slider"
          />
          <span>{{ formatTime(duration) }}</span>
        </div>

        <div class="control-buttons">
          <button class="icon-btn" @click="playPrev" :disabled="!hasPrev">
            <span class="icon prev"></span>
          </button>
          <button
              class="icon-btn play-btn"
              @click="togglePlay"
              :disabled="!audio.src && !(currentSong && currentSong.fileUrl)"
          >
            <span class="icon" :class="isPlaying ? 'pause' : 'play'"></span>
          </button>
          <button class="icon-btn" @click="playNext" :disabled="!hasNext">
            <span class="icon next"></span>
          </button>

          <!-- 播放模式下拉 -->
          <div class="mode-dropdown">
            <button class="mode-btn" @click="cyclePlayMode" :title="playModeLabel">
              <span class="mode-current">{{ playModeLabel }}</span>
              <span class="mode-arrow"></span>
            </button>
            <transition name="fade">
              <div v-if="showPlayModeMenu" class="mode-menu">
                <div
                    class="mode-menu-item"
                    :class="{ active: playMode === 'sequential' }"
                    @click="setPlayMode('sequential')"
                >
                  <div class="mode-menu-label">列表循环</div>
                  <div class="mode-menu-desc">按顺序播放并循环整个列表</div>
                </div>
                <div
                    class="mode-menu-item"
                    :class="{ active: playMode === 'shuffle' }"
                    @click="setPlayMode('shuffle')"
                >
                  <div class="mode-menu-label">随机播放</div>
                  <div class="mode-menu-desc">从列表中随机选择下一首</div>
                </div>
                <div
                    class="mode-menu-item"
                    :class="{ active: playMode === 'single' }"
                    @click="setPlayMode('single')"
                >
                  <div class="mode-menu-label">单曲循环</div>
                  <div class="mode-menu-desc">当前歌曲循环播放</div>
                </div>
                <div
                    class="mode-menu-item"
                    :class="{ active: playMode === 'stop' }"
                    @click="setPlayMode('stop')"
                >
                  <div class="mode-menu-label">播完停止</div>
                  <div class="mode-menu-desc">当前歌曲结束后停止播放</div>
                </div>
              </div>
            </transition>
          </div>

        </div>
      </div>

      <div class="volume-area">
        <div
            class="volume-control"
            @mouseenter="showVolumePanel = true"
            @mouseleave="hideVolumePanel"
            @focusin="showVolumePanel = true"
            @focusout="hideVolumePanel"
        >
          <button
              type="button"
              class="icon-btn volume-btn"
              :title="volume === 0 ? '取消静音' : '静音'"
              :aria-label="volume === 0 ? '取消静音' : '静音'"
              @click="toggleMute"
          >
            <span class="icon" :class="volumeIconClass"></span>
          </button>
          <transition name="volume-fade">
            <div
                v-if="showVolumePanel"
                class="volume-panel"
                :style="{ '--volume-percent': volumePercent + '%' }"
            >
              <span class="volume-value">{{ volumePercent }}%</span>
              <input
                  type="range"
                  class="volume-slider"
                  min="0"
                  max="1"
                  step="0.01"
                  :value="volume"
                  aria-label="音量"
                  @input="setVolume($event.target.value)"
              />
            </div>
          </transition>
        </div>
      </div>

      <div class="player-tools">
        <button class="toggle-lyrics" :class="{ open: showLyrics }" @click="toggleLyrics">
          {{ showLyrics ? '收起歌词' : '展开歌词' }}
        </button>
      </div>
    </div>

    <div
        class="lyrics-panel"
        v-if="showLyrics"
        :style="{ height: lyricsPanelHeight + 'px' }"
    >
      <!-- 顶部拖拽区域 -->
      <div class="lyrics-resize-handle" @mousedown="startLyricsResize"></div>

      <div class="panel-content">
        <div class="playlist-panel">
          <div class="playlist-header">
            <div class="title">当前播放列表</div>
            <div class="playlist-header-right">
              <div class="subtitle" v-if="userId">
                共 {{ currentPlaylistSongs.length }} 首
              </div>
              <div class="subtitle" v-else>登录后自动保存播放记录</div>
              <button
                  v-if="userId && currentPlaylistSongs.length > 0"
                  class="clear-playlist-btn"
                  @click="clearPlaylist"
              >
                清空列表
              </button>
            </div>
          </div>
          <div v-if="userId && currentPlaylistSongs.length" class="playlist-list">
            <div
                v-for="(song, idx) in currentPlaylistSongs"
                :key="song.id || idx"
                :class="['playlist-item', { active: currentSong && song.id === currentSong.id }]"
                @click="playFromCurrentList(idx)"
            >
              <div class="order">{{ idx + 1 }}</div>
              <div class="info">
                <button
                    type="button"
                    class="name song-name-link"
                    :title="song.title || '未知歌曲'"
                    @click.stop="goToSongDetail(song.id)"
                >
                  {{ song.title || '未知歌曲' }}
                </button>
                <p class="artist">歌手：{{ getArtistName(song.artistId) }}</p>
              </div>
              <button
                  class="remove-btn"
                  @click.stop="removeSongFromPlaylist(song.id)"
                  :disabled="!currentPlaylistId"
              >
                移除
              </button>
            </div>
          </div>
          <p v-else-if="userId" class="playlist-empty">播放任意歌曲后会自动加入此处～</p>
          <p v-else class="playlist-empty">登录账号后可同步播放列表</p>
        </div>
        <div class="lyrics-right">
          <div class="lyric-header">
            <div class="title">歌词</div>
            <div class="controls">
              <div class="lyrics-select" v-if="allLyrics.length > 1 && !bilingualMode">
                <select v-model="selectedLyricId" @change="loadSelectedLyrics">
                  <option v-for="lyric in allLyrics" :key="lyric.id" :value="lyric.id">
                    {{ lyricLabel(lyric.languageMsg) }} #{{ lyric.id }}
                  </option>
                </select>
              </div>
              <div class="lang-select">
                <span class="lang-btn" :class="{ active: !bilingualMode && selectedLang === 'ja', disabled: !hasLang('ja') }" @click="hasLang('ja') && setSingleLang('ja')">日</span>
                <span class="lang-btn" :class="{ active: !bilingualMode && selectedLang === 'zh', disabled: !hasLang('zh') }" @click="hasLang('zh') && setSingleLang('zh')">中</span>
                <span class="lang-btn" :class="{ active: !bilingualMode && selectedLang === 'en', disabled: !hasLang('en') }" @click="hasLang('en') && setSingleLang('en')">英</span>
                <span class="lang-btn bilingual-btn" :class="{ active: bilingualMode }" @click="toggleBilingual">全</span>
              </div>
              <div class="color-select">
                <span class="color-btn pink" :class="{ active: highlightColor === 'pink' }" @click="setHighlightColor('pink')"></span>
                <span class="color-btn blue" :class="{ active: highlightColor === 'blue' }" @click="setHighlightColor('blue')"></span>
                <span class="color-btn green" :class="{ active: highlightColor === 'green' }" @click="setHighlightColor('green')"></span>
                <span class="color-btn purple" :class="{ active: highlightColor === 'purple' }" @click="setHighlightColor('purple')"></span>
              </div>
            </div>
          </div>
          <div
              class="lyrics-content"
              ref="lyricsContent"
              :class="[
              'highlight-color-' + highlightColor,
              { empty: !displayLyrics.length }
            ]"
          >
            <div v-for="(line, index) in displayLyrics" :key="index" class="lyrics-group">
              <div v-if="line.break" class="lyric-break"></div>
              <div v-else
                  :class="{ active: isActiveLine(line.time, index) }"
                  class="lyric-line"
                  ref="lyricLines"
              >
                <template v-if="bilingualMode">
                  <p v-for="(lang, li) in availableLangs" :key="li"
                     :class="li === 0 ? 'lyric-primary' : 'lyric-secondary'"
                     v-show="line[lang]">{{ line[lang] }}</p>
                  <p v-if="line.text" class="lyric-primary">{{ line.text }}</p>
                </template>
                <template v-else-if="line.text">
                  <p>{{ line.text }}</p>
                </template>
                <template v-else>
                  <p v-if="line[selectedLang]">{{ line[selectedLang] }}</p>
                  <p v-else-if="getFirstLang(line)">{{ getFirstLang(line) }}</p>
                </template>
              </div>
            </div>
            <p v-if="!displayLyrics.length" class="no-lyric">
              <span>暂无歌词</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import musicApi from '@/api/music';
import statisticsApi from '@/api/statistics';
import defaultCover from '@/assets/default-cover.png';
import {
  parseLyrics as parseLrc,
  timeToSeconds,
  mergeMultiLang,
  detectLangs,
  normalizeLyricLang,
  orderLyricLangs,
  lyricLangLabel,
} from '@/utils/lrcParser';

export default {
  name: 'BottomBar',
  watch: {
    currentSong: {
      deep: true,
      handler() {
        this.broadcastPlaybackState();
      },
    },
    currentTime() {
      this.broadcastPlaybackState();
    },
    isPlaying() {
      this.broadcastPlaybackState();
    },
    duration() {
      this.broadcastPlaybackState();
    },
  },
  data() {
    return {
      currentSong: {},
      currentPlaySessionId: null,
      allLyrics: [],
      selectedLyricId: null,
      selectedLang: 'ja',
      bilingualMode: false,
      parsedLyrics: [],
      bilingualLyrics: [],
      availableLangs: [],
      audio: new Audio(),
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      actualListenedTime: 0,
      lastKnownAudioTime: 0,
      showLyrics: false,
      defaultCover,
      highlightColor: 'pink',
      userId: null,
      currentPlaylistId: null,
      currentPlaylistSongs: [],
      playMode: 'sequential', // stop | sequential | single | shuffle
      playlist: [],
      currentIndex: -1,
      showPlayModeMenu: false,
      shuffleHistory: [],
      shuffleHistoryIndex: -1,

      // 歌词区域拖拽相关状态
      lyricsPanelHeight: 240,
      lyricsMinHeight: 130,
      lyricsMaxHeight: 520,
      isResizingLyrics: false,
      resizeStartY: 0,
      resizeStartHeight: 240,
      bottomFixedHeight: 104,
      artistNameCache: {},
      playSource: 'unknown',
      audioContext: null,
      gainNode: null,
      audioSourceConnected: false,
      volume: 1,
      lastNonZeroVolume: 1,
      showVolumePanel: false,
    };
  },
  computed: {
    hasPrev() {
      if (this.playMode === 'shuffle') {
        return this.playlist.length > 1;
      }
      return this.playlist.length > 0 && this.currentIndex > 0;
    },
    hasNext() {
      if (this.playMode === 'shuffle' || this.playMode === 'sequential') {
        return this.playlist.length > 0;
      }
      return (
          this.playlist.length > 0 &&
          this.currentIndex >= 0 &&
          this.currentIndex < this.playlist.length - 1
      );
    },
    playModeLabel() {
      if (this.playMode === 'single') return '单曲循环';
      if (this.playMode === 'sequential') return '列表循环';
      if (this.playMode === 'shuffle') return '随机播放';
      return '播完停止';
    },
    volumePercent() {
      return Math.round(this.volume * 100);
    },
    volumeIconClass() {
      if (this.volume <= 0) return 'volume-muted';
      if (this.volume < 0.5) return 'volume-low';
      return 'volume-high';
    },
    bottomBarStyle() {
      const base = this.bottomFixedHeight;
      const total = this.showLyrics ? base + this.lyricsPanelHeight : base;
      return {
        height: total + 'px'
      };
    },
    displayLyrics() {
      return this.bilingualMode ? this.bilingualLyrics : this.parsedLyrics;
    },
  },
  created() {
    this._playSeq = 0;
    this._lastPlaylistLoadId = 0;
    this._playlistSetByEvent = false;
    this._saveStateTimer = null;
    let userBase = {};
    try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
    this.userId = userBase.id ?? null;
    if (this.userId) {
      this.loadCurrentPlaylist();
      this.restorePlaybackState();
    }
    this.audio.addEventListener('timeupdate', this.updateProgress);
    this.audio.addEventListener('loadedmetadata', this.updateDuration);
    this.audio.addEventListener('ended', this.handleEnded);
    this.$bus.on('playSong', this.handlePlaySongEvent);
    this.$bus.on('refreshCurrentPlaylist', this.loadCurrentPlaylist);
    window.addEventListener('userBaseUpdated', this.handleUserStateChange);
    window.addEventListener('beforeunload', this.onBeforeUnload);
  },
  beforeDestroy() {
    this.reportPlayEnd('destroy');
    this.savePlaybackStateNow();
    this.audio.removeEventListener('timeupdate', this.updateProgress);
    this.audio.removeEventListener('loadedmetadata', this.updateDuration);
    this.audio.removeEventListener('ended', this.handleEnded);
    this.$bus.off('playSong', this.handlePlaySongEvent);
    this.$bus.off('refreshCurrentPlaylist', this.loadCurrentPlaylist);
    this.audio.pause();
    this.audio.src = '';
    if (this.audioContext) {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }
    window.removeEventListener('userBaseUpdated', this.handleUserStateChange);
    window.removeEventListener('mousemove', this.onLyricsResizing);
    window.removeEventListener('mouseup', this.stopLyricsResize);
    window.removeEventListener('beforeunload', this.onBeforeUnload);
    if (this._saveStateTimer) clearTimeout(this._saveStateTimer);
  },
  methods: {
    broadcastPlaybackState() {
      const state = {
        songId: this.currentSong?.id ? Number(this.currentSong.id) : null,
        currentTime: Number(this.currentTime || 0),
        duration: Number(this.duration || 0),
        isPlaying: !!this.isPlaying,
      };
      if (typeof window !== 'undefined') {
        window.__SHINING_PLAYBACK_STATE__ = state;
      }
      this.$bus.emit('playbackStateChanged', state);
    },
    async restorePlaybackState() {
      if (!this.userId) return;
      try {
        const res = await musicApi.getPlaybackState(this.userId);
        if (res.data && res.data.passed && res.data.data) {
          const state = res.data.data;
          if (state.playMode) {
            this.playMode = state.playMode;
          }
          if (state.volume !== undefined && state.volume !== null) {
            this.applyVolume(parseFloat(state.volume));
          }
          if (state.lastSongId) {
            const songId = parseInt(state.lastSongId);
            if (songId > 0) {
              this._restoredPosition = state.lastPosition ? parseFloat(state.lastPosition) : 0;
              await this.playSong(songId);
              this.audio.pause();
              this.isPlaying = false;
              if (this._restoredPosition > 0) {
                this.audio.currentTime = this._restoredPosition;
                this.currentTime = this._restoredPosition;
              }
            }
          }
        }
      } catch (e) { /* ignore restore failure */ }
    },
    debounceSavePlaybackState() {
      if (this._saveStateTimer) clearTimeout(this._saveStateTimer);
      this._saveStateTimer = setTimeout(() => this.savePlaybackStateNow(), 500);
    },
    savePlaybackStateNow() {
      if (!this.userId) return;
      const state = {
        playMode: this.playMode,
        lastSongId: this.currentSong?.id ? String(this.currentSong.id) : '',
        lastPosition: String(this.audio.currentTime || 0),
        volume: String(this.volume),
      };
      musicApi.savePlaybackState(this.userId, state).catch(() => {});
    },
    onBeforeUnload() {
      this.savePlaybackStateNow();
    },
    showApiError(message, prefix = '') {
      const loginExpiredText = '登录已过期，请重新登录';
      const msg = message || '';
      const fullMessage = prefix ? prefix + msg : msg;
      const sourceText = msg || fullMessage;
      if (sourceText && sourceText.indexOf(loginExpiredText) !== -1) {
        if (window.__LOGIN_EXPIRED_ALERT_SHOWN__) {
          return;
        }
        window.__LOGIN_EXPIRED_ALERT_SHOWN__ = true;
        alert(loginExpiredText);
      } else {
        alert(fullMessage || prefix || '未知错误');
      }
    },
    async ensureArtistNameLoaded(artistId) {
      if (!artistId || this.artistNameCache[artistId]) {
        return;
      }
      try {
        const res = await musicApi.getSingerBaseInfo(artistId);
        const name =
            res.data && res.data.passed && res.data.data && res.data.data.name
                ? res.data.data.name
                : `歌手 ${artistId}`;
        this.artistNameCache[artistId] = name;
      } catch (error) {
        this.artistNameCache[artistId] = `歌手 ${artistId}`;
      }
    },
    async ensureArtistNames(songs) {
      if (!Array.isArray(songs) || !songs.length) {
        return;
      }
      const ids = [
        ...new Set(
            songs
                .map(song => song && song.artistId)
                .filter(id => id !== null && id !== undefined)
        ),
      ];
      await Promise.all(ids.map(id => this.ensureArtistNameLoaded(id)));
    },
    getArtistName(artistId) {
      if (!artistId) {
        return '未知歌手';
      }
      return this.artistNameCache[artistId] || `歌手 ${artistId}`;
    },

    async handleUserStateChange() {
      const token = localStorage.getItem('token');
      let userBase = {};
      try { userBase = JSON.parse(localStorage.getItem('userBase') || '{}'); } catch (e) { /* ignore */ }
      const newUserId = userBase.id ?? null;

      if (token && newUserId) {
        this.userId = newUserId;
        await this.loadCurrentPlaylist();
      } else {
        this.reportPlayEnd('logout');
        this.userId = null;
        this.currentPlaylistId = null;
        this.currentPlaylistSongs = [];
        this.playlist = [];
        this.currentIndex = -1;
        this.isPlaying = false;
        this.audio.pause();
        this.audio.src = '';
        this.currentTime = 0;
        this.duration = 0;
        this.currentSong = {};
      }
    },

    async handlePlaySongEvent({ songId, playlist, index, source, skipServerSync }) {
      this.playSource = source || 'unknown';
      if (this.userId) {
        await this.ensureCurrentPlaylistReady();
      }
      if (Array.isArray(playlist) && playlist.length) {
        this._playlistSetByEvent = true;
        this.playlist = playlist;
        this.currentIndex =
            typeof index === 'number' ? index : playlist.findIndex(id => id === songId);
        if (this.userId && playlist.length > 1 && !skipServerSync) {
          this.batchLoadEventPlaylist(playlist);
        }
      } else if (this.userId && this.currentPlaylistSongs.length) {
        this._playlistSetByEvent = false;
        this.playlist = this.currentPlaylistSongs.map(song => song.id);
        this.currentIndex = this.playlist.indexOf(songId);
      } else {
        this._playlistSetByEvent = false;
        this.playlist = [songId];
        this.currentIndex = 0;
      }
      if (this.playMode === 'shuffle') {
        this.shuffleHistory = [songId];
        this.shuffleHistoryIndex = 0;
      }
      await this.playSong(songId);
      if (this.currentIndex < 0) {
        this.currentIndex = this.playlist.indexOf(songId);
      }
    },
    async playSong(songId) {
      const playId = ++this._playSeq;
      this.reportPlayEnd('switch');
      this.audio.pause();
      this.audio.src = '';
      this.currentTime = 0;
      this.actualListenedTime = 0;
      this.lastKnownAudioTime = 0;
      this.isPlaying = false;

      try {
        const response = await musicApi.playSong(songId, this.userId);
        if (this._playSeq !== playId) return;
        if (response.data && response.data.passed) {
          this.currentSong = response.data.data;
          this.currentPlaySessionId = this.currentSong?.playSessionId || null;
          await this.ensureArtistNameLoaded(this.currentSong.artistId);
          if (this._playSeq !== playId) return;
          if (this.userId) {
            await this.ensureCurrentPlaylistReady();
            await this.addSongToCurrentPlaylist(this.currentSong);
          }
          if (this._playSeq !== playId) return;
          const url = this.currentSong.fileUrl || '';
          if (!url) return;
          this.audio.src = url;
          this.ensureAudioContext();
          this.applyVolumeGain(this.currentSong);
          try {
            await this.audio.play();
            if (this._playSeq !== playId) return;
            this.isPlaying = true;
            this.debounceSavePlaybackState();
          } catch (playErr) {
            if (this._playSeq === playId) {
              this.isPlaying = false;
              console.warn('Audio play blocked:', playErr.message);
            }
          }
          this.loadAllLyrics(songId);
        } else {
          this.showApiError(response.data ? response.data.message : '未知错误', '获取歌曲信息失败：');
        }
      } catch (error) {
        if (this._playSeq === playId) {
          this.showApiError(error.message, '播放歌曲失败：');
        }
      }
    },
    playPrev() {
      if (!this.hasPrev) return;
      this.playSource = 'prev';
      if (this.playMode === 'shuffle') {
        if (this.shuffleHistoryIndex > 0) {
          this.shuffleHistoryIndex--;
          const prevId = this.shuffleHistory[this.shuffleHistoryIndex];
          this.currentIndex = this.playlist.indexOf(prevId);
          if (prevId != null) this.playSong(prevId);
        } else {
          let prevIndex;
          if (this.playlist.length === 1) {
            prevIndex = 0;
          } else {
            do {
              prevIndex = Math.floor(Math.random() * this.playlist.length);
            } while (prevIndex === this.currentIndex);
          }
          this.currentIndex = prevIndex;
          const prevId = this.playlist[prevIndex];
          this.shuffleHistory.unshift(prevId);
          if (prevId != null) this.playSong(prevId);
        }
      } else {
        this.currentIndex -= 1;
        const prevId = this.playlist[this.currentIndex];
        if (prevId != null) this.playSong(prevId);
      }
    },
    playNext() {
      if (!this.hasNext) return;
      this.playSource = 'next';
      if (this.playMode === 'shuffle') {
        if (this.shuffleHistoryIndex < this.shuffleHistory.length - 1) {
          this.shuffleHistoryIndex++;
          const nextId = this.shuffleHistory[this.shuffleHistoryIndex];
          this.currentIndex = this.playlist.indexOf(nextId);
          if (nextId != null) this.playSong(nextId);
        } else {
          let nextIndex;
          if (this.playlist.length === 1) {
            nextIndex = 0;
          } else {
            do {
              nextIndex = Math.floor(Math.random() * this.playlist.length);
            } while (nextIndex === this.currentIndex);
          }
          this.currentIndex = nextIndex;
          const nextId = this.playlist[nextIndex];
          this.shuffleHistory.push(nextId);
          this.shuffleHistoryIndex = this.shuffleHistory.length - 1;
          if (nextId != null) this.playSong(nextId);
        }
      } else if (this.playMode === 'sequential' && this.currentIndex >= this.playlist.length - 1) {
        this.currentIndex = 0;
        const firstId = this.playlist[0];
        if (firstId != null) this.playSong(firstId);
      } else {
        this.currentIndex += 1;
        const nextId = this.playlist[this.currentIndex];
        if (nextId != null) this.playSong(nextId);
      }
    },
    handleEnded() {
      this.reportPlayEnd('ended');
      this.playSource = 'auto';
      const mode = this.playMode;
      if (mode === 'single') {
        if (this.currentSong && this.currentSong.id) {
          this.playSong(this.currentSong.id);
        }
      } else if (mode === 'sequential') {
        if (this.playlist.length > 0) {
          this.playNext();
        } else {
          this.isPlaying = false;
          this.currentTime = 0;
        }
      } else if (mode === 'shuffle') {
        if (this.playlist.length > 0) {
          let nextIndex;
          if (this.playlist.length === 1) {
            nextIndex = 0;
          } else {
            do {
              nextIndex = Math.floor(Math.random() * this.playlist.length);
            } while (nextIndex === this.currentIndex);
          }
          this.currentIndex = nextIndex;
          const nextId = this.playlist[this.currentIndex];
          if (nextId != null) {
            this.shuffleHistory.push(nextId);
            this.shuffleHistoryIndex = this.shuffleHistory.length - 1;
            this.playSong(nextId);
          }
        } else {
          this.isPlaying = false;
          this.currentTime = 0;
        }
      } else {
        if (this._playlistSetByEvent && this.currentIndex < this.playlist.length - 1) {
          this.currentIndex += 1;
          const nextId = this.playlist[this.currentIndex];
          if (nextId != null) this.playSong(nextId);
        } else {
          this._playlistSetByEvent = false;
          this.isPlaying = false;
          this.currentTime = 0;
        }
      }
    },
    cyclePlayMode() {
      this.showPlayModeMenu = !this.showPlayModeMenu;
    },
    setPlayMode(mode) {
      if (mode === 'single' || mode === 'sequential' || mode === 'stop' || mode === 'shuffle') {
        this.playMode = mode;
        if (mode === 'shuffle' && this.currentSong && this.currentSong.id) {
          this.shuffleHistory = [this.currentSong.id];
          this.shuffleHistoryIndex = 0;
        }
        this.debounceSavePlaybackState();
      }
      this.showPlayModeMenu = false;
    },
    clampVolume(value) {
      const parsed = Number(value);
      if (Number.isNaN(parsed)) return 1;
      return Math.min(Math.max(parsed, 0), 1);
    },
    applyVolume(value) {
      const nextVolume = this.clampVolume(value);
      this.volume = nextVolume;
      this.audio.volume = nextVolume;
      if (nextVolume > 0) {
        this.lastNonZeroVolume = nextVolume;
      }
    },
    setVolume(value) {
      this.applyVolume(value);
      this.debounceSavePlaybackState();
    },
    toggleMute() {
      if (this.volume > 0) {
        this.applyVolume(0);
      } else {
        this.applyVolume(this.lastNonZeroVolume || 1);
      }
      this.showVolumePanel = true;
      this.debounceSavePlaybackState();
    },
    hideVolumePanel(event) {
      if (event && event.currentTarget && event.relatedTarget && event.currentTarget.contains(event.relatedTarget)) {
        return;
      }
      this.showVolumePanel = false;
    },
    async loadCurrentPlaylist() {
      if (!this.userId) {
        this.currentPlaylistId = null;
        this.currentPlaylistSongs = [];
        this.syncPlaylistQueue();
        return;
      }
      const loadId = Date.now();
      this._lastPlaylistLoadId = loadId;
      try {
        const response = await musicApi.getCurrentPlaylist(this.userId);
        if (this._lastPlaylistLoadId !== loadId) return;
        if (response.data && response.data.passed) {
          const data = response.data.data || {};
          this.currentPlaylistId = data.id || null;
          this.currentPlaylistSongs = Array.isArray(data.songs) ? data.songs : [];
          await this.ensureArtistNames(this.currentPlaylistSongs);
          if (this._lastPlaylistLoadId !== loadId) return;
          this.syncPlaylistQueue();
          this.syncCurrentSongFromPlaylist();
        }
      } catch (error) {
        console.error('加载播放列表失败', error);
      }
    },
    async ensureCurrentPlaylistReady() {
      if (!this.userId) {
        return;
      }
      if (this.currentPlaylistId) {
        return;
      }
      await this.loadCurrentPlaylist();
    },
    syncPlaylistQueue() {
      if (this.userId && !this._playlistSetByEvent) {
        this.playlist = this.currentPlaylistSongs.map(song => song.id);
        if (this.currentSong && this.currentSong.id) {
          this.currentIndex = this.playlist.indexOf(this.currentSong.id);
        } else if (this.playlist.length === 0) {
          this.currentIndex = -1;
        } else if (this.currentIndex >= this.playlist.length) {
          this.currentIndex = this.playlist.length - 1;
        }
      } else if (this.playlist.length === 0) {
        this.currentIndex = -1;
      }
    },
    syncCurrentSongFromPlaylist() {
      if (!this.userId || !Array.isArray(this.currentPlaylistSongs) || !this.currentPlaylistSongs.length) {
        return;
      }
      if (this.currentSong && this.currentSong.id) {
        const matched = this.currentPlaylistSongs.find(song => song.id === this.currentSong.id);
        if (matched) {
          this.currentSong = { ...matched, ...this.currentSong };
          return;
        }
        if (this.audio.src && this.isPlaying) {
          return;
        }
      }
      if (!this.audio.src) {
        this.currentSong = { ...this.currentPlaylistSongs[0] };
        this.currentTime = 0;
        this.duration = 0;
      }
    },
    async addSongToCurrentPlaylist(song) {
      if (!this.userId || !this.currentPlaylistId || !song || !song.id) {
        return;
      }
      const exists = this.currentPlaylistSongs.some(item => item.id === song.id);
      if (!exists) {
        await this.ensureArtistNameLoaded(song.artistId);
        this.currentPlaylistSongs.push({
          id: song.id,
          title: song.title,
          artistId: song.artistId,
          coverUrl: song.coverUrl,
          favorite: song.favorite
        });
        this.syncPlaylistQueue();
        try {
          const response = await musicApi.managePlaylistSong({
            playlistId: this.currentPlaylistId,
            songId: song.id,
            action: 'add',
          });
          if (!response.data || !response.data.passed) {
            throw new Error(response.data ? response.data.message : '未知错误');
          }
        } catch (error) {
          console.error('添加歌曲到播放列表失败', error);
          await this.loadCurrentPlaylist();
        }
      } else {
        this.syncPlaylistQueue();
      }
    },
    async removeSongFromPlaylist(songId) {
      if (!this.userId || !this.currentPlaylistId) {
        return;
      }
      const index = this.currentPlaylistSongs.findIndex(song => song.id === songId);
      if (index === -1) {
        return;
      }
      const removedSong = this.currentPlaylistSongs.splice(index, 1)[0];
      this.syncPlaylistQueue();
      if (removedSong && this.currentSong && removedSong.id === this.currentSong.id) {
        this.isPlaying = false;
        this.audio.pause();
        this.audio.src = '';
        this.currentTime = 0;
        if (this.currentPlaylistSongs.length > 0) {
          const nextIdx = Math.min(index, this.currentPlaylistSongs.length - 1);
          this.currentSong = { ...this.currentPlaylistSongs[nextIdx] };
          this.currentIndex = nextIdx;
        } else {
          this.currentSong = {};
          this.currentIndex = -1;
        }
      }
      try {
        const response = await musicApi.managePlaylistSong({
          playlistId: this.currentPlaylistId,
          songId,
          action: 'remove',
        });
        if (!response.data || !response.data.passed) {
          throw new Error(response.data ? response.data.message : '未知错误');
        }
      } catch (error) {
        this.showApiError(error.message, '移除歌曲失败：');
        await this.loadCurrentPlaylist();
      }
    },
    async clearPlaylist() {
      if (!this.userId || !this.currentPlaylistId || !this.currentPlaylistSongs.length) {
        return;
      }
      if (!confirm('确定要清空当前播放列表吗？')) {
        return;
      }
      this.reportPlayEnd('clear');
      this.audio.pause();
      this.audio.src = '';
      this.isPlaying = false;
      this.currentTime = 0;
      this.duration = 0;
      this.currentSong = {};
      this.currentPlaylistSongs = [];
      this.playlist = [];
      this.currentIndex = -1;
      this.shuffleHistory = [];
      this.shuffleHistoryIndex = -1;
      this._playlistSetByEvent = false;
      this.savePlaybackStateNow();
      try {
        const response = await musicApi.clearCurrentPlaylist(this.userId);
        if (!response.data || !response.data.passed) {
          throw new Error(response.data ? response.data.message : '未知错误');
        }
      } catch (error) {
        this.showApiError(error.message, '清空播放列表失败：');
        await this.loadCurrentPlaylist();
      }
    },
    async batchLoadEventPlaylist(songIds) {
      const existingIds = new Set(this.currentPlaylistSongs.map(s => s.id));
      const toLoad = songIds.filter(id => !existingIds.has(id));
      if (!toLoad.length) return;
      const results = await Promise.all(
          toLoad.map(id =>
              musicApi.getSongBaseInfo(id, this.userId).catch(() => null)
          )
      );
      const newSongs = [];
      for (const res of results) {
        const song = res?.data?.passed ? res.data.data : null;
        if (song && !this.currentPlaylistSongs.some(s => s.id === song.id)) {
          this.currentPlaylistSongs.push({
            id: song.id,
            title: song.title,
            artistId: song.artistId,
            coverUrl: song.coverUrl,
            favorite: song.favorite,
          });
          newSongs.push(song);
        }
      }
      if (newSongs.length) {
        await this.ensureArtistNames(newSongs);
        if (this.currentPlaylistId) {
          for (const song of newSongs) {
            musicApi.managePlaylistSong({
              playlistId: this.currentPlaylistId,
              songId: song.id,
              action: 'add',
            }).catch(() => {});
          }
        }
      }
    },
    playFromCurrentList(index) {
      const song = this.currentPlaylistSongs[index];
      if (!song) {
        return;
      }
      this.playSource = 'currentList';
      this._playlistSetByEvent = false;
      this.playlist = this.currentPlaylistSongs.map(s => s.id);
      this.currentIndex = index;
      this.playSong(song.id);
    },
    goToSongDetail(songId) {
      if (!songId) {
        return;
      }
      this.$router.push({ name: 'song-detail', params: { id: songId } });
    },
    openSongShare() {
      if (!this.currentSong?.id) return;
      this.$bus.emit('openSongShare', {
        songId: this.currentSong.id,
        currentTime: this.currentTime,
        preferredLanguage: this.selectedLang,
      });
    },
    async toggleFavoriteFromPlayer() {
      if (!this.currentSong || !this.currentSong.id) {
        return;
      }
      if (!this.userId) {
        alert('请先登录再收藏歌曲');
        return;
      }
      try {
        const response = await musicApi.toggleFavoriteSong({
          userId: this.userId,
          songId: this.currentSong.id
        });
        if (response.data && response.data.passed) {
          const favorite = response.data.data?.favorite ?? false;
          this.currentSong.favorite = favorite;
          statisticsApi.reportEvent({
            userId: this.userId,
            eventType: 'FAVORITE',
            targetType: 'song',
            targetId: this.currentSong.id,
            extraData: { action: favorite ? 'add' : 'remove' },
          }).catch(() => {});
        } else {
          const msg = response.data ? response.data.message : '未知错误';
          this.showApiError(msg, '更新收藏状态失败：');
        }
      } catch (error) {
        this.showApiError(error.message, '更新收藏状态失败：');
      }
    },
    async loadAllLyrics(songId) {
      this.allLyrics = [];
      this.selectedLyricId = null;
      this.parsedLyrics = [];
      this.bilingualLyrics = [];
      this.availableLangs = [];
      try {
        const response = await musicApi.getAllLyrics(songId);
        if (response.data.passed && Array.isArray(response.data.data) && response.data.data.length > 0) {
          this.allLyrics = response.data.data;
          this.selectedLyricId = this.allLyrics[0].id;
          const firstLang = this.allLyrics[0].languageMsg;
          if (firstLang) {
            this.selectedLang = normalizeLyricLang(firstLang);
          }
          this.loadSelectedLyrics();
        }
      } catch (error) {
        console.error('[BottomBar] loadAllLyrics failed for songId=' + songId, error);
      }
    },
    loadSelectedLyrics() {
      const selectedLyric = this.allLyrics.find(lyric => lyric.id === this.selectedLyricId);
      if (selectedLyric) {
        this.parseLyrics(selectedLyric.content || '');
      } else {
        this.parsedLyrics = [];
      }
      this.buildBilingual();
    },
    parseLyrics(content) {
      this.parsedLyrics = parseLrc(content);
    },
    buildBilingual() {
      const parsed = this.parsedLyrics;
      const inlineLangs = detectLangs(parsed);
      if (inlineLangs.length > 0) {
        this.bilingualLyrics = parsed;
        this.availableLangs = orderLyricLangs(inlineLangs);
        return;
      }
      if (this.allLyrics.length >= 2) {
        const sources = this.allLyrics
          .filter(l => l.languageMsg)
          .map(l => ({ lang: normalizeLyricLang(l.languageMsg), lines: parseLrc(l.content || '') }))
          .filter(s => s.lang);
        if (sources.length >= 2) {
          this.bilingualLyrics = mergeMultiLang(sources);
          this.availableLangs = orderLyricLangs(sources.map(s => s.lang));
          return;
        }
      }
      this.bilingualLyrics = parsed;
      this.availableLangs = [];
    },
    getFirstLang(line) {
      for (const k of Object.keys(line)) {
        if (k !== 'time' && k !== 'break' && line[k]) return line[k];
      }
      return '';
    },
    timeToSeconds(timeStr) {
      return timeToSeconds(timeStr);
    },
    isActiveLine(time, index) {
      const lyrics = this.displayLyrics;
      if (!lyrics.length) return false;
      const nextLine = lyrics[index + 1];
      const isActive =
          this.currentTime >= time &&
          (!nextLine || this.currentTime < nextLine.time);
      if (isActive && this.showLyrics) {
        this.$nextTick(() => this.scrollToActiveLine(index));
      }
      return isActive;
    },
    scrollToActiveLine(index) {
      const lyricsContent = this.$refs.lyricsContent;
      if (!lyricsContent) return;
      const groups = lyricsContent.querySelectorAll('.lyrics-group');
      const target = groups && groups[index];
      if (target) {
        const scrollTop = target.offsetTop - lyricsContent.offsetTop - 50;
        if (scrollTop >= 0 && scrollTop <= lyricsContent.scrollHeight - lyricsContent.clientHeight) {
          lyricsContent.scrollTo({ top: scrollTop, behavior: 'smooth' });
        }
      }
    },
    togglePlay() {
      if (this.isPlaying) {
        this.audio.pause();
        this.isPlaying = false;
      } else {
        if (!this.audio.src && this.currentSong && this.currentSong.fileUrl) {
          this.audio.src = this.currentSong.fileUrl;
        }
        if (!this.audio.src) {
          return;
        }
        this.ensureAudioContext();
        this.audio.play();
        this.isPlaying = true;
      }
    },
    updateProgress() {
      const now = this.audio.currentTime || 0;
      const delta = now - this.lastKnownAudioTime;
      if (delta > 0 && delta < 2) {
        this.actualListenedTime += delta;
      }
      this.lastKnownAudioTime = now;
      this.currentTime = now;
    },
    updateDuration() {
      this.duration = this.audio.duration || 0;
      if (this.currentSong?.id && this.duration > 0 && !this.currentSong.duration) {
        const dur = Math.round(this.duration);
        musicApi.updateSongDuration(this.currentSong.id, dur).catch(() => {});
      }
    },
    reportPlayEnd(reason) {
      if (!this.currentSong?.id || !this.userId || this.currentTime <= 1) return;
      const payload = {
        userId: this.userId,
        songId: this.currentSong.id,
        playSessionId: this.currentPlaySessionId || null,
        duration: Math.round(this.currentTime),
        totalDuration: Math.round(this.duration),
        actualListenedTime: Math.round(this.actualListenedTime),
        completed: reason === 'ended',
        source: this.playSource || 'unknown',
      };
      musicApi.reportPlayEnd(payload).catch(() => {});
      this.actualListenedTime = 0;
      this.lastKnownAudioTime = this.audio.currentTime || 0;
    },
    seek() {
      if (this.audio.src) {
        this.audio.currentTime = this.currentTime;
        this.lastKnownAudioTime = this.currentTime;
      }
    },
    toggleLyrics() {
      this.showLyrics = !this.showLyrics;
    },
    hasLang(lang) {
      return this.allLyrics.some(
        l => normalizeLyricLang(l.languageMsg) === lang
      );
    },
    setSingleLang(lang) {
      this.bilingualMode = false;
      this.selectedLang = lang;
      const match = this.allLyrics.find(
        l => normalizeLyricLang(l.languageMsg) === lang
      );
      if (match && match.id !== this.selectedLyricId) {
        this.selectedLyricId = match.id;
        this.loadSelectedLyrics();
      }
    },
    toggleBilingual() {
      this.bilingualMode = !this.bilingualMode;
    },
    setHighlightColor(color) {
      this.highlightColor = color;
    },
    lyricLabel(lang) {
      return lyricLangLabel(lang);
    },
    formatTime(time) {
      if (!time || isNaN(time)) return '00:00';
      const minutes = Math.floor(time / 60)
          .toString()
          .padStart(2, '0');
      const seconds = Math.floor(time % 60)
          .toString()
          .padStart(2, '0');
      return `${minutes}:${seconds}`;
    },

    ensureAudioContext() {
      if (this.audioContext) {
        if (this.audioContext.state === 'suspended') {
          this.audioContext.resume();
        }
        return;
      }
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      this.audioContext = new AudioCtx();
      this.gainNode = this.audioContext.createGain();
      const source = this.audioContext.createMediaElementSource(this.audio);
      source.connect(this.gainNode);
      this.gainNode.connect(this.audioContext.destination);
      this.audioSourceConnected = true;
    },
    applyVolumeGain(song) {
      if (!this.gainNode) return;
      const gainDb = (song && song.volumeGain != null) ? song.volumeGain : 0;
      const linear = Math.pow(10, gainDb / 20);
      this.gainNode.gain.value = Math.min(Math.max(linear, 0.05), 15.0);
    },

    // 歌词区域拖拽相关方法
    startLyricsResize(event) {
      event.preventDefault();
      this.isResizingLyrics = true;
      this.resizeStartY = event.clientY;
      this.resizeStartHeight = this.lyricsPanelHeight;
      window.addEventListener('mousemove', this.onLyricsResizing);
      window.addEventListener('mouseup', this.stopLyricsResize);
    },
    onLyricsResizing(event) {
      if (!this.isResizingLyrics) return;
      const delta = this.resizeStartY - event.clientY;
      let newHeight = this.resizeStartHeight + delta;
      if (newHeight < this.lyricsMinHeight) {
        newHeight = this.lyricsMinHeight;
      }
      if (newHeight > this.lyricsMaxHeight) {
        newHeight = this.lyricsMaxHeight;
      }
      this.lyricsPanelHeight = newHeight;
    },
    stopLyricsResize() {
      if (!this.isResizingLyrics) return;
      this.isResizingLyrics = false;
      window.removeEventListener('mousemove', this.onLyricsResizing);
      window.removeEventListener('mouseup', this.stopLyricsResize);
    }
  }
};
</script>

<style scoped>
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 104px;
  background: #fff;
  box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  transition: height 0.3s ease;
}
.bottom-bar.expanded {
  /* 高度通过行内样式控制 */
}
/* 拖动过程中关闭过渡，避免“变形”感 */
.bottom-bar.resizing {
  transition: none;
}

.fixed-bar {
  --bar-control-shift: 8px;
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 14px 24px;
  gap: 22px;
  height: 104px;
  z-index: 3;
}
.player-tools {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex: 0 0 160px;
  transform: translate(-34px, var(--bar-control-shift));
  z-index: 4;
}
.volume-area {
  position: absolute;
  right: 52px;
  top: calc(50% + var(--bar-control-shift));
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  transform: translateY(-50%);
  z-index: 8;
  pointer-events: auto;
}
.song-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  transform: translateY(var(--bar-control-shift));
}
.song-cover {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
}
.song-details {
  display: flex;
  flex-direction: column;
}
.song-details span {
  font-size: 14px;
}
.song-details .artist {
  font-size: 12px;
  color: #666;
}
.favorite-btn {
  width: 40px;
  height: 40px;
  border-radius: 20px;
  border: none;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}
.favorite-btn .heart-icon {
  width: 20px;
  height: 20px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23ff6b81' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 21s-6.3-4.35-9-8.4C1 9 2 5 5.5 4S12 8 12 8s2.5-4 6-4 4.5 3 2.5 6.6c-2.7 4.05-9 8.4-9 8.4z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}
.favorite-btn.active {
  background: rgba(255, 99, 132, 0.18);
  box-shadow: 0 6px 16px rgba(255, 99, 132, 0.35);
}
.favorite-btn.active .heart-icon {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23ff3366'%3E%3Cpath d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3c3.08 0 5.5 2.42 5.5 5.5 0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/%3E%3C/svg%3E");
}
.favorite-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(255, 99, 132, 0.3);
}
.player-share-btn {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #818cf8, #7c3aed);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.24);
  cursor: pointer;
}
.player-share-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}
.player-controls {
  flex: 2;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transform: translate(-14px, var(--bar-control-shift));
}
.progress-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.progress-bar span {
  font-size: 12px;
  color: #666;
}
.progress-slider {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: #dfe6e9;
  appearance: none;
  cursor: pointer;
}
.progress-slider::-webkit-slider-thumb {
  appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ff6b81;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.2);
}
.control-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}
.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 20px;
  border: 1px solid rgba(148, 163, 184, 0.7);
  background: transparent;
  box-shadow: 0 0 10px rgba(148, 163, 184, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  backdrop-filter: blur(4px);
}
.icon-btn:hover {
  box-shadow: 0 0 14px rgba(79, 172, 254, 0.55);
  border-color: rgba(79, 172, 254, 0.9);
}
.icon-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
}
.play-btn {
  width: 46px;
  height: 46px;
  border-radius: 23px;
  border-color: rgba(79, 172, 254, 0.9);
  box-shadow: 0 0 16px rgba(79, 172, 254, 0.6);
}
.icon {
  display: block;
  width: 24px;
  height: 24px;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
}
.icon.play {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9 6l8 6-8 6z' fill='none' stroke='%234facfe' stroke-width='2.2' stroke-linejoin='round'/%3E%3C/svg%3E");
}
.icon.pause {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='7' y='6' width='3.5' height='12' rx='1.2' fill='none' stroke='%234facfe' stroke-width='2'/%3E%3Crect x='13.5' y='6' width='3.5' height='12' rx='1.2' fill='none' stroke='%234facfe' stroke-width='2'/%3E%3C/svg%3E");
}
.icon.prev,
.icon.next {
  width: 24px;
  height: 24px;
}
.icon.prev {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M9 6v12' stroke='%234b5563' stroke-width='2' stroke-linecap='round'/%3E%3Cpath d='M17 6l-7 6 7 6z' fill='none' stroke='%234b5563' stroke-width='2' stroke-linejoin='round'/%3E%3C/svg%3E");
}
.icon.next {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M15 6v12' stroke='%234b5563' stroke-width='2' stroke-linecap='round'/%3E%3Cpath d='M7 6l7 6-7 6z' fill='none' stroke='%234b5563' stroke-width='2' stroke-linejoin='round'/%3E%3C/svg%3E");
}

/* 播放模式下拉样式 */
.mode-dropdown {
  position: relative;
}
.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #e0f2fe, #f5f3ff);
  color: #1e293b;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(148, 163, 184, 0.35);
  transition: all 0.2s ease;
}
.mode-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(148, 163, 184, 0.5);
}
.mode-current {
  font-weight: 500;
}
.mode-arrow {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid #64748b;
}
.mode-icon {
  font-size: 12px;
}
.mode-menu {
  position: absolute;
  right: 0;
  bottom: 110%;
  min-width: 180px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.18);
  padding: 8px;
  z-index: 9999;
}
.mode-menu-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.mode-menu-item + .mode-menu-item {
  margin-top: 4px;
}
.mode-menu-item:hover {
  background: #eff6ff;
}
.mode-menu-item.active {
  background: linear-gradient(135deg, #4facfe, #38bdf8);
  color: #ffffff;
}
.mode-menu-label {
  font-size: 13px;
  font-weight: 500;
}
.mode-menu-desc {
  font-size: 11px;
  opacity: 0.8;
  margin-top: 2px;
}

.volume-control {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
}
.volume-control::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 34px;
  width: 58px;
  height: 24px;
  transform: translateX(-50%);
}
.volume-btn {
  width: 38px;
  height: 38px;
  border-color: rgba(203, 213, 225, 0.9);
  box-shadow: none;
}
.volume-btn:hover {
  border-color: rgba(236, 65, 65, 0.75);
  box-shadow: 0 0 12px rgba(236, 65, 65, 0.18);
}
.icon.volume-high,
.icon.volume-low,
.icon.volume-muted {
  width: 21px;
  height: 21px;
}
.icon.volume-high {
  background-image: url("data:image/svg+xml,%3Csvg width='24' height='24' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M4.8 9.6v4.8h3.35L13 18.2V5.8L8.15 9.6H4.8z' fill='%23374751'/%3E%3Cpath d='M16.1 8.25c1.3 1.78 1.3 5.72 0 7.5M18.7 6.15c2.25 3.05 2.25 8.65 0 11.7' stroke='%23374751' stroke-width='1.65' stroke-linecap='round'/%3E%3C/svg%3E");
}
.icon.volume-low {
  background-image: url("data:image/svg+xml,%3Csvg width='24' height='24' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M4.8 9.6v4.8h3.35L13 18.2V5.8L8.15 9.6H4.8z' fill='%23374751'/%3E%3Cpath d='M16.1 8.25c1.3 1.78 1.3 5.72 0 7.5' stroke='%23374751' stroke-width='1.65' stroke-linecap='round'/%3E%3C/svg%3E");
}
.icon.volume-muted {
  background-image: url("data:image/svg+xml,%3Csvg width='24' height='24' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M4.8 9.6v4.8h3.35L13 18.2V5.8L8.15 9.6H4.8z' fill='%23ec4141'/%3E%3Cpath d='M16.4 9.6l4.2 4.2M20.6 9.6l-4.2 4.2' stroke='%23ec4141' stroke-width='1.8' stroke-linecap='round'/%3E%3C/svg%3E");
}
.volume-panel {
  position: absolute;
  left: 50%;
  bottom: 42px;
  width: 44px;
  height: 148px;
  padding: 11px 0 13px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.16);
  transform: translateX(-50%);
  z-index: 10000;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.volume-panel::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -6px;
  width: 10px;
  height: 10px;
  border-right: 1px solid rgba(226, 232, 240, 0.95);
  border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  background: #ffffff;
  transform: translateX(-50%) rotate(45deg);
}
.volume-value {
  height: 16px;
  line-height: 16px;
  color: #64748b;
  font-size: 11px;
  font-weight: 500;
}
.volume-slider {
  position: absolute;
  left: 50%;
  bottom: 58px;
  width: 96px;
  height: 18px;
  border-radius: 999px;
  appearance: none;
  background: linear-gradient(
      to right,
      #ec4141 0%,
      #ec4141 var(--volume-percent),
      #d7dce2 var(--volume-percent),
      #d7dce2 100%
  );
  background-size: 100% 4px;
  background-position: center;
  background-repeat: no-repeat;
  cursor: pointer;
  touch-action: none;
  transform: translateX(-50%) rotate(-90deg);
  transform-origin: center;
  z-index: 1;
}
.volume-slider:focus {
  outline: none;
}
.volume-slider::-webkit-slider-thumb {
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 3px solid #ec4141;
  background: #ffffff;
  box-shadow: 0 2px 6px rgba(236, 65, 65, 0.28);
}
.volume-slider::-moz-range-thumb {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 3px solid #ec4141;
  background: #ffffff;
  box-shadow: 0 2px 6px rgba(236, 65, 65, 0.28);
}

.volume-fade-enter-active,
.volume-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.volume-fade-enter,
.volume-fade-enter-from,
.volume-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(4px);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.fade-enter,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.toggle-lyrics {
  position: relative;
  height: 32px;
  padding: 0 12px 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 999px;
  background:
      radial-gradient(circle at 0 0, rgba(186, 230, 253, 0.72), transparent 58%),
      radial-gradient(circle at 100% 100%, rgba(244, 219, 255, 0.72), transparent 58%),
      rgba(255, 255, 255, 0.82);
  color: #475569;
  font-size: 13px;
  line-height: 30px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.78),
      0 5px 14px rgba(15, 23, 42, 0.08);
  transition: color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}
.toggle-lyrics::after {
  content: '';
  width: 6px;
  height: 6px;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  transform: rotate(-135deg);
  transform-origin: center;
  margin-top: 3px;
  transition: transform 0.18s ease, margin-top 0.18s ease;
}
.toggle-lyrics:hover {
  color: #0f172a;
  border-color: rgba(96, 165, 250, 0.68);
  box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.9),
      0 0 16px rgba(56, 189, 248, 0.22),
      0 8px 20px rgba(15, 23, 42, 0.1);
  transform: translateY(-1px);
}
.toggle-lyrics.open::after {
  transform: rotate(45deg);
  margin-top: -3px;
}
.toggle-lyrics.open {
  border-color: rgba(244, 114, 182, 0.42);
}

/* 歌词区域和拖拽条 */
.lyrics-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
  z-index: 1;
  display: flex;
  flex-direction: column;
}
.lyrics-resize-handle {
  height: 28px;
  flex-shrink: 0;
  cursor: row-resize;
  position: relative;
}
.lyrics-resize-handle::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 40px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
  transform: translate(-50%, -50%);
}

/* 顶部区域内部布局 */
.panel-content {
  display: flex;
  flex-direction: row;
  flex: 1;
  min-height: 0;
}

/* 播放列表列 */
.playlist-panel {
  width: 35%;
  padding: 16px;
  border-right: 1px solid #e2e8f0;
  background: #fefeff;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.playlist-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}
.playlist-header .title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}
.playlist-header-right {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.playlist-header .subtitle {
  font-size: 12px;
  color: #94a3b8;
}
.clear-playlist-btn {
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  transition: all 0.2s ease;
}
.clear-playlist-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}
.playlist-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  padding-right: 6px;
}
.playlist-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s ease;
}
.playlist-item:hover {
  background: #e2e8f0;
}
.playlist-item.active {
  background: #dbeafe;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.35);
}
.playlist-item .order {
  width: 18px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}
.playlist-item .info {
  flex: 1;
  min-width: 0;
}
.playlist-item .info .name {
  display: inline;
  max-width: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-family: inherit;
  text-align: left;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}
.playlist-item .info .song-name-link:hover {
  color: #2563eb;
  text-decoration: underline;
}
.playlist-item .info .song-name-link:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
  border-radius: 4px;
}
.playlist-item .info .artist {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}
.remove-btn {
  border: none;
  background: transparent;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
}
.remove-btn:disabled {
  color: #cbd5f5;
  cursor: not-allowed;
}
.playlist-empty {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 20px;
}

/* 歌词列 */
.lyrics-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.lyric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px 0;
}
.lyric-header .title {
  font-weight: bold;
}
.lyric-header .controls {
  display: flex;
  gap: 10px;
  align-items: center;
}
.lyrics-select select {
  padding: 4px;
  border-radius: 4px;
  border: 1px solid #ccc;
  font-size: 14px;
  cursor: pointer;
}
.lang-select {
  display: flex;
  gap: 6px;
}
.color-select {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.lang-btn {
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: 50%;
  background: #dfe6e9;
  color: #636e72;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}
.lang-btn:hover {
  background: #b2bec3;
  color: #fff;
}
.lang-btn.active {
  background: #4facfe;
  color: #fff;
  box-shadow: 0 0 8px rgba(0, 0, 0, 0.2);
  transform: scale(1.1);
}
.lang-btn.disabled {
  opacity: 0.3;
  cursor: not-allowed;
  pointer-events: none;
}
.color-btn {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid #fff;
}
.color-btn.pink {
  background: #ff6b81;
}
.color-btn.blue {
  background: #3498db;
}
.color-btn.purple {
  background: #9b59b6;
}
.color-btn.green {
  background: #2ecc71;
}
.color-btn:hover {
  transform: scale(1.2);
}
.color-btn.active {
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.3);
  transform: scale(1.2);
}

/* 歌词内容区域随高度伸缩，并支持空状态垂直居中 */
.lyrics-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #edf1f6;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.lyrics-content.empty {
  justify-content: center;
}
.lyrics-content.empty .no-lyric {
  margin: 0;
}

.lyrics-content.highlight-color-pink .lyric-line {
  color: #ff6b81;
}
.lyrics-content.highlight-color-blue .lyric-line {
  color: #3498db;
}
.lyrics-content.highlight-color-green .lyric-line {
  color: #2ecc71;
}
.lyrics-content.highlight-color-purple .lyric-line {
  color: #9b59b6;
}
.lyrics-content.highlight-color-pink .lyric-line.active {
  --highlight-color: #ff6b81;
  --highlight-bg: rgba(255, 107, 129, 0.2);
}
.lyrics-content.highlight-color-blue .lyric-line.active {
  --highlight-color: #3498db;
  --highlight-bg: rgba(52, 152, 219, 0.2);
}
.lyrics-content.highlight-color-green .lyric-line.active {
  --highlight-color: #2ecc71;
  --highlight-bg: rgba(46, 204, 113, 0.2);
}
.lyrics-content.highlight-color-purple .lyric-line.active {
  --highlight-color: #9b59b6;
  --highlight-bg: rgba(155, 89, 182, 0.2);
}

.lyrics-group {
  margin-bottom: 20px;
}
.lyric-line {
  font-size: 20px;
  color: #333;
  margin-bottom: 8px;
  font-family: 'LXGW WenKai', 'AR PL UKai CN', 'STKaiti', 'KaiTi', '楷体', serif;
  display: inline-block;
}
.lyric-line.active {
  color: var(--highlight-color);
  background: var(--highlight-bg);
  font-weight: 600;
  width: 100%;
  padding: 8px 0;
}
.lyric-line p {
  margin: 2px 0;
}
.lyric-primary {
  font-size: 21px;
}
.lyric-secondary {
  font-size: 16px;
  opacity: 0.55;
  margin-top: 3px !important;
  letter-spacing: 0.5px;
}
.lyric-break {
  height: 24px;
}
.bilingual-btn {
  width: auto !important;
  padding: 0 10px;
  border-radius: 18px !important;
  font-size: 13px !important;
}
.lyrics-content p:not(.lyric-line p) {
  font-size: 20px;
  color: #666;
  font-family: 'LXGW WenKai', 'AR PL UKai CN', 'STKaiti', 'KaiTi', '楷体', serif;
}
.no-lyric {
  text-align: center;
  font-size: 18px;
  color: #666;
}
</style>
