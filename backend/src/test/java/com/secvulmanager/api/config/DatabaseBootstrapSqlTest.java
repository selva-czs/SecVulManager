package com.secvulmanager.api.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseBootstrapSqlTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void baselineSchemaContainsApplicationTablesColumnsAndConstraints() throws IOException {
        String schema = read("database/01_schema.sql");

        for (String table : List.of(
            "app_user",
            "customer",
            "security_software",
            "user_customer_access",
            "customer_software_access",
            "customer_template",
            "upload_details",
            "vulnerability_finding",
            "vulnerability_remediation_status",
            "vulnerability_remediation_event",
            "user_saved_view"
        )) {
            assertContains(schema, "CREATE TABLE IF NOT EXISTS " + table);
        }

        for (String column : List.of(
            "software_id uuid REFERENCES security_software(id) ON DELETE SET NULL",
            "processed_records integer NOT NULL DEFAULT 0",
            "processing_stage varchar(60) NOT NULL DEFAULT 'FILE_STORED'",
            "queue_mode varchar(60) NOT NULL DEFAULT 'REJECT_IF_BUSY'",
            "replace_active_when_done boolean NOT NULL DEFAULT false",
            "uploaded_file_path text",
            "known_ransomware_campaign boolean NOT NULL DEFAULT false"
        )) {
            assertContains(schema, column);
        }

        assertContains(schema, "CONSTRAINT app_user_role_chk CHECK");
        assertContains(schema, "CONSTRAINT customer_template_file_format_chk CHECK");
        assertContains(schema, "CONSTRAINT upload_details_processing_stage_chk CHECK");
        assertContains(schema, "CONSTRAINT upload_details_queue_mode_chk CHECK");
        assertContains(schema, "CONSTRAINT vulnerability_finding_severity_chk CHECK");
        assertContains(schema, "CONSTRAINT vulnerability_remediation_status_workflow_chk CHECK");
        assertContains(schema, "'ACCEPTED_RISK'");
        assertContains(schema, "CONSTRAINT user_saved_view_name_uk UNIQUE (user_id, view_type, name)");
        assertContains(schema, "CREATE INDEX IF NOT EXISTS idx_user_saved_view_user_type");
        assertContains(schema, "CREATE INDEX IF NOT EXISTS idx_vulnerability_remediation_event_lookup");
        assertContains(schema, "CREATE UNIQUE INDEX IF NOT EXISTS ux_upload_details_customer_running");
        assertContains(schema, "CREATE UNIQUE INDEX IF NOT EXISTS ux_upload_active_customer_software");
    }

    @Test
    void minimalSeedCreatesOnlyRequiredBootstrapRows() throws IOException {
        String seed = read("database/02_seed_minimal.sql");

        assertContains(seed, "INSERT INTO app_user");
        assertContains(seed, "'admin'");
        assertContains(seed, "'SUPER_ADMIN'");
        assertContains(seed, "INSERT INTO security_software");
        assertContains(seed, "('Kaseya', true, false, NULL, NULL, now())");
        assertContains(seed, "('Rapidfire', true, false, NULL, NULL, now())");
        assertContains(seed, "('Nessus', true, false, NULL, NULL, now())");
        assertContains(seed, "ON CONFLICT (username) DO UPDATE");
        assertContains(seed, "ON CONFLICT (software_name) DO UPDATE");
    }

    @Test
    void applicationUsesSqlBootstrapAsSchemaSourceOfTruth() throws IOException {
        String properties = read("backend/src/main/resources/application.properties");
        String readme = read("database/README.md");

        assertContains(properties, "spring.jpa.hibernate.ddl-auto=validate");
        assertContains(readme.replace("`", ""), "01_schema.sql plus 02_seed_minimal.sql is the source of truth");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath));
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected to find: " + expected);
    }
}
