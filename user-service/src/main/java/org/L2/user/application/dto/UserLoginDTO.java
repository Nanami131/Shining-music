package org.L2.user.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserLoginDTO {
    private UserBaseDTO userBaseDTO;
    private String token;
    private String deviceCode;
}
