package org.L2.music.infrastructure;

import org.L2.music.domain.model.Video;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoMapper {
    Video selectById(Long id);

    int insert(Video video);

    int update(Video video);

    List<Video> query(Video video);
}

