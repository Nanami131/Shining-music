package org.L2.common.rpc;

import org.L2.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "music-service")
public interface MusicClient {

    /**
     * 生成用户播放列表
     * @param userId 用户ID
     * @return
     */
    @PostMapping("music/curPlaylist")
    public R createUserCurrentPlaylist(@RequestBody Long userId);

}