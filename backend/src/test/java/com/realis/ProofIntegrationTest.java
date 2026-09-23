package com.realis;

import com.realis.model.User;
import com.realis.repository.UserRepository;
import com.realis.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".+")
class ProofIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("management.health.mail.enabled", () -> "false");
        r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        r.add("spring.datasource.username", () -> "realis_test");
        r.add("spring.datasource.password", () -> "realis_test");
        r.add("realis.jwt.secret", () -> "test-only-secret-at-least-thirty-two-bytes-long");
        r.add("realis.tsa.provider", () -> "noop");
        r.add("realis.storage.path", () -> "/tmp/realis-integration-captures");
        r.add("realis.storage.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        r.add("realis.retention.interval-ms", () -> "3600000");
        r.add("realis.app.frontend-url", () -> "http://localhost:3000");
    }
    @Autowired MockMvc mvc;
    @Autowired SealingService sealing;
    @Autowired com.realis.security.RateLimiter rateLimiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired ProofLifecycleService lifecycle;
    @MockBean JavaMailSender mail;
    UUID owner; String token;
    static final byte[] VIDEO = "original video fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("realis_test");
        jdbc.execute("truncate users, consent_logs, sealed_records, proof_labels, account_tokens, deleted_proofs cascade");
        var user = users.save(User.builder().email("owner@example.test").passwordHash(passwords.encode("test-password-123"))
            .emailVerified(true).build());
        owner = user.getId(); token = jwt.generateToken(owner, user.getEmail());
        reset(mail);
        ((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(rateLimiter, "windows")).clear();
        org.springframework.test.util.ReflectionTestUtils.setField(sealing, "quotaBytes", 5368709120L);
    }
    String seal(UUID id) throws Exception {
        return mvc.perform(multipart("/api/seal").file(new MockMultipartFile("file", "video.webm", "video/webm", VIDEO))
            .param("captureId", id.toString()).param("consentAccepted", "true").param("geolocConsented", "false")
            .param("policyVersion", ConsentPolicy.VERSION).param("consentedAt", Instant.now().toString())
            .header("Authorization", "Bearer " + token)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    @Test void sealRetryExportAndOwnership() throws Exception {
        UUID id = UUID.randomUUID(); seal(id); seal(id);
        assertThat(jdbc.queryForObject("select count(*) from sealed_records", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select purpose_text from consent_logs", String.class)).isEqualTo(ConsentPolicy.TEXT);
        var original = mvc.perform(get("/api/seal/{id}/original", id).header("Authorization", "Bearer " + token))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(original)).andExpect(status().isOk()).andExpect(content().bytes(VIDEO));
        var archive = mvc.perform(get("/api/seal/{id}/export", id).header("Authorization", "Bearer " + token))
            .andExpect(request().asyncStarted()).andReturn();
        var bytes = mvc.perform(asyncDispatch(archive)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Set<String> names = new HashSet<>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("original.bin")) assertThat(zip.readAllBytes()).isEqualTo(VIDEO);
            }
        }
        assertThat(names).contains("original.bin", "certificat.pdf", "token.tsr", "sha256.txt", "LISEZ-MOI.txt");
        var other = users.save(User.builder().email("other@example.test").passwordHash("x").emailVerified(true).build());
        mvc.perform(get("/api/seal/{id}/original", id).header("Authorization", "Bearer " + jwt.generateToken(other.getId(), other.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/seal/{id}/original", id)).andExpect(status().isUnauthorized());
    }
    @Test void deletionHidesThenErasesAndKeepsOnlyTombstone() throws Exception {
        UUID id = UUID.randomUUID(); seal(id);
        String path = jdbc.queryForObject("select storage_path from sealed_records where id = ?", String.class, id);
        mvc.perform(delete("/api/seal/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        for (String suffix : List.of("", "/certificate", "/tsa"))
            mvc.perform(get("/api/verify/" + id + suffix)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/verify").file(new MockMultipartFile("file", VIDEO)).param("recordId", id.toString()))
            .andExpect(jsonPath("$.verdict").value("SUPPRIME")).andExpect(jsonPath("$.record").isEmpty());
        lifecycle.purge(id);
        assertThat(Files.exists(Path.of(path))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from consent_logs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sealed_records", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from deleted_proofs", Long.class)).isEqualTo(1);
    }
    @Test void postgresProtectsProofAndLabelsStayPrivate() throws Exception {
        UUID id = UUID.randomUUID(); seal(id);
        assertThatThrownBy(() -> jdbc.update("update sealed_records set sha256_hex = 'forged' where id = ?", id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from sealed_records where id = ?", id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        mvc.perform(patch("/api/seal/{id}/labels", id).header("Authorization", "Bearer " + token)
            .contentType("application/json").content("{\"title\":\"Cuisine\",\"folder\":\"Appartement A\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/seal").param("q", "cuisine").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.content[0].title").value("Cuisine"));
        mvc.perform(get("/api/verify/{id}", id)).andExpect(jsonPath("$.title").doesNotExist());
    }
    @Test void passwordResetIsSingleUseAndRevokesSessions() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"owner@example.test\"}"))
            .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        String resetToken = captor.getValue().getText().split("token=")[1].split("\\s")[0];
        assertThat(jdbc.queryForObject("select digest from account_tokens", String.class)).doesNotContain(resetToken);
        String body = json.writeValueAsString(Map.of("token", resetToken, "password", "new-password-123"));
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/account").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(passwords.matches("new-password-123", users.findById(owner).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void accountDeletionRevokesTokenAndErasesRecords() throws Exception {
        UUID id = UUID.randomUUID(); seal(id);
        mvc.perform(delete("/api/account").header("Authorization", "Bearer " + token).contentType("application/json")
            .content("{\"password\":\"test-password-123\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/account").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/verify/{id}", id)).andExpect(status().isNotFound());
        lifecycle.purge(id); lifecycle.finishAccountDeletion(owner);
        assertThat(users.findById(owner)).isEmpty();
    }
    @Test void quotaRejectsWithoutWritingProofOrConsent() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(sealing, "quotaBytes", 1L);
        mvc.perform(multipart("/api/seal").file(new MockMultipartFile("file", "video.webm", "video/webm", VIDEO))
            .param("captureId", UUID.randomUUID().toString()).param("consentAccepted", "true").param("geolocConsented", "false")
            .param("policyVersion", ConsentPolicy.VERSION).param("consentedAt", Instant.now().toString())
            .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from sealed_records", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from consent_logs", Long.class)).isZero();
    }
    @Test void expiredProofIsUnavailableAndPurged() throws Exception {
        UUID id = UUID.randomUUID(); seal(id);
        // Seed a historical proof using an insert; immutable fields are never updated.
        UUID expired = UUID.randomUUID();
        UUID consent = UUID.randomUUID();
        jdbc.update("insert into consent_logs select ?, user_id, session_id, geoloc_consented, purpose_text, retention_days, consented_at, user_agent, ip_address, policy_version, received_at from consent_logs limit 1", consent);
        jdbc.update("insert into sealed_records select ?, user_id, ?, file_name, file_size_bytes, mime_type, sha256_hex, tsa_token_der, tsa_url, tsa_timestamp, now() - interval '366 days', geoloc_lat, geoloc_lng, device_ua, '/tmp/realis-integration-captures/expired.enc', null from sealed_records where id = ?", expired, consent, id);
        mvc.perform(get("/api/verify/{id}", expired)).andExpect(status().isNotFound());
        lifecycle.purge(expired);
        assertThat(jdbc.queryForObject("select count(*) from sealed_records where id = ?", Long.class, expired)).isZero();
        mvc.perform(get("/api/verify/{id}", id)).andExpect(status().isOk());
    }
    @Test void verificationEmailUnlocksSealingAndTokenExpires() throws Exception {
        var user = users.findById(owner).orElseThrow(); user.setEmailVerified(false); users.save(user);
        mvc.perform(post("/api/auth/resend-verification").contentType("application/json").content("{\"email\":\"owner@example.test\"}"))
            .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(captor.capture());
        String secret = captor.getValue().getText().split("token=")[1].split("\\s")[0];
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(json.writeValueAsString(Map.of("token",secret))))
            .andExpect(status().isOk());
        assertThat(users.findById(owner).orElseThrow().isEmailVerified()).isTrue();
        seal(UUID.randomUUID());
        reset(mail);
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"owner@example.test\"}"));
        verify(mail).send(captor.capture());
        String expiredToken = captor.getValue().getText().split("token=")[1].split("\\s")[0];
        jdbc.update("update account_tokens set expires_at = now() - interval '1 second'");
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(json.writeValueAsString(Map.of("token",expiredToken,"password","another-password"))))
            .andExpect(status().isBadRequest());
    }
    @Test void missingConsentAndUnverifiedAccountsCannotSeal() throws Exception {
        var request = multipart("/api/seal").file(new MockMultipartFile("file", "video.webm", "video/webm", VIDEO))
            .param("captureId", UUID.randomUUID().toString()).param("consentAccepted", "false").param("geolocConsented", "false")
            .param("policyVersion", ConsentPolicy.VERSION).param("consentedAt", Instant.now().toString())
            .header("Authorization", "Bearer " + token);
        mvc.perform(request).andExpect(status().isBadRequest());
        var user = users.findById(owner).orElseThrow(); user.setEmailVerified(false); users.save(user);
        mvc.perform(multipart("/api/seal").file(new MockMultipartFile("file", "video.webm", "video/webm", VIDEO))
            .param("captureId", UUID.randomUUID().toString()).param("consentAccepted", "true").param("geolocConsented", "false")
            .param("policyVersion", ConsentPolicy.VERSION).param("consentedAt", Instant.now().toString())
            .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from sealed_records", Long.class)).isZero();
    }
}
