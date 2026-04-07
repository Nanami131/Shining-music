package org.L2.community.domain.service;

import org.L2.community.domain.model.UserFollow;
import org.L2.community.infrastructure.mapper.UserFollowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserFollowService {

    @Autowired
    private UserFollowMapper userFollowMapper;

    public boolean follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) return false;
        UserFollow f = new UserFollow()
                .setFollowerId(followerId)
                .setFollowingId(followingId);
        return userFollowMapper.insert(f) > 0;
    }

    public boolean unfollow(Long followerId, Long followingId) {
        return userFollowMapper.delete(followerId, followingId) > 0;
    }

    public List<UserFollow> getFollowingList(Long userId, int page, int size) {
        return userFollowMapper.selectFollowing(userId, (page - 1) * size, size);
    }

    public List<UserFollow> getFollowerList(Long userId, int page, int size) {
        return userFollowMapper.selectFollowers(userId, (page - 1) * size, size);
    }

    public int countFollowing(Long userId) {
        return userFollowMapper.countFollowing(userId);
    }

    public int countFollowers(Long userId) {
        return userFollowMapper.countFollowers(userId);
    }

    /**
     * @return "NONE", "FOLLOWING", or "MUTUAL"
     */
    public String getFollowStatus(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) return "SELF";
        boolean iFollow = userFollowMapper.selectByPair(fromUserId, toUserId) != null;
        boolean theyFollow = userFollowMapper.selectByPair(toUserId, fromUserId) != null;
        if (iFollow && theyFollow) return "MUTUAL";
        if (iFollow) return "FOLLOWING";
        return "NONE";
    }
}
