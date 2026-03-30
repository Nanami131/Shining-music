package org.L2.recommend.infrastructure;

import org.L2.recommend.domain.model.SongTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SongTagMapper {

    List<SongTag> selectBySongId(@Param("songId") Long songId);

    SongTag selectBySongIdAndTagId(@Param("songId") Long songId, @Param("tagId") Long tagId);

    int insertOrUpdate(SongTag songTag);

    int delete(@Param("songId") Long songId, @Param("tagId") Long tagId);

    List<Long> selectAllDistinctSongIds();
}
