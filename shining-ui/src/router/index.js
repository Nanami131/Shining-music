import { createRouter, createWebHistory } from 'vue-router';
import ShiningHome from '../views/ShiningHome.vue';
import Login from '../views/Login.vue';

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
        component: () => import('../views/Register.vue'), // 占位，待实现
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;