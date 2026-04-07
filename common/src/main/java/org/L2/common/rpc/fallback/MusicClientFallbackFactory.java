package org.L2.common.rpc.fallback;

import lombok.extern.slf4j.Slf4j;
import org.L2.common.R;
import org.L2.common.rpc.MusicClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MusicClientFallbackFactory implements FallbackFactory<MusicClient> {

    @Override
    public MusicClient create(Throwable cause) {
        log.warn("MusicClient fallback triggered: {}", cause.getMessage());
        return userId -> R.error("音乐服务暂时不可用");
    }
}
