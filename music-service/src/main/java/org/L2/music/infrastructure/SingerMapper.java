package org.L2.music.infrastructure;

import org.L2.music.domain.model.Singer;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SingerMapper {
    Singer selectById(Long id);

    int insert(Singer singer);

    int update(Singer singer);

    int deleteById(Long id);

    List<Singer> query(Singer singer);
}