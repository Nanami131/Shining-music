package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserBaseDTO {
    private Long id;
    private String nickName;
    private String avatarUrl;
    private String signature;
    private String email;
}
