package com.realis.controller;

import com.realis.service.ProofDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.file.*;
import java.util.UUID;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seal")
public class ProofDownloadController {
    private final ProofDownloadService downloads;
    @GetMapping({"/{id}/original", "/{id}/export"})
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, Authentication auth,
            jakarta.servlet.http.HttpServletRequest request) throws IOException {
        boolean archive = request.getRequestURI().endsWith("/export");
        Path prepared = downloads.prepare(id, (UUID) auth.getPrincipal(), archive);
        StreamingResponseBody body = out -> {
            try { Files.copy(prepared, out); }
            finally { Files.deleteIfExists(prepared); }
        };
        return ResponseEntity.ok().contentType(archive ? MediaType.parseMediaType("application/zip") : MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"realis-" + id + (archive ? ".zip" : ".bin") + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store").contentLength(Files.size(prepared)).body(body);
    }
}
