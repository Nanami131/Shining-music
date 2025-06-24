package org.L2.music.infrastructure;

import org.L2.music.domain.model.Lyrics;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LyricsMapper {
    Lyrics selectById(Long id);

    List<Lyrics> selectBySongId(Long songId);

    int insert(Lyrics lyrics);

    int update(Lyrics lyrics);

    int deleteById(Long id);
}