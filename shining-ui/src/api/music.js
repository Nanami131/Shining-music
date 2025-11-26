import api from './index';

export default {
    createSong(data) {
        return api.post('/music/song', data);
    },
    uploadSong(id, file, md5) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('avatarFile', file);
        formData.append('md5', md5);
        return api.post('/music/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    uploadLyrics(songId, file, msg) {
        const formData = new FormData();
        formData.append('lyricsFile', file);
        formData.append('msg', msg);
        return api.post(`/music/lyrics/${songId}`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    uploadSongCover(id, file, md5) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('avatarFile', file);
        formData.append('md5', md5);
        return api.post('/music/cover/song', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    uploadPlaylistCover(id, file, md5) {
        const formData = new FormData();
        formData.append('id', id);
        formData.append('avatarFile', file);
        formData.append('md5', md5);
        return api.post('/music/cover/playlist', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    getSongBaseInfo(songId, userId) {
        const params = {};
        if (userId !== null && userId !== undefined) {
            params.userId = userId;
        }
        return api.get(`/music/info/song/${songId}`, { params });
    },
    getSongDetailsInfo(songId, userId) {
        const params = {};
        if (userId !== null && userId !== undefined) {
            params.userId = userId;
        }
        return api.get(`/music/details/song/${songId}`, { params });
    },
    getSongs(userId) {
        const params = {};
        if (userId !== null && userId !== undefined) {
            params.userId = userId;
        }
        return api.get('/music/songs', { params });
    },
    playSong(songId, userId) {
        const params = {};
        if (userId !== null && userId !== undefined) {
            params.userId = userId;
        }
        return api.get(`/music/play/song/${songId}`, { params });
    },
    getAllLyrics(songId) {
        return api.get(`/music/lyrics/all/${songId}`);
    },
    getLyrics(lyricsId) {
        return api.get(`/music/lyrics/${lyricsId}`);
    },
    createPlaylist(data) {
        return api.post('/music/playlist', data);
    },
    deletePlaylist(playlistId) {
        return api.delete('/music/playlist', { params: { playlistId } });
    },
    managePlaylistSong(data) {
        return api.post('/music/playlist/song', data);
    },
    discoverPlaylists(userId) {
        return api.get('/music/discover/playlists', {
            params: { userId },
        });
    },
    getPlaylistBaseInfo(playlistId) {
        return api.get(`/music/info/playlist/${playlistId}`);
    },
    getPlaylistDetailsInfo(playlistId) {
        return api.get(`/music/details/playlist/${playlistId}`);
    },
    getCurrentPlaylist(userId) {
        return api.get('/music/playlist/current', {
            params: { userId },
        });
    },
    createSinger(data) {
        return api.post('/music/singer', data);
    },
    deleteSinger(singerId) {
        return api.delete('/music/singer', { params: { singerId } });
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
    toggleFavoriteSong(data) {
        return api.post('/music/song/favorite', data);
    },
    getUserFavoriteSongs(userId) {
        return api.get('/music/user/favorite/songs', {
            params: { userId },
        });
    },
};
