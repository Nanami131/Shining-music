import api from './index';

export default {
  getUserTopSongs(userId, params = {}) {
    if (!userId) throw new Error('userId is required');
    return api.get(`/statistics/user/${userId}/plays/top-songs`, { params });
  },

  getUserTopSingers(userId, limit = 5) {
    return api.get(`/statistics/user/${userId}/plays/top-singers`, { params: { limit } });
  },

  getUserProfile(userId) {
    return api.get(`/statistics/user/${userId}/profile`);
  },

  refreshUserProfile(userId) {
    return api.post(`/statistics/user/${userId}/profile/refresh`);
  },

  getUserDailyStats(userId, params = {}) {
    return api.get(`/statistics/user/${userId}/plays/daily`, { params });
  },

  getUserPlayCount(userId, params = {}) {
    return api.get(`/statistics/user/${userId}/plays/count`, { params });
  },

  reportEvent(data) {
    return api.post('/statistics/events', data);
  },

  getSearchKeywords(userId, limit = 10) {
    return api.get(`/statistics/events/search-keywords/${userId}`, { params: { limit } });
  },

  getRecentPlays(userId, limit = 30) {
    return api.get(`/statistics/user/${userId}/plays/history`, { params: { limit } });
  },

  getGlobalTopSongs(limit = 20) {
    return api.get('/statistics/user/ranking/top-songs', { params: { limit } });
  },
};
