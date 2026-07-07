-- ============================================================
-- TiiBnTick Core — tnt-sync-core schema migration V1 (part 2)
-- Author: MANFOUO Braun
-- ============================================================

-- Trigger to auto-update version sequence on upsert
CREATE OR REPLACE FUNCTION tnt_entity_version_set_version()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.version IS NULL OR NEW.version = 0 THEN
        NEW.version = (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    END IF;
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_entity_version_set_version ON tnt_entity_version;
CREATE TRIGGER trg_entity_version_set_version
    BEFORE INSERT OR UPDATE ON tnt_entity_version
    FOR EACH ROW EXECUTE FUNCTION tnt_entity_version_set_version();
