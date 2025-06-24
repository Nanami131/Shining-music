import { createRouter, createWebHistory } from 'vue-router';
import ShiningHome from '../views/ShiningHome.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Profile from '../views/Profile.vue';

// 开发者模式路由，生产环境注释以下导入和路由
import SingerManage from '../views/dev/SingerManage.vue';
import SongManage from '../views/dev/SongManage.vue';
import PlaylistManage from '../views/dev/PlaylistManage.vue';

const routes = [
    {
        path: '/',
        name: 'shining-home',
        component: ShiningHome,
    },
    {
        path: '/login',
        name: 'login',
        component: Login,
    },
    {
        path: '/register',
        name: 'register',
        component: Register,
    },
    {
        path: '/profile',
        name: 'profile',
        component: Profile,
    },
    // 开发者模式路由，生产环境注释以下路由
    {
      path: '/dev/singer',
      name: 'singer-manage',
      component: SingerManage,
    },
    {
      path: '/dev/song',
      name: 'song-manage',
      component: SongManage,
    },
    {
      path: '/dev/playlist',
      name: 'playlist-manage',
      component: PlaylistManage,
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;