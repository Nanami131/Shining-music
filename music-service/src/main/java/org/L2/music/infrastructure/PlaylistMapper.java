package org.L2.music.infrastructure;
import org.L2.music.domain.model.Playlist;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlaylistMapper {
    Playlist selectById(Long id);

    int insert(Playlist playlist);

    int update(Playlist playlist);

    int deleteById(Long id);

    List<Playlist> query(Playlist playlist);
}