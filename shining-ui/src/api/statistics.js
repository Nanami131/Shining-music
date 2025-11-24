import api from './index';

export default {
  getUserTopSongs(userId, params = {}) {
    if (!userId) {
      throw new Error('userId is required');
    }
    return api.get(`/statistics/user/${userId}/plays/top-songs`, {
      params,
    });
  },
};
