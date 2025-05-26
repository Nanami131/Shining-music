package org.L2.user.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickName;
    private String salt;
    private String phone;
    private String email;
    private String avatarUrl;
    private String signature;
    private String profile;
    private Byte role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}