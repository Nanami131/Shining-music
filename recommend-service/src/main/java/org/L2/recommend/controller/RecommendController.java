package org.L2.recommend.controller;

import org.L2.common.R;
import org.L2.recommend.domain.model.SongTag;
import org.L2.recommend.domain.model.TagDefinition;
import org.L2.recommend.service.TagService;
import org.L2.recommend.service.TagVectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Autowired
    private TagService tagService;

    @Autowired
    private TagVectorService tagVectorService;

    // ─── 标签定义 ───

    @PostMapping("/tags")
    public R createTagDefinition(@RequestBody TagDefinition tagDefinition) {
        return tagService.createTagDefinition(tagDefinition);
    }

    @GetMapping("/tags")
    public R getAllTagDefinitions() {
        return tagService.getAllTagDefinitions();
    }

    @PutMapping("/tags/{tagId}")
    public R updateTagDefinition(@PathVariable(value = "tagId") Long tagId,
                                 @RequestBody TagDefinition tagDefinition) {
        return tagService.updateTagDefinition(tagId, tagDefinition);
    }

    // ─── 歌曲标签 ───

    @GetMapping("/songs/{songId}/tags")
    public R getSongTags(@PathVariable(value = "songId") Long songId) {
        return tagService.getSongTags(songId);
    }

    @PutMapping("/songs/{songId}/tags/{tagId}")
    public R updateSongTag(@PathVariable(value = "songId") Long songId,
                           @PathVariable(value = "tagId") Long tagId,
                           @RequestBody SongTag songTag) {
        return tagService.updateSongTag(songId, tagId, songTag);
    }

    @DeleteMapping("/songs/{songId}/tags/{tagId}")
    public R deleteSongTag(@PathVariable(value = "songId") Long songId,
                           @PathVariable(value = "tagId") Long tagId) {
        return tagService.deleteSongTag(songId, tagId);
    }

    // ─── 向量重建 ───

    @PostMapping("/songs/{songId}/rebuild-vector")
    public R rebuildVector(@PathVariable(value = "songId") Long songId) {
        try {
            tagVectorService.rebuildVector(songId);
            return R.success("向量重建完成: songId=" + songId);
        } catch (Exception e) {
            return R.error("向量重建失败: " + e.getMessage());
        }
    }

    @PostMapping("/songs/rebuild-all")
    public R rebuildAll() {
        return tagVectorService.rebuildAll();
    }

    // ─── 维度信息 ───

    @GetMapping("/dimensions")
    public R getDimensions() {
        return R.success("查询成功", tagVectorService.getDimensions());
    }

    @PostMapping("/dimensions/reload")
    public R reloadDimensions() {
        try {
            tagVectorService.reloadDimensions();
            return R.success("维度重载成功，当前 " + tagVectorService.getDimensionCount() + " 维");
        } catch (Exception e) {
            return R.error("维度重载失败: " + e.getMessage());
        }
    }
}
