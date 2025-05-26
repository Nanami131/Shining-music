package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserDTO {
    private Long id;
    private String username;
    private String avatarUrl;
    private String signature;
    private String profile;

}
