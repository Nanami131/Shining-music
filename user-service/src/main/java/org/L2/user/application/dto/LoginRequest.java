package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LoginRequest {
    private String username;
    private String password;
    private String phone;
    private String email;
}