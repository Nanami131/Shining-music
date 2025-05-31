import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 5000,
});

// 请求拦截器，添加 token
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

export default {
    register(data) {
        return api.post('/user/register', data);
    },
    login(data) {
        return api.post('/user/login', data);
    },
    resetPassword(data) {
        return api.post('/user/reset-password', data);
    },
    getUserBaseInfo(userId) {
        return api.get('/user/info', { params: { userId } });
    },
    getUserDetailsInfo(userId) {
        return api.get('/user/update-profile', { params: { userId } });
    },
    updateProfile(data) {
        return api.post('/user/update-profile', data);
    },
    testUserService() {
        return api.get('/user/test');
    },
};