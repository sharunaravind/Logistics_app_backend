package com.example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174"}) // Added your React dev port
@RestController
@RequestMapping("/api/v1/secure")
public class SecureController {

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong (secure)");
    }
}
