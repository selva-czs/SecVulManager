package com.secvulmanager.api.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class DatabaseSchemaMaintenance implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        allowGlobalTemplates();
        keepLegacyTemplateActiveColumnInsertable();
        repairUploadTemplateForeignKey();
        backfillUploadSoftwareAndStats();
        widenVendorTextFields();
        addUploadQueueColumns();
        addSavedViewsAndRemediationEvents();
        normalizeActiveSnapshots();
        addUploadConcurrencyIndexes();
    }

    private void allowGlobalTemplates() {
        Integer notNullCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'customer_template'
              AND column_name = 'customer_id'
              AND is_nullable = 'NO'
            """,
            Integer.class
        );

        if (notNullCount != null && notNullCount > 0) {
            jdbcTemplate.execute("ALTER TABLE customer_template ALTER COLUMN customer_id DROP NOT NULL");
            System.out.println("[DatabaseSchemaMaintenance] Updated customer_template.customer_id to allow global templates.");
        }
    }

    private void keepLegacyTemplateActiveColumnInsertable() {
        Integer legacyColumnCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'customer_template'
              AND column_name = 'is_active'
              AND column_default IS NULL
            """,
            Integer.class
        );

        if (legacyColumnCount != null && legacyColumnCount > 0) {
            jdbcTemplate.execute("UPDATE customer_template SET is_active = true WHERE is_active IS NULL");
            jdbcTemplate.execute("ALTER TABLE customer_template ALTER COLUMN is_active SET DEFAULT true");
            System.out.println("[DatabaseSchemaMaintenance] Added default for legacy customer_template.is_active column.");
        }
    }

    private void repairUploadTemplateForeignKey() {
        jdbcTemplate.execute("""
            DO $$
            DECLARE
                stale_constraint text;
            BEGIN
                SELECT tc.constraint_name
                INTO stale_constraint
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'upload_details'
                  AND tc.constraint_type = 'FOREIGN KEY'
                  AND kcu.column_name = 'template_id'
                  AND ccu.table_name <> 'customer_template'
                LIMIT 1;

                IF stale_constraint IS NOT NULL THEN
                    EXECUTE format('ALTER TABLE upload_details DROP CONSTRAINT %I', stale_constraint);
                END IF;

                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                    JOIN information_schema.constraint_column_usage ccu
                      ON ccu.constraint_name = tc.constraint_name
                     AND ccu.table_schema = tc.table_schema
                    WHERE tc.table_schema = current_schema()
                      AND tc.table_name = 'upload_details'
                      AND tc.constraint_type = 'FOREIGN KEY'
                      AND kcu.column_name = 'template_id'
                      AND ccu.table_name = 'customer_template'
                ) THEN
                    ALTER TABLE upload_details
                    ADD CONSTRAINT upload_details_template_id_fkey
                    FOREIGN KEY (template_id) REFERENCES customer_template(id) ON DELETE SET NULL;
                END IF;
            END $$;
            """);
    }

    private void backfillUploadSoftwareAndStats() {
        jdbcTemplate.execute("""
            UPDATE upload_details u
            SET software_id = t.software_id
            FROM customer_template t
            WHERE u.template_id = t.id
              AND u.software_id IS NULL
            """);
        jdbcTemplate.execute("""
            UPDATE upload_details
            SET successful_records = GREATEST(total_records - failed_records, 0)
            WHERE successful_records IS NULL
            """);
        jdbcTemplate.execute("UPDATE upload_details SET warning_records = 0 WHERE warning_records IS NULL");
        jdbcTemplate.execute("""
            UPDATE upload_details
            SET uploaded_file_path = sample_file_path
            WHERE uploaded_file_path IS NULL
              AND sample_file_path IS NOT NULL
            """);
    }

    private void widenVendorTextFields() {
        jdbcTemplate.execute("ALTER TABLE vulnerability_finding ALTER COLUMN cve_id TYPE text");
    }

    private void addUploadQueueColumns() {
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS processed_records integer NOT NULL DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS processing_stage varchar(60) NOT NULL DEFAULT 'COMPLETED'");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS queue_mode varchar(60) NOT NULL DEFAULT 'REJECT_IF_BUSY'");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS queue_comment text");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS queued_at timestamptz");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS started_at timestamptz");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS finished_at timestamptz");
        jdbcTemplate.execute("ALTER TABLE upload_details ADD COLUMN IF NOT EXISTS replace_active_when_done boolean NOT NULL DEFAULT false");
        jdbcTemplate.execute("""
            DELETE FROM vulnerability_finding vf
            USING upload_details u
            WHERE vf.upload_id = u.id
              AND u.status = 'PROCESSING'
            """);
        jdbcTemplate.execute("""
            UPDATE upload_details
            SET status = 'FAILED',
                processing_stage = 'FAILED',
                error_summary = COALESCE(error_summary, 'Recovered stale processing upload during schema maintenance.')
            WHERE status = 'PROCESSING'
            """);
        jdbcTemplate.execute("""
            UPDATE upload_details
            SET processing_stage = CASE
                WHEN status = 'PROCESSING' THEN 'FAILED'
                ELSE 'COMPLETED'
            END
            WHERE processing_stage IS NULL
            """);
        jdbcTemplate.execute("UPDATE upload_details SET processed_records = GREATEST(successful_records + failed_records, 0) WHERE processed_records IS NULL OR processed_records = 0");
    }

    private void addUploadConcurrencyIndexes() {
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_upload_details_customer_running
            ON upload_details (customer_id)
            WHERE status = 'PROCESSING' AND processing_stage <> 'QUEUED'
            """);
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS ux_upload_active_customer_software
            ON upload_details (customer_id, software_id)
            WHERE is_active_snapshot = true AND software_id IS NOT NULL
            """);
    }

    private void addSavedViewsAndRemediationEvents() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'vulnerability_remediation_status'
                      AND constraint_name = 'vulnerability_remediation_status_workflow_chk'
                ) THEN
                    ALTER TABLE vulnerability_remediation_status
                    DROP CONSTRAINT vulnerability_remediation_status_workflow_chk;
                END IF;

                ALTER TABLE vulnerability_remediation_status
                ADD CONSTRAINT vulnerability_remediation_status_workflow_chk
                CHECK (workflow_status IN ('OPEN', 'IN_PROGRESS', 'FALSE_POSITIVE', 'RESOLVED', 'ACCEPTED_RISK'));
            END $$;
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vulnerability_remediation_event (
                id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
                logical_finding_hash varchar(64) NOT NULL,
                from_status varchar(50),
                to_status varchar(50) NOT NULL,
                comment varchar(4000),
                changed_by_user_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
                changed_by_username varchar(100) NOT NULL,
                changed_at timestamptz NOT NULL DEFAULT now(),
                CONSTRAINT vulnerability_remediation_event_to_status_chk CHECK (to_status IN ('OPEN', 'IN_PROGRESS', 'FALSE_POSITIVE', 'RESOLVED', 'ACCEPTED_RISK'))
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_saved_view (
                id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                name varchar(120) NOT NULL,
                view_type varchar(60) NOT NULL,
                is_default boolean NOT NULL DEFAULT false,
                filters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
                sort_json jsonb NOT NULL DEFAULT '{}'::jsonb,
                created_at timestamptz NOT NULL DEFAULT now(),
                updated_at timestamptz NOT NULL DEFAULT now(),
                CONSTRAINT user_saved_view_name_uk UNIQUE (user_id, view_type, name)
            )
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_vulnerability_remediation_event_lookup
            ON vulnerability_remediation_event (customer_id, logical_finding_hash, changed_at DESC)
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_user_saved_view_user_type
            ON user_saved_view (user_id, view_type)
            """);
    }

    private void normalizeActiveSnapshots() {
        jdbcTemplate.execute("""
            UPDATE upload_details older
            SET is_active_snapshot = false
            WHERE older.is_active_snapshot = true
              AND older.software_id IS NOT NULL
              AND EXISTS (
                  SELECT 1
                  FROM upload_details newer
                  WHERE newer.customer_id = older.customer_id
                    AND newer.software_id = older.software_id
                    AND newer.is_active_snapshot = true
                    AND (
                        newer.uploaded_at > older.uploaded_at
                        OR (newer.uploaded_at = older.uploaded_at AND newer.id::text > older.id::text)
                    )
              )
            """);
    }
}
