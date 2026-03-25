package org.L2.community.application.service;

import org.L2.common.R;
import org.L2.common.annotation.PermissionCheck;
import org.L2.common.minio.MinioProperties;
import org.L2.common.minio.service.FileNameGenerateService;
import org.L2.common.minio.service.SimpleMinioService;
import org.L2.community.application.dto.CommentDTO;
import org.L2.community.application.dto.PostDTO;
import org.L2.community.application.dto.PostDetailsDTO;
import org.L2.community.application.request.CommentCreateRequest;
import org.L2.community.application.request.PostCreateRequest;
import org.L2.community.application.request.PostUpdateRequest;
import org.L2.community.domain.model.ForumComment;
import org.L2.community.domain.model.ForumPost;
import org.L2.community.domain.service.ForumCommentService;
import org.L2.community.domain.service.ForumPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 社区应用服务，负责封装聚合和 DTO 转换。
 */
@Service
public class CommunityAppService {

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private ForumCommentService forumCommentService;

    @Autowired
    private SimpleMinioService simpleMinioService;

    @Autowired
    private MinioProperties minioProperties;

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    /**
     * 发布帖子。
     */
    @PermissionCheck
    public R createPost(PostCreateRequest request) {
        if (request == null
                || request.getUserId() == null
                || request.getTitle() == null
                || request.getTitle().isBlank()
                || request.getContent() == null
                || request.getContent().isBlank()) {
            return R.error("帖子信息不完整");
        }

        ForumPost post = new ForumPost()
                .setUserId(request.getUserId())
                .setTitle(request.getTitle())
                .setContent(request.getContent());

        return forumPostService.createPost(post);
    }

    /**
     * 更新帖子。
     */
    public R updatePost(PostUpdateRequest request) {
        if (request == null || request.getId() == null) {
            return R.error("帖子ID不能为空");
        }

        ForumPost post = new ForumPost()
                .setId(request.getId())
                .setTitle(request.getTitle())
                .setContent(request.getContent());

        return forumPostService.updatePost(post);
    }

    /**
     * 删除帖子，同时清理关联评论。
     */
    public R deletePost(Long id) {
        forumCommentService.deleteByPostId(id);
        return forumPostService.deletePost(id);
    }

    /**
     * 帖子列表（简单版，不分页）。
     */
    public R listPosts(Long userId) {
        ForumPost condition = new ForumPost();
        if (userId != null) {
            condition.setUserId(userId);
        }
        List<ForumPost> posts = forumPostService.queryPosts(condition);

        List<PostDTO> dtoList = new ArrayList<>();
        if (posts != null) {
            for (ForumPost post : posts) {
                PostDTO dto = new PostDTO()
                        .setId(post.getId())
                        .setUserId(post.getUserId())
                        .setTitle(post.getTitle())
                        .setContent(post.getContent())
                        .setCommentCount(post.getCommentCount())
                        .setLastCommentAt(post.getLastCommentAt())
                        .setCreatedAt(post.getCreatedAt());
                dtoList.add(dto);
            }
        }

        return R.success("获取帖子列表成功", dtoList);
    }

    /**
     * 帖子详情 + 评论树。
     */
    public R getPostDetails(Long id) {
        ForumPost post = forumPostService.getPostById(id);
        if (post == null) {
            return R.error("帖子不存在");
        }

        PostDetailsDTO details = new PostDetailsDTO()
                .setId(post.getId())
                .setUserId(post.getUserId())
                .setTitle(post.getTitle())
                .setContent(post.getContent())
                .setCommentCount(post.getCommentCount())
                .setLastCommentAt(post.getLastCommentAt())
                .setCreatedAt(post.getCreatedAt());

        // 查询该帖所有评论
        ForumComment condition = new ForumComment().setPostId(id);
        List<ForumComment> comments = forumCommentService.queryComments(condition);

        List<CommentDTO> commentTree = buildCommentTree(comments);
        details.setComments(commentTree);

        return R.success("获取帖子详情成功", details);
    }

    /**
     * 单独拉取评论列表（如果前端只想刷新评论时可以使用）。
     */
    public R listComments(Long postId) {
        ForumComment condition = new ForumComment().setPostId(postId);
        List<ForumComment> comments = forumCommentService.queryComments(condition);
        List<CommentDTO> commentTree = buildCommentTree(comments);
        return R.success("获取评论列表成功", commentTree);
    }

    /**
     * 发表评论。
     */
    @PermissionCheck
    public R createComment(CommentCreateRequest request) {
        if (request == null
                || request.getPostId() == null
                || request.getUserId() == null
                || request.getContent() == null
                || request.getContent().isBlank()) {
            return R.error("评论信息不完整");
        }

        ForumComment comment = new ForumComment()
                .setPostId(request.getPostId())
                .setParentId(request.getParentId())
                .setUserId(request.getUserId())
                .setReplyToUserId(request.getReplyToUserId())
                .setContent(request.getContent());

        return forumCommentService.createComment(comment);
    }

    /**
     * 上传社区附件（图片 / 文件），存入 MinIO 并返回可访问 URL。
     */
    @PermissionCheck
    public R uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.error("文件不能为空");
        }

        String contentType = file.getContentType();
        boolean isImage = contentType != null && IMAGE_TYPES.contains(contentType.toLowerCase());

        long maxSize = isImage ? MAX_IMAGE_SIZE : MAX_FILE_SIZE;
        if (file.getSize() > maxSize) {
            return R.error("文件大小超过限制（图片 5MB / 其他 20MB）");
        }

        String prefix = isImage ? "community/images/" : "community/files/";
        String objectName = FileNameGenerateService.defineNamePath(
                file.getOriginalFilename(), prefix, 0L, 6);

        String result = simpleMinioService.uploadFile(file, objectName);
        if (!result.contains("成功")) {
            return R.error(result);
        }

        String url = minioProperties.getEndpoint() + "/"
                + minioProperties.getBucketName() + "/" + objectName;
        return R.success("上传成功", Map.of("url", url));
    }

    /**
     * 根据 post_comments 表记录，在内存中构建两级评论树。
     * 规则：
     *  - parentId 为空视为一级评论
     *  - parentId 不为空视为回复，挂到对应父评论下
     */
    private List<CommentDTO> buildCommentTree(List<ForumComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        Map<Long, CommentDTO> idToDto = new LinkedHashMap<>();
        List<CommentDTO> roots = new ArrayList<>();

        for (ForumComment c : comments) {
            CommentDTO dto = new CommentDTO()
                    .setId(c.getId())
                    .setPostId(c.getPostId())
                    .setParentId(c.getParentId())
                    .setUserId(c.getUserId())
                    .setReplyToUserId(c.getReplyToUserId())
                    .setContent(c.getContent())
                    .setFloorNo(c.getFloorNo())
                    .setReplyCount(c.getReplyCount())
                    .setCreatedAt(c.getCreatedAt());
            idToDto.put(dto.getId(), dto);
        }

        for (CommentDTO dto : idToDto.values()) {
            Long parentId = dto.getParentId();
            if (parentId == null) {
                roots.add(dto);
            } else {
                CommentDTO parent = idToDto.get(parentId);
                if (parent != null) {
                    parent.getReplies().add(dto);
                } else {
                    roots.add(dto);
                }
            }
        }

        Comparator<CommentDTO> byTime = Comparator.comparing(
                CommentDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

        roots.sort(byTime);
        for (CommentDTO root : roots) {
            if (root.getReplies() != null && root.getReplies().size() > 1) {
                root.getReplies().sort(byTime);
            }
        }

        return roots;
    }
}
