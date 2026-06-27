package com.realis.controller;

import com.realis.dto.LoginRequest;
import com.realis.dto.RegisterRequest;
import com.realis.dto.RegisterResponse;
import com.realis.model.User;
import com.realis.repository.UserRepository;
import com.realis.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Un compte existe déjà pour cet email");
        }
        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RegisterResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<RegisterResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new RegisterResponse(token, user.getId(), user.getEmail()));
    }
}
