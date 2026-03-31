import axios from 'axios';

const recommendApi = axios.create({
    baseURL: '/api',
    timeout: 5000,
});

recommendApi.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default {
    getAllTagDefinitions() {
        return recommendApi.get('/recommend/tags');
    },
    getSongTags(songId) {
        return recommendApi.get(`/recommend/songs/${songId}/tags`);
    },
};
