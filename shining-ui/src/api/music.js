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
    getSingers() {
        return api.get('/music/singers');
    },
    getPlaylists(userId) {
        const params = {};
        if (userId !== null && userId !== undefined) {
            params.userId = userId;
        }
        return api.get('/music/playlists', { params });
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
    updatePlaylist(data) {
        return api.post('/music/playlist/update', data);
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
    clearCurrentPlaylist(userId) {
        return api.post('/music/playlist/clear', null, {
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
    uploadVideo(singerId, title, videoFile, md5) {
        const formData = new FormData();
        if (singerId !== null && singerId !== undefined && singerId !== '') {
            formData.append('singerId', singerId);
        }
        formData.append('title', title);
        formData.append('videoFile', videoFile);
        formData.append('md5', md5);
        return api.post('/music/video/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
    },
    updateVideoMeta(data) {
        return api.post('/music/video/update', data);
    },
    getVideoInfo(videoId) {
        return api.get(`/music/video/${videoId}`);
    },
    listVideos() {
        return api.get('/music/videos');
    },
    search(keyword, page = 0, size = 10) {
        return api.get('/music/search', { params: { keyword, page, size } });
    },
    searchSync() {
        return api.post('/music/search/sync');
    },
    updateSongDuration(songId, duration) {
        return api.post(`/music/song/${songId}/duration`, null, { params: { duration } });
    },
    reportPlayEnd(data) {
        return api.post('/music/play/end', data);
    },
};
