package com.realis.controller;

import com.realis.service.*;
import com.realis.repository.*;
import com.realis.security.*;
import com.realis.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;
    private final UserRepository users;
    private final SealedRecordRepository records;
    private final RateLimiter limiter;
    private final ClientIpResolver ips;
    @org.springframework.beans.factory.annotation.Value("${realis.storage.quota-bytes:5368709120}") private long quota;
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record Token(@NotBlank @Size(max=100) String token, String password) {}
    public record Password(String password) {}
    @GetMapping("/api/policy")
    public Map<String, Object> policy() {
        return Map.of("version", ConsentPolicy.VERSION, "text", ConsentPolicy.TEXT, "retentionDays", ConsentPolicy.DAYS);
    }
    @PostMapping({"/api/auth/forgot-password", "/api/auth/resend-verification"})
    public Map<String,String> request(@Valid @RequestBody EmailRequest body, HttpServletRequest req) {
        limit(req);
        accounts.request(body.email(), req.getRequestURI().endsWith("forgot-password") ? "RESET" : "VERIFY");
        return Map.of("message", "Si cette adresse correspond à un compte concerné, un email sera envoyé.");
    }
    @PostMapping({"/api/auth/reset-password", "/api/auth/verify-email"})
    public void consume(@Valid @RequestBody Token body, HttpServletRequest req) {
        limit(req);
        accounts.consume(body.token(), req.getRequestURI().endsWith("reset-password") ? "RESET" : "VERIFY", body.password());
    }
    @GetMapping("/api/account")
    public Map<String,Object> me(Authentication auth) {
        var user = users.findById((UUID)auth.getPrincipal()).orElseThrow();
        return Map.of("email", user.getEmail(), "emailVerified", user.isEmailVerified(),
            "storageUsed", records.storageUsed(user.getId()), "storageQuota", quota);
    }
    @DeleteMapping("/api/account")
    public void delete(@RequestBody Password body, Authentication auth, HttpServletRequest req) {
        limit(req); accounts.delete((UUID)auth.getPrincipal(), body.password());
    }
    private void limit(HttpServletRequest req) {
        if (!limiter.tryAcquire("account:" + ips.resolve(req), 5))
            throw new TooManyRequestsException("Trop de demandes. Réessayez dans une minute.");
    }
}
