package org.L2.user.infrastructure.mapper;

import org.L2.user.domain.model.PasswordHistory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PasswordHistoryMapper {
    void insert(PasswordHistory passwordHistory);
    List<PasswordHistory> queryByUserId(Long userId);
}