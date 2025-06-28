<template>
  <div id="app">
    <Header v-if="!isAuthRoute" />
    <router-view />
    <bottom-bar />
  </div>
</template>

<script>
import Header from '@/components/Header.vue';
import BottomBar from '@/components/BottomBar.vue';

export default {
  name: 'App',
  components: {
    Header,
    BottomBar,
  },
  computed: {
    isAuthRoute() {
      return ['/login', '/register'].includes(this.$route.path);
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
  padding-bottom: 80px; /* 为底部栏预留空间 */
}
</style>