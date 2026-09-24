<template>
  <div id="app" :class="{ 'has-bar': showAppChrome, 'android-app': isAndroidApp, 'web-compact': !isAndroidApp && webListMode === 'compact' }">
    <CursorTrail />
    <Header v-if="showAppChrome" />
    <router-view />
    <bottom-bar v-if="showAppChrome" />
    <SongShareDialog />
    <AndroidServerSettings v-if="isAndroidApp" :with-player="showAppChrome" />
  </div>
</template>

<script>
import Header from '@/components/Header.vue';
import BottomBar from '@/components/BottomBar.vue';
import CursorTrail from '@/components/CursorTrail.vue';
import SongShareDialog from '@/components/SongShareDialog.vue';
import AndroidServerSettings from '@/components/AndroidServerSettings.vue';
import { isAndroidApp } from '@/utils/androidServer';
import { webListMode } from '@/utils/webListMode';
import '@/styles/webCompact.css';

export default {
  name: 'App',
  components: {
    Header,
    BottomBar,
    CursorTrail,
    SongShareDialog,
    AndroidServerSettings,
  },
  computed: {
    webListMode() {
      return webListMode.value;
    },
    isAndroidApp() {
      return isAndroidApp;
    },
    isAuthRoute() {
      return ['/login', '/register'].includes(this.$route.path);
    },
    showAppChrome() {
      return !this.isAuthRoute;
    },
  },
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  min-height: 100vh;
  background:
    radial-gradient(ellipse at 20% 0%, rgba(186, 230, 253, 0.25) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 100%, rgba(244, 219, 255, 0.2) 0%, transparent 50%),
    linear-gradient(180deg, #e8eef5 0%, #dfe6ed 50%, #e8eef5 100%);
}
#app.has-bar {
  padding-bottom: 104px;
}
@media (max-width: 768px) {
  #app.android-app {
    width: 100%;
    overflow-x: clip;
  }
  #app.android-app .header {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    padding: 10px 12px;
  }
  #app.android-app .header .logo-area {
    min-width: 0;
  }
  #app.android-app .header .nav {
    grid-column: 1 / -1;
    grid-row: 2;
    min-width: 0;
    max-width: 100%;
    gap: 2px;
    overflow-x: auto;
    box-shadow: none;
  }
  #app.android-app .header .nav-item {
    flex-shrink: 0;
    padding: 6px 10px;
  }
  #app.android-app .header .actions {
    grid-column: 2;
    grid-row: 1;
    min-width: 0;
    gap: 4px;
  }
  #app.android-app .header .nickname {
    max-width: 80px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  #app.android-app .discover-page {
    min-width: 0;
    padding: 12px;
  }
  #app.android-app .hero-panel {
    grid-template-columns: minmax(0, 1fr);
    min-width: 0;
    padding: 16px;
  }
  #app.android-app .hero-content {
    min-width: 0;
  }
  #app.android-app .hero-content h1 {
    overflow-wrap: anywhere;
  }
  #app.android-app.has-bar {
    padding-bottom: 136px;
  }
  #app.android-app .bottom-bar,
  #app.android-app .fixed-bar {
    height: 136px;
  }
  #app.android-app .fixed-bar {
    display: flex;
    flex-wrap: wrap;
    align-content: center;
    box-sizing: border-box;
    gap: 4px 6px;
    padding: 8px;
  }
  #app.android-app .fixed-bar > .song-info {
    flex: 1 1 110px;
    min-width: 0;
    order: 1;
    transform: none;
  }
  #app.android-app .fixed-bar .song-details {
    min-width: 0;
    max-width: 86px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  #app.android-app .player-tools {
    flex: 0 0 auto;
    order: 2;
    transform: none;
  }
  #app.android-app .volume-area {
    position: static;
    flex: 0 0 46px;
    order: 3;
    transform: none;
  }
  #app.android-app .player-controls {
    flex: 1 1 100%;
    min-width: 0;
    order: 4;
    transform: none;
    gap: 2px;
  }
  #app.android-app .android-server-settings.with-player {
    bottom: 144px;
  }
  #app.android-app .my-music-container .quick-links {
    gap: 6px;
  }
  #app.android-app .my-music-container .quick-link-card {
    flex: 1 1 0;
    min-width: 0;
    flex-direction: column;
    gap: 4px;
    padding: 10px 4px;
    white-space: nowrap;
    font-size: 12px;
  }
}
</style>
