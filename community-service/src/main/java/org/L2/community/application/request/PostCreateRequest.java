package org.L2.community.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 发布帖子请求。
 */
@Data
@Accessors(chain = true)
public class PostCreateRequest {

    private Long userId;
    private String title;
    private String content;
}
