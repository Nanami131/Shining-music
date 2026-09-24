import api from './index';
import { legacyListResult } from '@/utils/listPagination';

export default {
  getUserTopSongs(userId, params = {}) {
    if (!userId) throw new Error('userId is required');
    return api.get(`/statistics/user/${userId}/plays/top-songs`, { params })
      .then(response => params.page === undefined && params.size === undefined ? legacyListResult(response) : response);
  },

  getUserTopSingers(userId, limit = 5, pagination) {
    return api.get(`/statistics/user/${userId}/plays/top-singers`, {
      params: { limit, ...pagination },
    }).then(response => pagination ? response : legacyListResult(response));
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

  getRecentPlays(userId, limit = 30, pagination) {
    return api.get(`/statistics/user/${userId}/plays/history`, {
      params: { limit, page: 1, size: limit, ...pagination },
    }).then(response => pagination ? response : legacyListResult(response));
  },

  getGlobalTopSongs(limit = 20, pagination) {
    return api.get('/statistics/user/ranking/top-songs', {
      params: { limit, page: 1, size: limit, ...pagination },
    }).then(response => pagination ? response : legacyListResult(response));
  },

  getAnnualReport(userId, year) {
    const params = year ? { year } : {};
    return api.get(`/statistics/user/${userId}/annual-report`, { params });
  },
};
