package com.realis.service;

import com.realis.dto.SealRequest;
import com.realis.dto.SealResponse;
import com.realis.exception.ResourceNotFoundException;
import com.realis.model.ConsentLog;
import com.realis.model.SealedRecord;
import com.realis.model.User;
import com.realis.repository.ConsentLogRepository;
import com.realis.repository.SealedRecordRepository;
import com.realis.repository.UserRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SealingService : scellement, consultation et suppression logique")
class SealingServiceTest {

    // HashService et TimestampAuthority : implémentations réelles, sans dépendance externe
    private final HashService hashService = new HashService();
    private final TimestampAuthority noOpTsa = new NoOpTimestampAuthority();

    @Mock private StorageService storageService;
    @Mock private UserRepository userRepository;
    @Mock private ConsentLogRepository consentLogRepository;
    @Mock private SealedRecordRepository sealedRecordRepository;

    private SealingService service;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SealingService(
            hashService, storageService, noOpTsa,
            userRepository, consentLogRepository, sealedRecordRepository
        );

        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("proprio@example.com").passwordHash("x").build();
    }

    // ── seal() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("seal() : flux complet réussi (hash, stockage, horodatage, sauvegarde)")
    void seal_success_returnsSealResponse() throws IOException {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(consentLogRepository.save(any(ConsentLog.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(storageService.encryptAndStore(any(), any(), any())).thenReturn("/data/captures/x.enc");
        when(sealedRecordRepository.save(any(SealedRecord.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
            "file", "video.webm", "video/webm", "contenu vidéo".getBytes()
        );
        SealRequest request = new SealRequest("video/webm", 48.85, 2.35, "Mozilla/5.0");

        SealResponse response = service.seal(file, userId, request, "203.0.113.1");

        assertThat(response.fileName()).isEqualTo("video.webm");
        assertThat(response.mimeType()).isEqualTo("video/webm");
        assertThat(response.geolocLat()).isEqualTo(48.85);
        assertThat(response.geolocLng()).isEqualTo(2.35);
        assertThat(response.deleted()).isFalse();
        assertThat(response.warning()).isNull();
        assertThat(response.tsaActive()).isFalse(); // NoOpTimestampAuthority

        verify(consentLogRepository).save(any(ConsentLog.class));
        verify(sealedRecordRepository).save(any(SealedRecord.class));
        verify(storageService).encryptAndStore(any(), eq(userId), any());
    }

    @Test
    @DisplayName("seal() : utilisateur introuvable → ResourceNotFoundException")
    void seal_unknownUser_throwsResourceNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
            "file", "video.webm", "video/webm", "contenu".getBytes()
        );
        SealRequest request = new SealRequest(null, null, null, null);

        assertThatThrownBy(() -> service.seal(file, userId, request, "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(consentLogRepository, sealedRecordRepository, storageService);
    }

    @Test
    @DisplayName("seal() : mimeType et fileName résolus à partir du fichier si absents de la requête")
    void seal_resolvesMimeTypeAndFileNameFromFile() throws IOException {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(consentLogRepository.save(any(ConsentLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.encryptAndStore(any(), any(), any())).thenReturn("/data/captures/x.enc");
        when(sealedRecordRepository.save(any(SealedRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
            "file", "", "video/mp4", "contenu".getBytes()
        );
        SealRequest request = new SealRequest(null, null, null, null);

        SealResponse response = service.seal(file, userId, request, "127.0.0.1");

        assertThat(response.fileName()).isEqualTo("capture.bin");
        assertThat(response.mimeType()).isEqualTo("video/mp4");
    }

    // ── findByIdForOwner() ────────────────────────────────────────────────

    @Test
    @DisplayName("findByIdForOwner() : propriétaire correct → retourne l'enregistrement")
    void findByIdForOwner_owner_returnsRecord() {
        UUID recordId = UUID.randomUUID();
        SealedRecord record = buildRecord(recordId, user);
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        SealResponse response = service.findByIdForOwner(recordId, userId);

        assertThat(response.id()).isEqualTo(recordId);
    }

    @Test
    @DisplayName("findByIdForOwner() : autre utilisateur → SecurityException")
    void findByIdForOwner_notOwner_throwsSecurityException() {
        UUID recordId = UUID.randomUUID();
        SealedRecord record = buildRecord(recordId, user);
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.findByIdForOwner(recordId, otherUserId))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("findByIdForOwner() : enregistrement introuvable → ResourceNotFoundException")
    void findByIdForOwner_unknownId_throwsResourceNotFound() {
        UUID recordId = UUID.randomUUID();
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdForOwner(recordId, userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── listForOwner() ────────────────────────────────────────────────────

    @Test
    @DisplayName("listForOwner() : mappe chaque enregistrement actif en SealResponse")
    void listForOwner_returnsMappedResponses() {
        SealedRecord r1 = buildRecord(UUID.randomUUID(), user);
        SealedRecord r2 = buildRecord(UUID.randomUUID(), user);
        when(sealedRecordRepository.findActiveByUserId(userId)).thenReturn(List.of(r1, r2));

        List<SealResponse> result = service.listForOwner(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SealResponse::id).containsExactly(r1.getId(), r2.getId());
    }

    // ── softDelete() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("softDelete() : propriétaire correct → pose deletedAt et retourne un avertissement")
    void softDelete_owner_setsDeletedAtAndWarns() {
        UUID recordId = UUID.randomUUID();
        SealedRecord record = buildRecord(recordId, user);
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(sealedRecordRepository.save(any(SealedRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        SealResponse response = service.softDelete(recordId, userId);

        assertThat(response.deleted()).isTrue();
        assertThat(response.warning()).isNotNull();
        assertThat(record.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("softDelete() : autre utilisateur → SecurityException")
    void softDelete_notOwner_throwsSecurityException() {
        UUID recordId = UUID.randomUUID();
        SealedRecord record = buildRecord(recordId, user);
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.softDelete(recordId, UUID.randomUUID()))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("softDelete() : déjà supprimé → IllegalStateException")
    void softDelete_alreadyDeleted_throwsIllegalStateException() {
        UUID recordId = UUID.randomUUID();
        SealedRecord record = buildRecord(recordId, user);
        record.setDeletedAt(Instant.now());
        when(sealedRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.softDelete(recordId, userId))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    private SealedRecord buildRecord(UUID id, User owner) {
        ConsentLog consent = ConsentLog.builder()
            .id(UUID.randomUUID()).user(owner).sessionId("s1")
            .geolocConsented(false).purposeText("test").retentionDays(365).build();

        return SealedRecord.builder()
            .id(id)
            .user(owner)
            .consentLog(consent)
            .fileName("video.webm")
            .fileSizeBytes(1_000L)
            .mimeType("video/webm")
            .sha256Hex(hashService.sha256Hex("contenu".getBytes()))
            .tsaTokenDer(new byte[0])
            .tsaUrl("no-op://localhost")
            .tsaTimestamp(Instant.now())
            .storagePath("/data/test.enc")
            .build();
    }
}
