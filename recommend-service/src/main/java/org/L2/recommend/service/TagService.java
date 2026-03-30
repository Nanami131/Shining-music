package org.L2.recommend.service;

import org.L2.common.R;
import org.L2.recommend.domain.model.SongTag;
import org.L2.recommend.domain.model.TagDefinition;
import org.L2.recommend.infrastructure.SongTagMapper;
import org.L2.recommend.infrastructure.TagDefinitionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TagService {

    @Autowired
    private TagDefinitionMapper tagDefinitionMapper;

    @Autowired
    private SongTagMapper songTagMapper;

    @Autowired
    private TagVectorService tagVectorService;

    // ─── 标签定义 CRUD ───

    public R createTagDefinition(TagDefinition def) {
        try {
            if (tagDefinitionMapper.selectByName(def.getName()) != null) {
                return R.error("标签名已存在: " + def.getName());
            }
            if (def.getDimIndex() == null) {
                Integer max = tagDefinitionMapper.selectMaxDimIndex();
                def.setDimIndex(max == null ? 0 : max + 1);
            }
            tagDefinitionMapper.insert(def);
            tagVectorService.reloadDimensions();
            return R.success("标签定义创建成功", def);
        } catch (Exception e) {
            return R.error("创建标签定义失败: " + e.getMessage());
        }
    }

    public R getAllTagDefinitions() {
        try {
            List<TagDefinition> all = tagDefinitionMapper.selectAllOrderByDimIndex();
            Map<String, List<TagDefinition>> grouped = all.stream()
                    .collect(Collectors.groupingBy(TagDefinition::getCategory, LinkedHashMap::new, Collectors.toList()));
            return R.success("查询成功", grouped);
        } catch (Exception e) {
            return R.error("查询标签定义失败: " + e.getMessage());
        }
    }

    public R updateTagDefinition(Long tagId, TagDefinition def) {
        try {
            TagDefinition existing = tagDefinitionMapper.selectById(tagId);
            if (existing == null) {
                return R.error("标签定义不存在: id=" + tagId);
            }
            def.setId(tagId);
            tagDefinitionMapper.update(def);
            tagVectorService.reloadDimensions();
            return R.success("标签定义更新成功", tagDefinitionMapper.selectById(tagId));
        } catch (Exception e) {
            return R.error("更新标签定义失败: " + e.getMessage());
        }
    }

    // ─── 歌曲标签操作 ───

    public R getSongTags(Long songId) {
        try {
            List<SongTag> tags = songTagMapper.selectBySongId(songId);
            return R.success("查询成功", tags);
        } catch (Exception e) {
            return R.error("查询歌曲标签失败: " + e.getMessage());
        }
    }

    @Transactional
    public R updateSongTag(Long songId, Long tagId, SongTag songTag) {
        try {
            TagDefinition def = tagDefinitionMapper.selectById(tagId);
            if (def == null) {
                return R.error("标签定义不存在: id=" + tagId);
            }
            songTag.setSongId(songId).setTagId(tagId);
            if (songTag.getSource() == null) {
                songTag.setSource("manual_fix");
            }
            if (songTag.getConfidence() == null) {
                songTag.setConfidence(1.0f);
            }
            if (songTag.getReviewStatus() == null) {
                songTag.setReviewStatus("accepted");
            }
            songTagMapper.insertOrUpdate(songTag);
            tagVectorService.rebuildVector(songId);
            return R.success("歌曲标签更新成功");
        } catch (Exception e) {
            return R.error("更新歌曲标签失败: " + e.getMessage());
        }
    }

    @Transactional
    public R deleteSongTag(Long songId, Long tagId) {
        try {
            songTagMapper.delete(songId, tagId);
            tagVectorService.rebuildVector(songId);
            return R.success("歌曲标签删除成功");
        } catch (Exception e) {
            return R.error("删除歌曲标签失败: " + e.getMessage());
        }
    }
}
