package com.realis.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.realis.model.SealedRecord;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Génère le certificat PDF d'un enregistrement scellé.
 *
 * Le PDF est produit à la demande (non stocké), toujours frais depuis la DB.
 *
 * Contenu garanti :
 *  - Identifiant de l'enregistrement
 *  - Hash SHA-256 des octets bruts du fichier scellé
 *  - Horodatage RFC 3161 (date + URL de la TSA)
 *  - Métadonnées de capture (nom, taille, format, géoloc, appareil)
 *  - Avertissement légal explicite
 *  - Lien et commande openssl pour vérification indépendante
 */
@Service
public class PdfCertificateService {

    // Palette Realis
    private static final Color REALIS_DARK   = new DeviceRgb(13,  28,  82);
    private static final Color REALIS_BLUE   = new DeviceRgb(45,  82,  196);
    private static final Color REALIS_LIGHT  = new DeviceRgb(224, 233, 255);
    private static final Color LABEL_BG      = new DeviceRgb(246, 248, 252);
    private static final Color BORDER_COLOR  = new DeviceRgb(200, 210, 235);
    private static final Color WARNING_BG    = new DeviceRgb(255, 249, 219);
    private static final Color WARNING_BORDER = new DeviceRgb(200, 160, 0);
    private static final Color MONO_COLOR    = new DeviceRgb(40,  60,  100);
    private static final Color GRAY_TEXT     = new DeviceRgb(100, 110, 130);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'UTC'")
            .withZone(ZoneId.of("UTC"));

    public byte[] generate(SealedRecord record) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PdfWriter   writer = new PdfWriter(out);
        PdfDocument pdf    = new PdfDocument(writer);
        Document    doc    = new Document(pdf, PageSize.A4);
        doc.setMargins(36, 40, 36, 40);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont mono    = PdfFontFactory.createFont(StandardFonts.COURIER);

        doc.add(header(bold, regular));
        doc.add(spacer(8));

        doc.add(referenceBar(record, regular, mono));
        doc.add(spacer(12));

        doc.add(sectionTitle("IDENTIFICATION", bold));
        doc.add(fieldsTable(bold, regular)
            .addRow("Référence",      record.getId().toString(),             regular, mono)
            .addRow("Scellé le",      DATE_FMT.format(record.getSealedAt()), regular, regular)
            .addRow("Propriétaire",   record.getUser().getEmail(),            regular, regular)
            .table());
        doc.add(spacer(10));

        doc.add(sectionTitle("PREUVE D'INTÉGRITÉ", bold));
        boolean tsaActive = !record.getTsaUrl().startsWith("no-op://");
        doc.add(fieldsTable(bold, regular)
            .addRow("Hash SHA-256",   record.getSha256Hex(),                 regular, mono)
            .addRow("Horodatage TSA", DATE_FMT.format(record.getTsaTimestamp()), regular, regular)
            .addRow("Autorité TSA",   record.getTsaUrl(),                    regular, regular)
            .addRow("Statut TSA",     tsaActive ? "ACTIF (RFC 3161)" : "NON ACTIF (développement)", regular, regular)
            .table());
        doc.add(spacer(10));

        doc.add(sectionTitle("FICHIER SCELLÉ", bold));
        doc.add(fieldsTable(bold, regular)
            .addRow("Nom du fichier", record.getFileName(),                  regular, regular)
            .addRow("Taille",         formatSize(record.getFileSizeBytes()),  regular, regular)
            .addRow("Format",         record.getMimeType(),                   regular, regular)
            .addRow("Géolocalisation", formatGeoloc(record),                 regular, regular)
            .addRow("Appareil",       nvl(record.getDeviceUa(), "Non renseigné"), regular, regular)
            .table());
        doc.add(spacer(12));

        doc.add(legalWarning(bold, regular));
        doc.add(spacer(12));

        doc.add(verificationSection(record, bold, regular, mono));

