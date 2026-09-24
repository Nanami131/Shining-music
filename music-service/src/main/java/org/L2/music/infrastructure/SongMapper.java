package org.L2.music.infrastructure;
import org.L2.music.domain.model.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SongMapper {
    Song selectById(Long id);

    List<Song> selectByIds(@Param("ids") Collection<Long> ids);

    int insert(Song song);

    int update(Song song);

    int deleteById(Long id);

    List<Song> query(Song song);

    List<Song> selectPageActive(@Param("offset") long offset, @Param("size") int size);

    List<Song> selectSingerSongsPage(@Param("singerId") Long singerId,
                                     @Param("offset") long offset, @Param("size") int size);

    long countSingerSongs(@Param("singerId") Long singerId);

    long countActive();

    void deleteBySingerId(Long singerId);

    Float selectAvgLufs();

    int recalcVolumeGain(@Param("targetLufs") float targetLufs);
}
