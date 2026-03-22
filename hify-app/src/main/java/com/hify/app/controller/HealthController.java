package com.hify.app.controller;

import com.hify.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
