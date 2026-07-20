package com.sentinelgateway.downstreamb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DownstreamBController {

    @GetMapping("/api/v2/hello")
    public String hello() {
        return "Hello from downstream-b!";
    }
}