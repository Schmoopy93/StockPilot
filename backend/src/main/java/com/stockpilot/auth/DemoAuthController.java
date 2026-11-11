package com.stockpilot.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Profile("demo")
public class DemoAuthController {

    @GetMapping("/me")
    public ResponseEntity<Void> currentUser() {
        return ResponseEntity.noContent().build();
    }
}
