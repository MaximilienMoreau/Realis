package com.realis.service;

import com.realis.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService : génération et validation des jetons")
class JwtServiceTest {

    // 32+ octets, requis pour HS256
    private static final String VALID_SECRET = "a".repeat(32);

    private JwtService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new JwtService(new JwtProperties(VALID_SECRET, 86_400_000L));
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Constructeur : secret trop court → IllegalArgumentException")
    void constructor_shortSecret_throwsIllegalArgumentException() {
        JwtProperties shortSecretProps = new JwtProperties("trop_court", 86_400_000L);

        assertThatThrownBy(() -> new JwtService(shortSecretProps))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32");
    }

    @Test
    @DisplayName("generateToken() puis isValid() : le jeton généré est valide")
    void generateToken_thenIsValid_returnsTrue() {
        String token = service.generateToken(userId, "test@realis.fr");

        assertThat(service.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("extractUserId() : retrouve l'UUID encodé dans le jeton")
    void extractUserId_returnsOriginalUserId() {
        String token = service.generateToken(userId, "test@realis.fr");

        assertThat(service.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("extractEmail() : retrouve l'email encodé dans le jeton")
    void extractEmail_returnsOriginalEmail() {
        String token = service.generateToken(userId, "test@realis.fr");

        assertThat(service.extractEmail(token)).isEqualTo("test@realis.fr");
    }

    @Test
    @DisplayName("isValid() : jeton malformé → false")
    void isValid_malformedToken_returnsFalse() {
        assertThat(service.isValid("ceci-nest-pas-un-jwt")).isFalse();
    }

    @Test
    @DisplayName("isValid() : jeton signé avec un autre secret → false")
    void isValid_tokenSignedWithDifferentSecret_returnsFalse() {
        JwtService otherService = new JwtService(new JwtProperties("b".repeat(32), 86_400_000L));
        String token = otherService.generateToken(userId, "test@realis.fr");

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid() : jeton expiré → false")
    void isValid_expiredToken_returnsFalse() {
        JwtService shortLivedService = new JwtService(new JwtProperties(VALID_SECRET, -1_000L));
        String token = shortLivedService.generateToken(userId, "test@realis.fr");

        assertThat(service.isValid(token)).isFalse();
    }
}
