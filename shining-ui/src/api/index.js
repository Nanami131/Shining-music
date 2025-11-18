import axios from 'axios';

const api = axios.create({
    baseURL: '/api',
    timeout: 5000,
});

// MinIO 地址统一替换：把 http://localhost:9000 换成 /minio
const MINIO_LOCAL_PREFIX = 'http://localhost:9000';
const MINIO_PROXY_PREFIX = '/minio';

function replaceMinioUrlDeep(data) {
    if (!data) return data;

    if (typeof data === 'string') {
        if (data.startsWith(MINIO_LOCAL_PREFIX)) {
            return data.replace(MINIO_LOCAL_PREFIX, MINIO_PROXY_PREFIX);
        }
        return data;
    }

    if (Array.isArray(data)) {
        return data.map(item => replaceMinioUrlDeep(item));
    }

    if (typeof data === 'object') {
        const result = {};
        for (const key in data) {
            if (!Object.prototype.hasOwnProperty.call(data, key)) continue;
            result[key] = replaceMinioUrlDeep(data[key]);
        }
        return result;
    }

    return data;
}

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

// 响应拦截器，处理 401 + 统一替换 MinIO 地址
api.interceptors.response.use(
    (response) => {
        if (response && response.data) {
            response.data = replaceMinioUrlDeep(response.data);
        }
        return response;
    },
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
