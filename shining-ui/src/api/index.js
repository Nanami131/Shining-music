import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 5000,
});

export default {
    testUserService() {
        return api.get('/user/test');
    },
};