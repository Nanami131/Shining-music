package org.L2.community.controller;

import org.L2.common.R;
import org.L2.common.context.UserContext;
import org.L2.community.domain.model.UserFollow;
import org.L2.community.domain.service.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/community")
public class FollowController {

    @Autowired
    private UserFollowService userFollowService;

    @PostMapping("/follow")
    public R follow(@RequestBody Map<String, Long> body) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return R.error("用户未登录");
        Long targetUserId = body.get("targetUserId");
        if (targetUserId == null) return R.error("目标用户ID不能为空");
        if (currentUserId.equals(targetUserId)) return R.error("不能关注自己");
        boolean ok = userFollowService.follow(currentUserId, targetUserId);
        return ok ? R.success("关注成功") : R.success("已关注");
    }

    @DeleteMapping("/follow/{targetUserId}")
    public R unfollow(@PathVariable("targetUserId") Long targetUserId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return R.error("用户未登录");
        userFollowService.unfollow(currentUserId, targetUserId);
        return R.success("已取消关注");
    }

    @GetMapping("/following")
    public R getFollowing(@RequestParam("userId") Long userId,
                          @RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "20") int size) {
        List<UserFollow> list = userFollowService.getFollowingList(userId, page, size);
        List<Long> ids = list.stream().map(UserFollow::getFollowingId).toList();
        return R.success("查询成功", ids);
    }

    @GetMapping("/followers")
    public R getFollowers(@RequestParam("userId") Long userId,
                          @RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "20") int size) {
        List<UserFollow> list = userFollowService.getFollowerList(userId, page, size);
        List<Long> ids = list.stream().map(UserFollow::getFollowerId).toList();
        return R.success("查询成功", ids);
    }

    @GetMapping("/follow/status")
    public R getFollowStatus(@RequestParam("targetUserId") Long targetUserId) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) return R.success("查询成功", "NONE");
        String status = userFollowService.getFollowStatus(currentUserId, targetUserId);
        return R.success("查询成功", status);
    }

    @GetMapping("/follow/count")
    public R getFollowCount(@RequestParam("userId") Long userId) {
        int following = userFollowService.countFollowing(userId);
        int followers = userFollowService.countFollowers(userId);
        return R.success("查询成功", Map.of("following", following, "followers", followers));
    }
}
