import api from './index';

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
    logout(userId, deviceCode) {
        return api.post('/user/logout', null, { params: { userId, deviceCode } });
    },
};