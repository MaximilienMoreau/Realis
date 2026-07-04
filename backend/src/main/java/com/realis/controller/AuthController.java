package com.realis.controller;

import com.realis.dto.LoginRequest;
import com.realis.dto.RegisterRequest;
import com.realis.dto.RegisterResponse;
import com.realis.exception.TooManyRequestsException;
import com.realis.model.User;
import com.realis.repository.UserRepository;
import com.realis.security.RateLimiter;
import com.realis.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
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

    // Volontairement permissif : protège contre le bruteforce automatisé sans
    // gêner un utilisateur qui se trompe quelques fois de mot de passe.
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        checkRateLimit("register", httpRequest);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà pour cet email");
        }
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.password()))
            .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RegisterResponse(token, user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<RegisterResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        checkRateLimit("login", httpRequest);
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new RegisterResponse(token, user.getId(), user.getEmail()));
    }

    private void checkRateLimit(String scope, HttpServletRequest httpRequest) {
        String key = scope + ":" + httpRequest.getRemoteAddr();
        if (!rateLimiter.tryAcquire(key, MAX_ATTEMPTS_PER_MINUTE)) {
            throw new TooManyRequestsException("Trop de tentatives, réessayez dans une minute.");
        }
    }

    // Évite que "Test@Mail.com" et "test@mail.com" soient traités comme deux comptes
    // distincts (la colonne users.email est UNIQUE mais sensible à la casse en base).
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
