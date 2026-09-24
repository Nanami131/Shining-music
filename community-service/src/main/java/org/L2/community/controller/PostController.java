package org.L2.community.controller;

import org.L2.common.R;
import org.L2.common.context.UserContext;
import org.L2.community.application.request.CommentCreateRequest;
import org.L2.community.application.request.PostCreateRequest;
import org.L2.community.application.request.PostUpdateRequest;
import org.L2.community.application.service.CommunityAppService;
import org.L2.community.domain.service.PostLikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 社区帖子/评论相关接口。
 */

@RestController
@RequestMapping("/community")
public class PostController {

    @Autowired
    private CommunityAppService communityAppService;

    @Autowired
    private PostLikeService postLikeService;

    // -------- 帖子 --------

    @PostMapping("/post")
    public R createPost(@RequestBody PostCreateRequest request) {
        return communityAppService.createPost(request);
    }

    @PutMapping("/post")
    public R updatePost(@RequestBody PostUpdateRequest request) {
        return communityAppService.updatePost(request);
    }

    @DeleteMapping("/post/{id}")
    public R deletePost(@PathVariable("id") Long id) {
        return communityAppService.deletePost(id);
    }

    public R listPosts(Long userId) { return listPosts(userId, null, null); }

    @GetMapping("/posts")
    public R listPosts(@RequestParam(value = "userId", required = false) Long userId,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "size", required = false) Integer size) {
        if ((page != null || size != null) &&
                (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))) {
            return R.error("分页参数无效");
        }
        if (page != null && size != null && page > 0 && size > 0) {
            R result = communityAppService.listPostsPage(userId, ((long) page - 1L) * size, size);
            return org.L2.common.ListPagination.fromPage(result, communityAppService.countPosts(userId), page, size);
        }
        return org.L2.common.ListPagination.apply(communityAppService.listPosts(userId), page, size);
    }

    public R getPostDetails(Long id) { return getPostDetails(id, null, null); }

    @GetMapping("/post/{id}")
    public R getPostDetails(@PathVariable("id") Long id,
                            @RequestParam(value = "page", required = false) Integer page,
                            @RequestParam(value = "size", required = false) Integer size) {
        if (page == null && size == null) return communityAppService.getPostDetails(id);
        if (page == null || size == null || page < 1 || size < 0 || (size == 0 && page != 1))
            return R.error("分页参数无效");
        return communityAppService.getPostDetails(id, page, size);
    }

    // -------- 评论 --------

    @PostMapping("/comment")
    public R createComment(@RequestBody CommentCreateRequest request) {
        return communityAppService.createComment(request);
    }

    public R listComments(Long postId) { return listComments(postId, null, null); }

    @GetMapping("/post/{postId}/comments")
    public R listComments(@PathVariable("postId") Long postId,
                          @RequestParam(value = "page", required = false) Integer page,
                          @RequestParam(value = "size", required = false) Integer size) {
        return org.L2.common.ListPagination.apply(communityAppService.listComments(postId), page, size);
    }

    @GetMapping("/user/{userId}/recent-comments")
    public R getRecentComments(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return communityAppService.getRecentCommentsByUser(userId, limit);
    }

    // -------- 点赞 --------

    @PostMapping("/post/{id}/like")
    public R toggleLike(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.error("用户未登录");
        boolean liked = postLikeService.toggleLike(id, userId);
        return R.success(liked ? "已点赞" : "已取消", Map.of("liked", liked));
    }

    @GetMapping("/post/{id}/like/status")
    public R getLikeStatus(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.success("查询成功", Map.of("liked", false));
        boolean liked = postLikeService.isLiked(id, userId);
        return R.success("查询成功", Map.of("liked", liked));
    }

    // -------- 文件上传 --------

    @PostMapping("/upload")
    public R uploadFile(@RequestParam("file") MultipartFile file) {
        return communityAppService.uploadFile(file);
    }
}
