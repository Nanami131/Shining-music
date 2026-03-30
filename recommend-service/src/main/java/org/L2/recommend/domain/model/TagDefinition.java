package org.L2.recommend.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class TagDefinition {
    private Long id;
    private String name;
    private String category;
    private String labelZh;
    private Integer dimIndex;
    private String valueType;
    private String enumValues;
    private LocalDateTime createdAt;
}