        doc.close();
        return out.toByteArray();
    }

    // ── Blocs de mise en page ────────────────────────────────────────────────

    private Table header(PdfFont bold, PdfFont regular) throws IOException {
        Table t = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
            .setWidth(UnitValue.createPercentValue(100));

        // Bloc gauche : identité
        Cell left = new Cell()
            .setBackgroundColor(REALIS_DARK)
            .setBorder(Border.NO_BORDER)
            .setPadding(16);
        left.add(new Paragraph("REALIS")
            .setFont(bold).setFontSize(22).setFontColor(DeviceRgb.WHITE)
            .setMarginBottom(2));
        left.add(new Paragraph("Certificat de preuve d'intégrité et d'antériorité")
            .setFont(regular).setFontSize(9).setFontColor(REALIS_LIGHT)
            .setMarginBottom(0));
        t.addCell(left);

        // Bloc droit : protocole
        Cell right = new Cell()
            .setBackgroundColor(REALIS_BLUE)
            .setBorder(Border.NO_BORDER)
            .setPadding(16)
            .setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("RFC 3161")
            .setFont(bold).setFontSize(11).setFontColor(DeviceRgb.WHITE)
            .setMarginBottom(2));
        right.add(new Paragraph("Time-Stamp Protocol")
            .setFont(regular).setFontSize(8).setFontColor(REALIS_LIGHT));
        right.add(new Paragraph("SHA-256")
            .setFont(bold).setFontSize(9).setFontColor(REALIS_LIGHT));
        t.addCell(right);

        return t;
    }

    private Paragraph referenceBar(SealedRecord record, PdfFont regular, PdfFont mono) throws IOException {
        return new Paragraph()
            .add("Réf. : ")
            .setFont(regular).setFontSize(8).setFontColor(GRAY_TEXT)
            .add(new com.itextpdf.layout.element.Text(record.getId().toString())
                .setFont(mono).setFontSize(8))
            .setTextAlignment(TextAlignment.RIGHT);
    }

    private Paragraph sectionTitle(String text, PdfFont bold) throws IOException {
        return new Paragraph(text)
            .setFont(bold).setFontSize(8)
            .setFontColor(REALIS_BLUE)
            .setMarginBottom(4)
            .setMarginTop(0);
    }

    private Paragraph spacer(float height) {
        return new Paragraph("").setMarginBottom(height).setMarginTop(0);
    }

    /** Avertissement légal, exigence spec : visible et précis. */
    private Table legalWarning(PdfFont bold, PdfFont regular) throws IOException {
        Table t = new Table(UnitValue.createPercentArray(new float[]{100}))
            .setWidth(UnitValue.createPercentValue(100));

        Cell cell = new Cell()
            .setBackgroundColor(WARNING_BG)
            .setBorder(new SolidBorder(WARNING_BORDER, 1))
            .setPaddingLeft(12).setPaddingRight(12)
            .setPaddingTop(10).setPaddingBottom(10);

        cell.add(new Paragraph("AVERTISSEMENT LÉGAL")
            .setFont(bold).setFontSize(8).setFontColor(WARNING_BORDER)
            .setMarginBottom(4));
        cell.add(new Paragraph(
            "Ce certificat constitue une preuve d'intégrité et d'antériorité basée sur un " +
            "hash SHA-256 et un horodatage RFC 3161 (Time-Stamp Protocol). " +
            "Il atteste que le fichier scellé existait, sous cette forme exacte, " +
            "à la date certifiée par l'autorité d'horodatage. " +
            "Il ne constitue pas un acte authentique au sens juridique " +
            "et ne remplace pas un constat d'huissier."
        ).setFont(regular).setFontSize(8).setFontColor(new DeviceRgb(80, 60, 0)));

        t.addCell(cell);
        return t;
    }

    private Table verificationSection(SealedRecord record, PdfFont bold, PdfFont regular, PdfFont mono) throws IOException {
        Table t = new Table(UnitValue.createPercentArray(new float[]{100}))
            .setWidth(UnitValue.createPercentValue(100));

        Cell cell = new Cell()
            .setBackgroundColor(LABEL_BG)
            .setBorder(new SolidBorder(BORDER_COLOR, 1))
            .setPadding(12);

        cell.add(new Paragraph("VÉRIFICATION INDÉPENDANTE")
            .setFont(bold).setFontSize(8).setFontColor(REALIS_BLUE)
            .setMarginBottom(6));

        cell.add(new Paragraph("Page de vérification en ligne :")
            .setFont(regular).setFontSize(8).setMarginBottom(2));
        cell.add(new Paragraph("https://realis.app/verifier/" + record.getId())
            .setFont(mono).setFontSize(8).setFontColor(REALIS_BLUE)
            .setMarginBottom(8));

        cell.add(new Paragraph("Vérification locale sans dépendance à Realis (openssl) :")
            .setFont(regular).setFontSize(8).setMarginBottom(2));
        cell.add(new Paragraph(
            "openssl ts -verify -in token.tsr -data " + record.getFileName() + " -CAfile freetsa-ca.crt"
        ).setFont(mono).setFontSize(7.5f).setFontColor(MONO_COLOR)
            .setMarginBottom(4));

        cell.add(new Paragraph(
            "Le jeton TSA (.tsr) peut être exporté via l'API : " +
            "GET /api/verify/" + record.getId() + "/tsa"
        ).setFont(regular).setFontSize(7.5f).setFontColor(GRAY_TEXT));

        t.addCell(cell);
        return t;
    }

    // ── Constructeur de tableau champs/valeurs ───────────────────────────────

    private FieldTableBuilder fieldsTable(PdfFont bold, PdfFont regular) {
        return new FieldTableBuilder(bold, regular);
    }

    private static class FieldTableBuilder {
        private final Table table;
        private final PdfFont labelFont;

        FieldTableBuilder(PdfFont bold, PdfFont regular) {
            this.table = new Table(UnitValue.createPercentArray(new float[]{28, 72}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(0);
            this.labelFont = bold;
        }

        FieldTableBuilder addRow(String label, String value, PdfFont labelF, PdfFont valueF) {
            Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(labelF).setFontSize(8).setFontColor(new DeviceRgb(60, 70, 100)))
                .setBackgroundColor(new DeviceRgb(237, 242, 252))
                .setBorderRight(new SolidBorder(new DeviceRgb(180, 196, 230), 0.5f))
                .setBorderBottom(new SolidBorder(new DeviceRgb(210, 220, 240), 0.5f))
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setPaddingLeft(8).setPaddingRight(6)
                .setPaddingTop(5).setPaddingBottom(5);

            Cell valueCell = new Cell()
                .add(new Paragraph(value).setFont(valueF).setFontSize(8))
                .setBorderBottom(new SolidBorder(new DeviceRgb(220, 228, 245), 0.5f))
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setPaddingLeft(8).setPaddingRight(6)
                .setPaddingTop(5).setPaddingBottom(5);

            table.addCell(labelCell);
            table.addCell(valueCell);
            return this;
        }

        Table table() { return table; }
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private static String formatSize(long bytes) {
        if (bytes < 1_024)               return bytes + " o";
        if (bytes < 1_024 * 1_024)       return String.format("%.1f Ko", bytes / 1_024.0);
        if (bytes < 1_024 * 1_024 * 1_024L) return String.format("%.1f Mo", bytes / (1_024.0 * 1_024));
        return String.format("%.2f Go", bytes / (1_024.0 * 1_024 * 1_024));
    }

    private static String formatGeoloc(SealedRecord r) {
        if (r.getGeolocLat() == null || r.getGeolocLng() == null) return "Non renseignée";
        return String.format("%.6f, %.6f", r.getGeolocLat(), r.getGeolocLng());
    }

    private static String nvl(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
