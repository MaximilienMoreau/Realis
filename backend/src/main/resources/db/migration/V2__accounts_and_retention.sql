-- Preserve V1 checksums and existing proof fields.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE account_tokens (
    digest VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX account_tokens_user ON account_tokens(user_id);

CREATE TABLE proof_labels (
    record_id UUID PRIMARY KEY REFERENCES sealed_records(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL DEFAULT '',
    folder VARCHAR(160) NOT NULL DEFAULT ''
);
CREATE INDEX proof_labels_folder ON proof_labels(folder);

-- Minimal tombstones: no content, user, location or hash remains after erasure.
CREATE TABLE deleted_proofs (id UUID PRIMARY KEY, deleted_at TIMESTAMPTZ NOT NULL);
ALTER TABLE consent_logs ADD COLUMN policy_version VARCHAR(40);
ALTER TABLE consent_logs ADD COLUMN received_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Erasure is permitted only after an explicit deletion marker. Active proofs stay immutable.
CREATE OR REPLACE FUNCTION prevent_sealed_record_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.deleted_at IS NULL THEN
        RAISE EXCEPTION 'Marquer la suppression avant effacement.';
    END IF;
    INSERT INTO deleted_proofs(id, deleted_at) VALUES (OLD.id, OLD.deleted_at)
        ON CONFLICT (id) DO NOTHING;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;
CREATE INDEX sealed_records_expiry ON sealed_records(sealed_at);
