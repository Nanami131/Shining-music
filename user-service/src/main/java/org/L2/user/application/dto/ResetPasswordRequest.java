package org.L2.user.application.dto;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ResetPasswordRequest {
    private String username;
    private String phone;
    private String email;
    private String Password;
}