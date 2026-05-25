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
        backfillUploadSoftwareAndStats();
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
}
