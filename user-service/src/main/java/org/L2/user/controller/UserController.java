package org.L2.user.controller;

import org.L2.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @GetMapping("/test")
    public R test() {
        return R.success("Test successful", "Hello from user-service!");
    }
}