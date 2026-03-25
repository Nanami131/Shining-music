<template>
  <div id="app" :class="{ 'has-bar': !isAuthRoute }">
    <CursorTrail />
    <Header v-if="!isAuthRoute" />
    <router-view />
    <bottom-bar v-if="!isAuthRoute" />
  </div>
</template>

<script>
import Header from '@/components/Header.vue';
import BottomBar from '@/components/BottomBar.vue';
import CursorTrail from '@/components/CursorTrail.vue';

export default {
  name: 'App',
  components: {
    Header,
    BottomBar,
    CursorTrail,
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
}
#app.has-bar {
  padding-bottom: 80px;
}
</style>
