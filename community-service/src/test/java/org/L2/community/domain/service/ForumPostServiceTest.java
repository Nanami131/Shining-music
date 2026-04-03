package org.L2.community.domain.service;

import org.L2.common.R;
import org.L2.community.domain.model.ForumPost;
import org.L2.community.infrastructure.mapper.ForumPostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForumPostService 单元测试")
class ForumPostServiceTest {

    @InjectMocks
    private ForumPostService forumPostService;

    @Mock
    private ForumPostMapper forumPostMapper;

    @Nested
    @DisplayName("createPost()")
    class CreateTests {

        @Test
        @DisplayName("null参数 — 返回信息不完整")
        void nullPost() {
            R result = forumPostService.createPost(null);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("信息不完整"));
        }

        @Test
        @DisplayName("userId为null — 返回信息不完整")
        void nullUserId() {
            ForumPost post = new ForumPost().setTitle("t").setContent("c");
            R result = forumPostService.createPost(post);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("标题为空 — 返回信息不完整")
        void blankTitle() {
            ForumPost post = new ForumPost().setUserId(1L).setTitle("  ").setContent("c");
            R result = forumPostService.createPost(post);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("内容为空 — 返回信息不完整")
        void blankContent() {
            ForumPost post = new ForumPost().setUserId(1L).setTitle("t").setContent("");
            R result = forumPostService.createPost(post);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("正常创建 — 返回成功")
        void success() {
            ForumPost post = new ForumPost().setUserId(1L).setTitle("标题").setContent("内容");
            doNothing().when(forumPostMapper).insert(any());

            R result = forumPostService.createPost(post);
            assertTrue(result.getPassed());
            verify(forumPostMapper).insert(post);
        }

        @Test
        @DisplayName("数据库异常 — 返回失败")
        void dbException() {
            ForumPost post = new ForumPost().setUserId(1L).setTitle("标题").setContent("内容");
            doThrow(new RuntimeException("DB error")).when(forumPostMapper).insert(any());

            R result = forumPostService.createPost(post);
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("发帖失败"));
        }
    }

    @Nested
    @DisplayName("updatePost()")
    class UpdateTests {

        @Test
        @DisplayName("null参数 — 返回错误")
        void nullPost() {
            R result = forumPostService.updatePost(null);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("id为null — 返回错误")
        void nullId() {
            R result = forumPostService.updatePost(new ForumPost());
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("帖子ID不能为空"));
        }

        @Test
        @DisplayName("帖子不存在 — 返回错误")
        void notFound() {
            when(forumPostMapper.selectById(99L)).thenReturn(null);
            R result = forumPostService.updatePost(new ForumPost().setId(99L));
            assertFalse(result.getPassed());
            assertTrue(result.getMessage().contains("帖子不存在"));
        }

        @Test
        @DisplayName("正常更新 — 返回成功")
        void success() {
            ForumPost existing = new ForumPost().setId(1L).setTitle("old");
            when(forumPostMapper.selectById(1L)).thenReturn(existing);
            doNothing().when(forumPostMapper).update(any());

            R result = forumPostService.updatePost(new ForumPost().setId(1L).setTitle("new"));
            assertTrue(result.getPassed());
        }
    }

    @Nested
    @DisplayName("deletePost()")
    class DeleteTests {

        @Test
        @DisplayName("id为null — 返回错误")
        void nullId() {
            R result = forumPostService.deletePost(null);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("帖子不存在 — 返回错误")
        void notFound() {
            when(forumPostMapper.selectById(99L)).thenReturn(null);
            R result = forumPostService.deletePost(99L);
            assertFalse(result.getPassed());
        }

        @Test
        @DisplayName("正常删除 — 返回成功")
        void success() {
            when(forumPostMapper.selectById(1L)).thenReturn(new ForumPost().setId(1L));
            doNothing().when(forumPostMapper).deleteById(1L);

            R result = forumPostService.deletePost(1L);
            assertTrue(result.getPassed());
        }
    }

    @Nested
    @DisplayName("getPostById()")
    class GetByIdTests {

        @Test
        @DisplayName("id为null — 返回null")
        void nullId() {
            assertNull(forumPostService.getPostById(null));
        }

        @Test
        @DisplayName("正常查询 — 返回帖子")
        void found() {
            ForumPost post = new ForumPost().setId(1L).setTitle("test");
            when(forumPostMapper.selectById(1L)).thenReturn(post);
            assertEquals(post, forumPostService.getPostById(1L));
        }
    }

    @Nested
    @DisplayName("queryPosts()")
    class QueryTests {

        @Test
        @DisplayName("返回列表")
        void normalQuery() {
            List<ForumPost> expected = List.of(new ForumPost().setId(1L));
            when(forumPostMapper.query(any())).thenReturn(expected);
            assertEquals(expected, forumPostService.queryPosts(new ForumPost()));
        }
    }
}
