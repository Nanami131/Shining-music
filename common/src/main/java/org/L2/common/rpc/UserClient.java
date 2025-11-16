package org.L2.common.rpc;

import org.L2.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/user/info")
    R getUserBaseInfo(@RequestParam("userId") Long userId);
}
