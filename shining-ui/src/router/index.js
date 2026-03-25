import { createRouter, createWebHistory } from 'vue-router';

import ShiningHome from '../views/ShiningHome.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Profile from '../views/Profile.vue';
import MyMusic from '../views/MyMusic.vue';
import Forum from '../views/Forum.vue';
import ForumCreate from '../views/ForumCreate.vue';
import PostDetail from '../views/PostDetail.vue';
import SingerDetail from '../views/SingerDetail.vue';
import PlaylistDetail from '../views/PlaylistDetail.vue';
import SongDetail from '../views/SongDetail.vue';
import VideoDetail from '../views/VideoDetail.vue';
import UserHome from '../views/UserHome.vue';
import Singers from '../views/Singers.vue';
import Songs from '../views/Songs.vue';
import Playlists from '../views/Playlists.vue';

import SingerManage from '../views/dev/SingerManage.vue';
import SongManage from '../views/dev/SongManage.vue';
import PlaylistManage from '../views/dev/PlaylistManage.vue';
import VideoManage from '../views/dev/VideoManage.vue';

const devGuard = (to, from, next) => {
    const token = localStorage.getItem('token');
    if (!token) {
        next('/login');
    } else {
        next();
    }
};

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
    {
        path: '/my-music',
        name: 'my-music',
        component: MyMusic,
    },
    {
        path: '/forum',
        name: 'forum',
        component: Forum,
    },
    {
        path: '/forum/create',
        name: 'forum-create',
        component: ForumCreate,
    },
    {
        path: '/forum/:id',
        name: 'post-detail',
        component: PostDetail,
    },
    {
        path: '/singers',
        name: 'singers',
        component: Singers,
    },
    {
        path: '/songs',
        name: 'songs',
        component: Songs,
    },
    {
        path: '/playlists',
        name: 'playlists',
        component: Playlists,
    },
    {
        path: '/singer/:id',
        name: 'singer-detail',
        component: SingerDetail,
    },
    {
        path: '/playlist/:id',
        name: 'playlist-detail',
        component: PlaylistDetail,
    },
    {
        path: '/song/:id',
        name: 'song-detail',
        component: SongDetail,
    },
    {
        path: '/video/:id',
        name: 'video-detail',
        component: VideoDetail,
    },
    {
        path: '/user/:id',
        name: 'user-home',
        component: UserHome,
    },

    {
        path: '/dev/singer',
        name: 'singer-manage',
        component: SingerManage,
        beforeEnter: devGuard,
    },
    {
        path: '/dev/song',
        name: 'song-manage',
        component: SongManage,
        beforeEnter: devGuard,
    },
    {
        path: '/dev/playlist',
        name: 'playlist-manage',
        component: PlaylistManage,
        beforeEnter: devGuard,
    },
    {
        path: '/dev/video',
        name: 'video-manage',
        component: VideoManage,
        beforeEnter: devGuard,
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
