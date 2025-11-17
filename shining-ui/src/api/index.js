import axios from 'axios';

const api = axios.create({
    baseURL: '/api',
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

// 响应拦截器，处理 401
api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token');
            localStorage.removeItem('deviceCode');
            localStorage.removeItem('userBase');
            alert('登录已过期，请重新登录');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export default api;