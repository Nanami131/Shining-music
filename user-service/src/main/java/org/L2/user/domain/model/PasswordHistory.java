package org.L2.user.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
/*
 查询历史密码用的
 */
@Data
@Accessors(chain = true)
public class PasswordHistory {
    private Long id;
    private Long userId;
    private String password;
    private String salt;
    private LocalDateTime createdAt;
}