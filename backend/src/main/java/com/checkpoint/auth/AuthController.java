package com.checkpoint.auth;

import com.checkpoint.auth.dto.AuthResponse;
import com.checkpoint.auth.dto.GuestAuthRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/guest")
    public AuthResponse guest(@Valid @RequestBody GuestAuthRequest request) {
        return authService.guestLogin(request.deviceId());
    }
}
