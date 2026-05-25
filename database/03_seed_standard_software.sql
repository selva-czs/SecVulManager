BEGIN;

INSERT INTO security_software (software_name, is_enabled, is_archived, created_at)
VALUES
    ('Kaseya', true, false, now()),
    ('Rapidfire', true, false, now()),
    ('Nessus', true, false, now())
ON CONFLICT (software_name) DO UPDATE SET
    is_enabled = true,
    is_archived = false,
    archived_at = NULL,
    archived_by = NULL;

COMMIT;
