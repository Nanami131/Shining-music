package org.L2.recommend.controller;

import org.L2.common.R;
import org.L2.recommend.domain.model.SongTag;
import org.L2.recommend.domain.model.TagDefinition;
import org.L2.recommend.service.RecommendationService;
import org.L2.recommend.service.TagService;
import org.L2.recommend.service.TagVectorService;
import org.L2.recommend.service.UserPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Autowired
    private TagService tagService;

    @Autowired
    private TagVectorService tagVectorService;

    @Autowired
    private UserPreferenceService userPreferenceService;

    @Autowired
    private RecommendationService recommendationService;

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

    // ─── 推荐 ───

    @GetMapping("/daily")
    public R getDailyRecommendation(@RequestParam("userId") Long userId,
                                    @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return recommendationService.recommend(userId, limit);
    }

    @GetMapping("/daily/content-based")
    public R getContentBasedRecommendation(@RequestParam("userId") Long userId,
                                           @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return recommendationService.recommendContentBased(userId, limit);
    }

    @GetMapping("/daily/item-cf")
    public R getItemCFRecommendation(@RequestParam("userId") Long userId,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return recommendationService.recommendItemCF(userId, limit);
    }

    @PostMapping("/itemcf/rebuild")
    public R rebuildItemCFMatrix() {
        return recommendationService.rebuildItemCFMatrix();
    }

    @GetMapping("/preference")
    public R getUserPreference(@RequestParam("userId") Long userId) {
        float[] vector = userPreferenceService.getPreferenceVector(userId);
        if (vector == null) {
            return R.error("暂无偏好数据，请先播放一些歌曲");
        }
        return R.success("查询成功", vector);
    }

    @PostMapping("/preference/rebuild")
    public R rebuildPreference(@RequestParam("userId") Long userId) {
        int count = userPreferenceService.rebuildFromHistory(userId);
        return R.success("偏好向量重建完成，处理 " + count + " 条记录", count);
    }
}
