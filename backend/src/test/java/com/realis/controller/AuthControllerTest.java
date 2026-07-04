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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController : inscription et connexion")
class AuthControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest httpRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepository, passwordEncoder, jwtService, rateLimiter);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.1");
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(true);
    }

    @Test
    @DisplayName("register() : normalise l'email en minuscules avant vérification et sauvegarde")
    void register_normalizesEmailToLowercase() {
        when(userRepository.existsByEmail("test@realis.fr")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(UUID.randomUUID()).email(u.getEmail()).passwordHash(u.getPasswordHash()).build();
        });
        when(passwordEncoder.encode("motdepasse123")).thenReturn("hashed");
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        RegisterRequest request = new RegisterRequest("Test@Realis.FR", "motdepasse123");
        ResponseEntity<RegisterResponse> response = controller.register(request, httpRequest);

        assertThat(response.getBody().email()).isEqualTo("test@realis.fr");
        verify(userRepository).existsByEmail("test@realis.fr");
    }

    @Test
    @DisplayName("register() : email déjà utilisé → IllegalArgumentException")
    void register_duplicateEmail_throwsIllegalArgumentException() {
        when(userRepository.existsByEmail("test@realis.fr")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("test@realis.fr", "motdepasse123");

        assertThatThrownBy(() -> controller.register(request, httpRequest))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("register() : trop de tentatives → TooManyRequestsException")
    void register_rateLimited_throwsTooManyRequestsException() {
        when(rateLimiter.tryAcquire(any(), anyInt())).thenReturn(false);

        RegisterRequest request = new RegisterRequest("test@realis.fr", "motdepasse123");

        assertThatThrownBy(() -> controller.register(request, httpRequest))
            .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("login() : email insensible à la casse")
    void login_emailIsCaseInsensitive() {
        User user = User.builder().id(UUID.randomUUID()).email("test@realis.fr").passwordHash("hashed").build();
        when(userRepository.findByEmail("test@realis.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("motdepasse123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        LoginRequest request = new LoginRequest("Test@Realis.FR", "motdepasse123");
        ResponseEntity<RegisterResponse> response = controller.login(request, httpRequest);

        assertThat(response.getBody().email()).isEqualTo("test@realis.fr");
    }

    @Test
    @DisplayName("login() : mot de passe incorrect → BadCredentialsException")
    void login_wrongPassword_throwsBadCredentialsException() {
        User user = User.builder().id(UUID.randomUUID()).email("test@realis.fr").passwordHash("hashed").build();
        when(userRepository.findByEmail("test@realis.fr")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais", "hashed")).thenReturn(false);

        LoginRequest request = new LoginRequest("test@realis.fr", "mauvais");

        assertThatThrownBy(() -> controller.login(request, httpRequest))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login() : email inconnu → BadCredentialsException (pas de fuite d'information)")
    void login_unknownEmail_throwsBadCredentialsException() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("inconnu@realis.fr", "motdepasse123");

        assertThatThrownBy(() -> controller.login(request, httpRequest))
            .isInstanceOf(BadCredentialsException.class);
    }
}
