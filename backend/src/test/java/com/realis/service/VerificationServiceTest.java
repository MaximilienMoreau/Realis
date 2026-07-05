package com.realis.service;

import com.realis.dto.VerificationResponse;
import com.realis.dto.VerificationResponse.Verdict;
import com.realis.model.ConsentLog;
import com.realis.model.SealedRecord;
import com.realis.model.User;
import com.realis.repository.SealedRecordRepository;
import com.realis.service.timestamp.NoOpTimestampAuthority;
import com.realis.service.timestamp.TimestampAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests critiques de la vérification : couvre les trois verdicts attendus par le DoD.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationService : verdicts AUTHENTIQUE / ALTÉRÉ / INCONNU")
class VerificationServiceTest {

    // HashService sans dépendances : implémentation réelle (pas de mock)
    private final HashService hashService = new HashService();
    private final TimestampAuthority noOpTsa = new NoOpTimestampAuthority();

    @Mock
    private SealedRecordRepository repo;

    private VerificationService service;

    // Contenu de référence (simule les octets bruts de la vidéo scellée)
    private static final byte[] ORIGINAL_BYTES = "contenu vidéo original scellé".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ALTERED_BYTES;

    static {
        ALTERED_BYTES = ORIGINAL_BYTES.clone();
        ALTERED_BYTES[0] ^= 0xFF; // 1 octet altéré
    }

    private SealedRecord sealedRecord;
    private UUID recordId;

    @BeforeEach
    void setUp() {
        service = new VerificationService(hashService, repo, noOpTsa);

        recordId = UUID.randomUUID();
        String originalHash = hashService.sha256Hex(ORIGINAL_BYTES);

        User user = User.builder()
            .id(UUID.randomUUID()).email("test@realis.fr").passwordHash("x").build();
        ConsentLog consent = ConsentLog.builder()
            .id(UUID.randomUUID()).user(user).sessionId("s1")
            .geolocConsented(false).purposeText("test").retentionDays(365).build();

        sealedRecord = SealedRecord.builder()
            .id(recordId)
            .user(user)
            .consentLog(consent)
            .fileName("video.webm")
            .fileSizeBytes(ORIGINAL_BYTES.length)
            .mimeType("video/webm")
            .sha256Hex(originalHash)
            .tsaTokenDer(new byte[0])          // no-op pour ces tests
            .tsaUrl("no-op://localhost")
            .tsaTimestamp(Instant.now())
            .storagePath("/data/test.enc")
            .build();
    }

    // ── Cas 1 : AUTHENTIQUE, même fichier, recherche par hash ───────────────

    @Test
    @DisplayName("DoD ✓ : Même fichier re-soumis (sans recordId) → AUTHENTIQUE")
    void verify_sameFile_noRecordId_returnsAuthentique() throws IOException {
        when(repo.findActiveBySha256Hex(any())).thenReturn(List.of(sealedRecord));

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, null);

