import { createRouter, createWebHistory } from 'vue-router';
import ShiningHome from '../views/ShiningHome.vue';

const routes = [
    {
        path: '/',
        name: 'shining-home',
        component: ShiningHome,
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;