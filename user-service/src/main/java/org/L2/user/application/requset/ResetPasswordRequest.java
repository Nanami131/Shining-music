package org.L2.user.application.requset;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ResetPasswordRequest {
    private Long id;
    private String username;
    private String phone;
    private String email;
    private String oldPassword;
    private String newPassword;
}