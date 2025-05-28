package org.L2.user.infrastructure.mapper;

import org.L2.user.domain.model.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(Long id);
    Long insert(User user);
    void update(User user);
    void deleteById(Long id);
    List<User> query(User user);
}