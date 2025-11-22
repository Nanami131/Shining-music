package org.L2.community.application.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更新帖子请求。
 */
@Data
@Accessors(chain = true)
public class PostUpdateRequest {

    private Long id;
    private String title;
    private String content;

}
