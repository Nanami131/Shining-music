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
    getItemCFRecommendations(userId, limit = 20) {
        return api.get('/recommend/daily/item-cf', { params: { userId, limit } });
    },
    rebuildItemCFMatrix() {
        return api.post('/recommend/itemcf/rebuild');
    },
    getUserPreference(userId) {
        return api.get('/recommend/preference', { params: { userId } });
    },
};
