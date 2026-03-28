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
  background:
    radial-gradient(ellipse at 20% 0%, rgba(186, 230, 253, 0.25) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 100%, rgba(244, 219, 255, 0.2) 0%, transparent 50%),
    linear-gradient(180deg, #e8eef5 0%, #dfe6ed 50%, #e8eef5 100%);
}
#app.has-bar {
  padding-bottom: 80px;
}
</style>
