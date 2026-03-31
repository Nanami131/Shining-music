import api from './index';

export default {
    getAllTagDefinitions() {
        return api.get('/recommend/tags');
    },
    getSongTags(songId) {
        return api.get(`/recommend/songs/${songId}/tags`);
    },
    getDailyRecommendations(userId, limit = 20) {
        return api.get('/recommend/daily', { params: { userId, limit } });
    },
    getUserPreference(userId) {
        return api.get('/recommend/preference', { params: { userId } });
    },
};
