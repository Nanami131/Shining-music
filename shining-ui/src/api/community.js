import api from './index';

/**
 * 社区服务相关接口
 * 每个方法都直接透传参数，不做额外假设。
 */
export default {
    /**
     * 发布帖子
     * @param {Object} data 包含 userId、title、content
     */
    createPost(data) {
        return api.post('/community/post', data);
    },

    /**
     * 更新帖子
     * @param {Object} data 包含 id、title、content、status
     */
    updatePost(data) {
        return api.put('/community/post', data);
    },

    /**
     * 删除帖子
     * @param {Number} postId 帖子 ID
     */
    deletePost(postId) {
        return api.delete(`/community/post/${postId}`);
    },

    /**
     * 获取帖子详情（包含评论）
     * @param {Number} postId 帖子 ID
     */
    getPostDetails(postId) {
        return api.get(`/community/post/${postId}`);
    },

    /**
     * 查询帖子列表
     * @param {Object} params 可传 title、userId
     */
    listPosts(params = {}) {
        return api.get('/community/posts', {
            params,
        });
    },

    /**
     * 创建评论
     * @param {Object} data 评论数据，至少包含 postId、userId、content
     */
    createComment(data) {
        return api.post('/community/comment', data);
    },

    /**
     * 获取指定帖子下的评论列表
     * 本接口返回的是帖子详情对象，comments 在 data.comments 中
     * @param {Number} postId 帖子 ID
     */
    listComments(postId) {
        return api.get(`/community/post/${postId}/comments`);
    },

    getRecentComments(userId, limit = 10) {
        return api.get(`/community/user/${userId}/recent-comments`, { params: { limit } });
    },

    /**
     * 上传社区附件（图片 / 文件）
     * @param {File} file 原生 File 对象
     * @returns {Promise} data.data.url 为可访问地址
     */
    uploadFile(file) {
        const formData = new FormData();
        formData.append('file', file);
        return api.post('/community/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 30000,
        });
    },
};
