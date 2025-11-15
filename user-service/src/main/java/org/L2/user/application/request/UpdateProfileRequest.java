package org.L2.user.application.request;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UpdateProfileRequest {
    private Long id;
    private String phone;
    private String email;
    private String nickName;
    private String avatarUrl;
    private String signature;
    private String profile;
}
