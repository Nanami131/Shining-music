package org.L2.user.application.dto;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UpdateProfileRequest {
    private String phone;
    private String email;
    private String avatarUrl;
    private String signature;
    private String profile;
}
