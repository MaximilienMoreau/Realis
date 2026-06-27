package com.realis.service;

import com.realis.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtProperties props;

    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + props.expirationMs()))
            .signWith(secretKey())
            .compact();
    }

    public boolean isValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT invalide : {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(claims(token).getSubject());
    }

    public String extractEmail(String token) {
        return claims(token).get("email", String.class);
    }

    private Claims claims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }
}
