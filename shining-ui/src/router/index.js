import { createRouter, createWebHistory } from 'vue-router';
import ShiningHome from '../views/ShiningHome.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Profile from '../views/Profile.vue';

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
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;