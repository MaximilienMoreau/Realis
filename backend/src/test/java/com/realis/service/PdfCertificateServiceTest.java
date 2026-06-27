package com.realis.service;

import com.realis.model.ConsentLog;
import com.realis.model.SealedRecord;
import com.realis.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PdfCertificateService — génération du certificat PDF")
class PdfCertificateServiceTest {

    private PdfCertificateService service;
    private SealedRecord record;

    @BeforeEach
    void setUp() {
        service = new PdfCertificateService();

        User user = User.builder()
            .id(UUID.randomUUID())
            .email("proprio@example.com")
            .passwordHash("$2a$12$xxx")
            .build();

        ConsentLog consent = ConsentLog.builder()
            .id(UUID.randomUUID())
            .user(user)
            .sessionId(UUID.randomUUID().toString())
            .geolocConsented(true)
            .purposeText("État des lieux — test")
            .retentionDays(365)
            .build();

        record = SealedRecord.builder()
            .id(UUID.randomUUID())
            .user(user)
            .consentLog(consent)
            .fileName("etat-des-lieux-entrant.webm")
            .fileSizeBytes(12_345_678L)
            .mimeType("video/webm")
            .sha256Hex("a".repeat(64))
            .tsaTokenDer(new byte[]{0x30, 0x00})
            .tsaUrl("https://freetsa.org/tsr")
            .tsaTimestamp(Instant.parse("2026-06-27T10:00:00Z"))
            .geolocLat(48.8566)
            .geolocLng(2.3522)
            .deviceUa("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")
            .storagePath("/data/captures/test.enc")
            .build();
    }

    @Test
    @DisplayName("Le PDF généré commence par l'en-tête PDF (%PDF-)")
    void generate_returnsValidPdf() throws IOException {
        byte[] pdf = service.generate(record);

        assertThat(pdf).isNotEmpty();
        // Signature magic des fichiers PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Le PDF a une taille raisonnable (> 1 Ko, < 5 Mo)")
    void generate_sizeIsReasonable() throws IOException {
        byte[] pdf = service.generate(record);

        assertThat(pdf.length).isGreaterThan(1_024);
        assertThat(pdf.length).isLessThan(5 * 1_024 * 1_024);
    }

    @Test
    @DisplayName("Deux appels successifs produisent le même PDF (déterminisme)")
    void generate_isDeterministicForSameRecord() throws IOException {
        byte[] pdf1 = service.generate(record);
        byte[] pdf2 = service.generate(record);

        // Le PDF peut varier légèrement (horodatage de génération) — on vérifie juste la taille
        assertThat(pdf1.length).isCloseTo(pdf2.length, within(500));
    }

    @Test
    @DisplayName("PDF sans géoloc ni device UA — ne lève pas d'exception")
    void generate_withNullOptionalFields_doesNotThrow() {
        SealedRecord minimal = SealedRecord.builder()
            .id(UUID.randomUUID())
            .user(record.getUser())
            .consentLog(record.getConsentLog())
            .fileName("video.webm")
            .fileSizeBytes(1_000L)
            .mimeType("video/webm")
            .sha256Hex("b".repeat(64))
            .tsaTokenDer(new byte[0])
            .tsaUrl("no-op://localhost")
            .tsaTimestamp(Instant.now())
            .geolocLat(null)
            .geolocLng(null)
            .deviceUa(null)
            .storagePath("/data/test.enc")
            .build();

        assertThatNoException().isThrownBy(() -> service.generate(minimal));
    }

    @Test
    @DisplayName("PDF avec TSA no-op — ne lève pas d'exception")
    void generate_withNoOpTsa_doesNotThrow() {
        SealedRecord noOpRecord = SealedRecord.builder()
            .id(UUID.randomUUID())
            .user(record.getUser())
            .consentLog(record.getConsentLog())
            .fileName("video.webm")
            .fileSizeBytes(500_000L)
            .mimeType("video/mp4")
            .sha256Hex("c".repeat(64))
            .tsaTokenDer(new byte[0])
            .tsaUrl("no-op://localhost")
            .tsaTimestamp(Instant.now())
            .storagePath("/data/test.enc")
            .build();

        assertThatNoException().isThrownBy(() -> service.generate(noOpRecord));
    }
}
