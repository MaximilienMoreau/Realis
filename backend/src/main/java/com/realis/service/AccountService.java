package com.realis.service;

import com.realis.repository.*;
import com.realis.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class AccountService {
    private final UserRepository users;
    private final SealedRecordRepository records;
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final AppProperties app;
    private final PasswordEncoder passwords;
    private final HashService hashes;
    @Value("${realis.mail.from:realis@localhost}") private String from;

    @Transactional
    public void request(String email, String purpose) {
        var user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || user.getDeletedAt() != null || (purpose.equals("VERIFY") && user.isEmailVerified())) return;
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String digest = hashes.sha256Hex(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("insert into account_tokens(digest,user_id,purpose,expires_at) values (?,?,?,?)",
            digest, user.getId(), purpose, java.sql.Timestamp.from(Instant.now().plusSeconds(purpose.equals("RESET") ? 1800 : 86400)));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail());
        message.setSubject(purpose.equals("RESET") ? "Réinitialiser votre mot de passe Realis" : "Vérifier votre adresse Realis");
        // Fragment keeps the secret out of HTTP access logs and Referer headers.
        message.setText("Ouvrez ce lien puis confirmez l'action :\n" + app.frontendUrl() +
            "/compte#action=" + purpose.toLowerCase(Locale.ROOT) + "&token=" + token +
            "\nCe lien est à usage unique. Si vous n'êtes pas à l'origine de la demande, ignorez ce message.");
        try { mail.send(message); }
        catch (org.springframework.mail.MailException e) {
            jdbc.update("delete from account_tokens where digest = ?", digest);
            log.error("Envoi d'email impossible : vérifier la configuration SMTP ({})", e.getClass().getSimpleName());
        }
    }

    @Transactional
    public void consume(String token, String purpose, String password) {
        if (token == null || token.length() != 43) throw new IllegalArgumentException("Lien invalide ou expiré");
        if (purpose.equals("RESET")) validatePassword(password);
        String digest = hashes.sha256Hex(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Serialize all operations for this user before consuming a token.
        var ids = jdbc.query("select user_id from account_tokens where digest = ? and purpose = ? and expires_at > now()",
            (rs, n) -> rs.getObject(1, UUID.class), digest, purpose);
        if (ids.isEmpty()) throw new IllegalArgumentException("Lien invalide ou expiré");
        var user = users.lockById(ids.get(0)).orElseThrow(() -> new IllegalArgumentException("Lien invalide ou expiré"));
        if (user.getDeletedAt() != null || jdbc.update("delete from account_tokens where digest = ? and expires_at > now()", digest) != 1)
            throw new IllegalArgumentException("Lien invalide ou expiré");
        if (purpose.equals("VERIFY")) user.setEmailVerified(true);
        else {
            user.setPasswordHash(passwords.encode(password));
            user.setTokenVersion(user.getTokenVersion() + 1);
            jdbc.update("delete from account_tokens where user_id = ? and purpose = 'RESET'", user.getId());
        }
        users.save(user);
    }

    @Transactional
    public void delete(UUID id, String password) {
        var user = users.lockById(id).orElseThrow(() -> new BadCredentialsException("Compte introuvable"));
        if (password == null || !passwords.matches(password, user.getPasswordHash()))
            throw new BadCredentialsException("Mot de passe incorrect");
        user.setDeletedAt(Instant.now()); user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);
        for (var record : records.findByUserId(id)) {
            if (record.getDeletedAt() == null) record.setDeletedAt(Instant.now());
        }
        jdbc.update("delete from account_tokens where user_id = ?", id);
    }
    private void validatePassword(String value) {
        if (value == null || value.length() < 12 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mot de passe : au moins 12 caractères et au plus 72 octets UTF-8");
    }
}
