package org.L2.community.controller;

import org.L2.common.R;
import org.L2.community.application.request.CommentCreateRequest;
import org.L2.community.application.request.PostCreateRequest;
import org.L2.community.application.request.PostUpdateRequest;
import org.L2.community.application.service.CommunityAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 社区帖子/评论相关接口。
 */

@RestController
@RequestMapping("/community")
public class PostController {

    @Autowired
    private CommunityAppService communityAppService;

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

    @GetMapping("/posts")
    public R listPosts(@RequestParam(value = "userId", required = false) Long userId) {
        return communityAppService.listPosts(userId);
    }

    @GetMapping("/post/{id}")
    public R getPostDetails(@PathVariable("id") Long id) {
        return communityAppService.getPostDetails(id);
    }

    // -------- 评论 --------

    @PostMapping("/comment")
    public R createComment(@RequestBody CommentCreateRequest request) {
        return communityAppService.createComment(request);
    }

    @GetMapping("/post/{postId}/comments")
    public R listComments(@PathVariable("postId") Long postId) {
        return communityAppService.listComments(postId);
    }
}
