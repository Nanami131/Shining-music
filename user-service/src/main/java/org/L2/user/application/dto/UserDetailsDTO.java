package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserDetailsDTO {
    private Long id;
    private String username;
    private String avatarUrl;
    private String signature;
    private String token;
    private String email;
    private String phone;
    private String profile;
    private Byte role;
}