        assertThat(result.verdict()).isEqualTo(Verdict.AUTHENTIQUE);
        assertThat(result.uploadedSha256()).isEqualTo(sealedRecord.getSha256Hex());
        assertThat(result.integrityCheck().passed()).isTrue();
        assertThat(result.record()).isNotNull();
    }

    // ── Cas 2 : AUTHENTIQUE, même fichier avec recordId ─────────────────────

    @Test
    @DisplayName("Même fichier + recordId fourni → AUTHENTIQUE")
    void verify_sameFile_withRecordId_returnsAuthentique() throws IOException {
        when(repo.findById(recordId)).thenReturn(Optional.of(sealedRecord));

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, recordId);

        assertThat(result.verdict()).isEqualTo(Verdict.AUTHENTIQUE);
        assertThat(result.integrityCheck().passed()).isTrue();
    }

    // ── Cas 3 : ALTÉRÉ, 1 octet modifié, recordId fourni ───────────────────

    @Test
    @DisplayName("DoD ✓ : 1 octet altéré + recordId → ALTÉRÉ")
    void verify_alteredFile_withRecordId_returnsAltere() throws IOException {
        when(repo.findById(recordId)).thenReturn(Optional.of(sealedRecord));

        MockMultipartFile file = new MockMultipartFile("file", "video_altered.webm",
            "video/webm", ALTERED_BYTES);

        VerificationResponse result = service.verify(file, recordId);

        assertThat(result.verdict()).isEqualTo(Verdict.ALTERE);
        assertThat(result.integrityCheck().passed()).isFalse();
        // Le hash uploadé doit différer du hash scellé
        assertThat(result.uploadedSha256()).isNotEqualTo(sealedRecord.getSha256Hex());
        // L'enregistrement de référence est quand même retourné (pour affichage)
        assertThat(result.record()).isNotNull();
    }

    // ── Cas 4 : INCONNU, hash inconnu, aucun recordId ───────────────────────

    @Test
    @DisplayName("DoD ✓ : Hash inconnu (sans recordId) → INCONNU")
    void verify_unknownHash_noRecordId_returnsInconnu() throws IOException {
        when(repo.findActiveBySha256Hex(any())).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile("file", "unknown.webm",
            "video/webm", "contenu jamais scellé".getBytes());

        VerificationResponse result = service.verify(file, null);

        assertThat(result.verdict()).isEqualTo(Verdict.INCONNU);
        assertThat(result.record()).isNull();
        assertThat(result.integrityCheck().passed()).isFalse();
        assertThat(result.tsaCheck()).isNull();
    }

    // ── Cas 5 : recordId introuvable ─────────────────────────────────────────

    @Test
    @DisplayName("RecordId inexistant → INCONNU (même contrat que le mode sans recordId)")
    void verify_unknownRecordId_returnsInconnu() throws IOException {
        UUID unknownId = UUID.randomUUID();
        when(repo.findById(unknownId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "f.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, unknownId);

        assertThat(result.verdict()).isEqualTo(Verdict.INCONNU);
        assertThat(result.record()).isNull();
        assertThat(result.tsaCheck()).isNull();
    }

    // ── Cas 6 : le hash uploadé correspond octet pour octet ──────────────────

    @Test
    @DisplayName("Hash SHA-256 calculé identique dans les deux chemins (avec et sans recordId)")
    void verify_hashConsistency_bothPaths() throws IOException {
        when(repo.findById(recordId)).thenReturn(Optional.of(sealedRecord));
        when(repo.findActiveBySha256Hex(any())).thenReturn(List.of(sealedRecord));

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse withId    = service.verify(file, recordId);

        // Re-créer le mock file (l'InputStream est consommé)
        MockMultipartFile file2 = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);
        VerificationResponse withoutId = service.verify(file2, null);

        assertThat(withId.uploadedSha256()).isEqualTo(withoutId.uploadedSha256());
    }

    // ── Cas 7 : fichier supprimé logiquement, recherche par hash ─────────────

    @Test
    @DisplayName("Enregistrement supprimé (deletedAt != null), sans recordId → INCONNU")
    void verify_deletedRecord_byHash_notReturnedByRepo() throws IOException {
        // findActiveBySha256Hex filtre les enregistrements supprimés (WHERE deleted_at IS NULL)
        when(repo.findActiveBySha256Hex(any())).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, null);

        // Un enregistrement supprimé n'apparaît plus dans la recherche par hash → INCONNU
        assertThat(result.verdict()).isEqualTo(Verdict.INCONNU);
    }

    // ── Cas 8 : fichier supprimé logiquement, recherche par recordId ─────────

    @Test
    @DisplayName("Enregistrement supprimé (deletedAt != null), avec recordId → AUTHENTIQUE mais avec avertissement")
    void verify_deletedRecord_withRecordId_returnsAuthentiqueWithWarning() throws IOException {
        // Contrairement à findActiveBySha256Hex, findById ne filtre pas deletedAt :
        // le hash cryptographique reste valide même après une suppression logique.
        // C'est SealResponse.warning (porté par le record) qui signale la suppression au client.
        sealedRecord.setDeletedAt(Instant.now());
        when(repo.findById(recordId)).thenReturn(Optional.of(sealedRecord));

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, recordId);

        assertThat(result.verdict()).isEqualTo(Verdict.AUTHENTIQUE);
        assertThat(result.integrityCheck().passed()).isTrue();
        assertThat(result.record()).isNotNull();
        assertThat(result.record().deleted()).isTrue();
        assertThat(result.record().warning()).isNotNull();
    }

    // ── Cas 9 : deux enregistrements actifs partagent le même hash ──────────

    @Test
    @DisplayName("Deux enregistrements actifs avec le même hash (sans recordId) → AUTHENTIQUE sur le plus récent, pas d'exception")
    void verify_duplicateHash_noRecordId_returnsMostRecentWithoutCrashing() throws IOException {
        SealedRecord olderDuplicate = SealedRecord.builder()
            .id(UUID.randomUUID())
            .user(sealedRecord.getUser())
            .consentLog(sealedRecord.getConsentLog())
            .fileName("video-copie.webm")
            .fileSizeBytes(ORIGINAL_BYTES.length)
            .mimeType("video/webm")
            .sha256Hex(sealedRecord.getSha256Hex())
            .tsaTokenDer(new byte[0])
            .tsaUrl("no-op://localhost")
            .tsaTimestamp(Instant.now().minusSeconds(3600))
            .sealedAt(Instant.now().minusSeconds(3600))
            .storagePath("/data/test-copie.enc")
            .build();

        // Le repository trie déjà par sealedAt DESC : le plus récent (sealedRecord) est en tête.
        when(repo.findActiveBySha256Hex(any())).thenReturn(List.of(sealedRecord, olderDuplicate));

        MockMultipartFile file = new MockMultipartFile("file", "video.webm",
            "video/webm", ORIGINAL_BYTES);

        VerificationResponse result = service.verify(file, null);

        assertThat(result.verdict()).isEqualTo(Verdict.AUTHENTIQUE);
        assertThat(result.record()).isNotNull();
        assertThat(result.record().id()).isEqualTo(sealedRecord.getId());
    }
}
