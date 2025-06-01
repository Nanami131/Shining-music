package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

@Data
@Accessors(chain = true)
public class UserAvatarRequest {
    private Long id;
    private MultipartFile avatarFile;
    private String md5;
}
