package org.L2.recommend.infrastructure;

import org.L2.recommend.domain.model.TagDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagDefinitionMapper {

    List<TagDefinition> selectAllOrderByDimIndex();

    TagDefinition selectById(@Param("id") Long id);

    TagDefinition selectByName(@Param("name") String name);

    int insert(TagDefinition tagDefinition);

    int update(TagDefinition tagDefinition);

    Integer selectMaxDimIndex();

    int selectCount();
}
