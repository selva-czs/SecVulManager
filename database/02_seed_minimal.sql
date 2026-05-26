BEGIN;

INSERT INTO app_user (
    username,
    password_hash,
    full_name,
    role,
    is_enabled,
    is_archived,
    created_at,
    updated_at
) VALUES (
    'admin',
    '$2a$10$b2KIrlePAwHaFivvidf3R.cpio7jlscpnYRERl9qlDR4RSujlKbuW',
    'Super Administrator',
    'SUPER_ADMIN',
    true,
    false,
    now(),
    now()
)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    is_enabled = true,
    is_archived = false,
    archived_at = NULL,
    archived_by = NULL,
    updated_at = now();

INSERT INTO security_software (software_name, is_enabled, is_archived, archived_at, archived_by, created_at)
VALUES
    ('Kaseya', true, false, NULL, NULL, now()),
    ('Rapidfire', true, false, NULL, NULL, now()),
    ('Nessus', true, false, NULL, NULL, now())
ON CONFLICT (software_name) DO UPDATE SET
    is_enabled = true,
    is_archived = false,
    archived_at = NULL,
    archived_by = NULL;

COMMIT;
