package com.checkpoint.auth;

import com.checkpoint.auth.dto.AuthResponse;
import com.checkpoint.user.User;
import com.checkpoint.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse guestLogin(String deviceId) {
        User user =
                userRepository
                        .findByDeviceId(deviceId)
                        .orElseGet(() -> userRepository.save(new User(deviceId)));
        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(user.getId(), token);
    }
}
