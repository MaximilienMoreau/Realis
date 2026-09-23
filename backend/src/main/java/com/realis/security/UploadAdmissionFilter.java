package com.realis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realis.dto.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Reject abusive uploads before Spring parses or spools the multipart body. Single-instance limits. */
@Component @Order(-90) @RequiredArgsConstructor
public class UploadAdmissionFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final ClientIpResolver ips;
    private final ObjectMapper json;
    private final Semaphore concurrent = new Semaphore(4);
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
        boolean upload = req.getMethod().equals("POST") &&
            (req.getRequestURI().equals("/api/seal") || req.getRequestURI().equals("/api/verify"));
        if (!upload) { chain.doFilter(req, res); return; }
        if (!limiter.tryAcquire("upload:" + ips.resolve(req), 30) || !concurrent.tryAcquire()) {
            res.setStatus(429); res.setContentType("application/json"); res.setHeader("Retry-After", "60");
            json.writeValue(res.getWriter(), ErrorResponse.of(429, "Too Many Requests", "Trop d’envois en cours. Réessayez dans une minute."));
            return;
        }
        try { chain.doFilter(req, res); }
        finally { concurrent.release(); }
    }
}
