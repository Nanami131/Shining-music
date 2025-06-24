import api from './index';

export default {
    // 歌曲相关
    uploadSong(id, file, md5) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('avatarFile', file);
        formData.append('md5', md5);
        return api.post('/music/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    getSongBaseInfo(songId) {
        return api.get(`/music/info/song/${songId}`);
    },
    getSongDetailsInfo(songId) {
        return api.get(`/music/details/song/${songId}`);
    },

    // 歌单相关
    createPlaylist(data) {
        return api.post('/music/playlist', data);
    },
    deletePlaylist(playlistId) {
        return api.delete(`/music/playlist/${playlistId}`);
    },
    managePlaylistSong(data) {
        return api.post('/music/playlist/song', data);
    },
    getPlaylistBaseInfo(playlistId) {
        return api.get(`/music/info/playlist/${playlistId}`);
    },
    getPlaylistDetailsInfo(playlistId) {
        return api.get(`/music/details/playlist/${playlistId}`);
    },

    // 歌手相关
    createSinger(data) {
        return api.post('/music/singer', data);
    },
    deleteSinger(singerId) {
        return api.delete(`/music/singer/${singerId}`);
    },
    updateSingerProfile(data) {
        return api.post('/music/update-profile', data);
    },
    updateSingerAvatar(id, file, md5) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('avatarFile', file);
        formData.append('md5', md5);
        return api.post('/music/update-avatar', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    getSingerBaseInfo(singerId) {
        return api.get(`/music/info/player/${singerId}`);
    },
    getSingerDetailsInfo(singerId) {
        return api.get(`/music/details/player/${singerId}`);
    },
};