package org.L2.music.domain.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import org.L2.music.application.dto.SearchResultDTO;
import org.L2.music.domain.model.MusicSearchDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    public static final String INDEX_NAME = "music_search";

    @Autowired
    private ElasticsearchClient esClient;

    /**
     * 创建索引（如果不存在）。
     * title / singerName 使用 ik_max_word（需安装 IK 插件），
     * lyricsZh 使用 ik_max_word，lyricsJa / lyricsEn 使用 standard。
     */
    public void createIndexIfNotExists() throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
        if (exists) {
            log.info("ES index [{}] already exists, skip creation", INDEX_NAME);
            return;
        }

        CreateIndexResponse resp = esClient.indices().create(c -> c
                .index(INDEX_NAME)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(m -> m
                        .properties("songId", p -> p.long_(l -> l))
                        .properties("title", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
                        ))
                        .properties("singerId", p -> p.long_(l -> l))
                        .properties("singerName", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
                        ))
                        .properties("coverUrl", p -> p.keyword(k -> k.index(false)))
                        .properties("lyricsZh", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                        ))
                        .properties("lyricsJa", p -> p.text(t -> t
                                .analyzer("standard")
                        ))
                        .properties("lyricsEn", p -> p.text(t -> t
                                .analyzer("standard")
                        ))
                )
        );
        log.info("ES index [{}] created, acknowledged={}", INDEX_NAME, resp.acknowledged());
    }

    /**
     * 批量索引文档。
     */
    public void bulkIndex(List<MusicSearchDoc> docs) throws IOException {
        if (docs == null || docs.isEmpty()) return;

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (MusicSearchDoc doc : docs) {
            br.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
                            .id(String.valueOf(doc.getSongId()))
                            .document(doc)
                    )
            );
        }

        BulkResponse result = esClient.bulk(br.build());
        if (result.errors()) {
            for (BulkResponseItem item : result.items()) {
                if (item.error() != null) {
                    log.error("Bulk index error for songId={}: {}", item.id(), item.error().reason());
                }
            }
        } else {
            log.info("Bulk indexed {} documents into [{}]", docs.size(), INDEX_NAME);
        }
    }

    /**
     * 索引单个文档（增量更新）。
     */
    public void indexDoc(MusicSearchDoc doc) throws IOException {
        esClient.index(i -> i
                .index(INDEX_NAME)
                .id(String.valueOf(doc.getSongId()))
                .document(doc)
        );
    }

    /**
     * 搜索：多字段匹配，标题和歌手权重更高，歌词命中返回高亮片段。
     */
    public List<SearchResultDTO> search(String keyword, int from, int size) throws IOException {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        SearchResponse<MusicSearchDoc> response = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .bool(b -> b
                                        .should(sh -> sh.match(m -> m.field("title").query(keyword).boost(3.0f)))
                                        .should(sh -> sh.match(m -> m.field("singerName").query(keyword).boost(2.0f)))
                                        .should(sh -> sh.match(m -> m.field("lyricsZh").query(keyword).boost(1.0f)))
                                        .should(sh -> sh.match(m -> m.field("lyricsJa").query(keyword).boost(1.0f)))
                                        .should(sh -> sh.match(m -> m.field("lyricsEn").query(keyword).boost(1.0f)))
                                        .minimumShouldMatch("1")
                                )
                        )
                        .highlight(h -> h
                                .fields("title", f -> f)
                                .fields("singerName", f -> f)
                                .fields("lyricsZh", f -> f.fragmentSize(100).numberOfFragments(3))
                                .fields("lyricsJa", f -> f.fragmentSize(100).numberOfFragments(3))
                                .fields("lyricsEn", f -> f.fragmentSize(100).numberOfFragments(3))
                        )
                        .from(from)
                        .size(size),
                MusicSearchDoc.class
        );

        List<SearchResultDTO> results = new ArrayList<>();
        for (Hit<MusicSearchDoc> hit : response.hits().hits()) {
            MusicSearchDoc doc = hit.source();
            if (doc == null) continue;

            SearchResultDTO dto = new SearchResultDTO()
                    .setSongId(doc.getSongId())
                    .setTitle(doc.getTitle())
                    .setSingerId(doc.getSingerId())
                    .setSingerName(doc.getSingerName())
                    .setCoverUrl(doc.getCoverUrl())
                    .setScore(hit.score() != null ? hit.score().floatValue() : 0f)
                    .setHighlights(hit.highlight());
            results.add(dto);
        }
        return results;
    }

    /**
     * 删除单个文档。
     */
    public void deleteDoc(Long songId) {
        try {
            esClient.delete(d -> d.index(INDEX_NAME).id(String.valueOf(songId)));
        } catch (IOException e) {
            log.error("Failed to delete doc songId={} from ES", songId, e);
        }
    }
}
