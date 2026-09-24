package org.L2.music.infrastructure;
import org.L2.music.domain.model.Playlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlaylistMapper {
    Playlist selectById(Long id);

    int insert(Playlist playlist);

    int update(Playlist playlist);

    int deleteById(Long id);

    List<Playlist> query(Playlist playlist);

    List<Playlist> selectVisiblePage(@Param("userId") Long userId,
                                     @Param("includeOwn") boolean includeOwn,
                                     @Param("offset") long offset,
                                     @Param("size") Integer size,
                                     @Param("search") String search);

    long countVisible(@Param("userId") Long userId, @Param("includeOwn") boolean includeOwn,
                      @Param("search") String search);

    List<Playlist> selectOwnPage(@Param("userId") Long userId,
                                 @Param("offset") long offset, @Param("size") int size);

    long countOwn(@Param("userId") Long userId);

    List<Playlist> selectPublicOwnerPage(@Param("creatorId") Long creatorId,
                                          @Param("offset") long offset, @Param("size") Integer size);

    long countPublicOwner(@Param("creatorId") Long creatorId);
}
