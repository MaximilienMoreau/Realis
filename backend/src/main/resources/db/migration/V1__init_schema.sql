-- =============================================================
-- Migration V1 : schéma initial Realis
-- =============================================================

-- Extension UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Utilisateurs ──────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Journal des consentements (append-only) ───────────────────────
CREATE TABLE consent_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    session_id        TEXT NOT NULL,
    geoloc_consented  BOOLEAN NOT NULL,
    -- Texte exact présenté à l'utilisateur (auditabilité RGPD)
    purpose_text      TEXT NOT NULL,
    retention_days    INTEGER NOT NULL,
    consented_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_agent        TEXT,
    ip_address        TEXT
);

-- ── Enregistrements scellés ───────────────────────────────────────
CREATE TABLE sealed_records (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    consent_log_id   UUID NOT NULL REFERENCES consent_logs(id),
    file_name        TEXT NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    mime_type        TEXT NOT NULL,
    sha256_hex       TEXT NOT NULL,
    -- Jeton RFC 3161 brut (DER) : vérifiable indépendamment
    tsa_token_der    BYTEA NOT NULL,
    tsa_url          TEXT NOT NULL,
    tsa_timestamp    TIMESTAMPTZ NOT NULL,
    sealed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    geoloc_lat       DOUBLE PRECISION,
    geoloc_lng       DOUBLE PRECISION,
    device_ua        TEXT,
    -- Chemin chiffré sur volume (jamais le fichier brut en clair)
    storage_path     TEXT NOT NULL,
    -- Suppression logique uniquement (casse la preuve, avertissement obligatoire)
    deleted_at       TIMESTAMPTZ
);

-- ── Règle d'immuabilité : interdit UPDATE sur les champs de preuve ─
-- On autorise uniquement la mise à jour de deleted_at via un endpoint
-- dédié. Le trigger ci-dessous bloque toute autre modification.
CREATE OR REPLACE FUNCTION prevent_sealed_record_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Seule la suppression logique est autorisée (et uniquement si le champ est NULL).
    -- Toutes les colonnes portant la preuve doivent rester strictement identiques :
    -- un simple contrôle sur (id, user_id, consent_log_id, sha256_hex, tsa_token_der,
    -- storage_path) laissait passer une réécriture silencieuse de tsa_timestamp/tsa_url
    -- (l'horodatage lui-même) en même temps que le soft-delete.
    IF OLD.deleted_at IS NULL AND
       NEW.deleted_at IS NOT NULL AND
       NEW.id              = OLD.id              AND
       NEW.user_id         = OLD.user_id         AND
       NEW.consent_log_id  = OLD.consent_log_id  AND
       NEW.file_name       = OLD.file_name       AND
       NEW.file_size_bytes = OLD.file_size_bytes AND
       NEW.mime_type       = OLD.mime_type       AND
       NEW.sha256_hex      = OLD.sha256_hex      AND
       NEW.tsa_token_der   = OLD.tsa_token_der   AND
       NEW.tsa_url         = OLD.tsa_url         AND
       NEW.tsa_timestamp   = OLD.tsa_timestamp   AND
       NEW.sealed_at       = OLD.sealed_at       AND
       NEW.geoloc_lat      IS NOT DISTINCT FROM OLD.geoloc_lat  AND
       NEW.geoloc_lng      IS NOT DISTINCT FROM OLD.geoloc_lng  AND
       NEW.device_ua       IS NOT DISTINCT FROM OLD.device_ua  AND
       NEW.storage_path    = OLD.storage_path
    THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Les enregistrements scellés sont immuables. Modification interdite.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sealed_records_immutability
BEFORE UPDATE ON sealed_records
FOR EACH ROW EXECUTE FUNCTION prevent_sealed_record_update();

-- Interdit également la suppression physique des lignes
CREATE OR REPLACE FUNCTION prevent_sealed_record_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Suppression physique interdite sur sealed_records. Utilisez deleted_at.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sealed_records_no_delete
BEFORE DELETE ON sealed_records
FOR EACH ROW EXECUTE FUNCTION prevent_sealed_record_delete();

-- ── Index utiles ──────────────────────────────────────────────────
CREATE INDEX idx_sealed_records_user_id   ON sealed_records(user_id);
CREATE INDEX idx_sealed_records_sha256    ON sealed_records(sha256_hex);
CREATE INDEX idx_consent_logs_user_id     ON consent_logs(user_id);
