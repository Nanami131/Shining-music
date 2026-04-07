package org.L2.common.rpc.fallback;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.rpc.UserClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.warn("UserClient fallback triggered: {}", cause.getMessage());
        return userId -> R.error("用户服务暂时不可用");
    }
}
