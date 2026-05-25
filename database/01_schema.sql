BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(100) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    full_name varchar(255) NOT NULL,
    role varchar(50) NOT NULL,
    is_enabled boolean NOT NULL DEFAULT true,
    is_archived boolean NOT NULL DEFAULT false,
    archived_at timestamptz,
    archived_by varchar(100),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_role_chk CHECK (role IN ('SUPER_ADMIN', 'GLOBAL_OPERATOR', 'CUSTOMER_OPERATOR', 'SECURITY_OPERATOR'))
);

CREATE TABLE IF NOT EXISTS customer (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name varchar(255) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100),
    is_enabled boolean NOT NULL DEFAULT true,
    is_archived boolean NOT NULL DEFAULT false,
    archived_at timestamptz,
    archived_by varchar(100),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(100)
);

CREATE TABLE IF NOT EXISTS security_software (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    software_name varchar(100) NOT NULL UNIQUE,
    is_enabled boolean NOT NULL DEFAULT true,
    is_archived boolean NOT NULL DEFAULT false,
    archived_at timestamptz,
    archived_by varchar(100),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_customer_access (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_customer_access_uk UNIQUE (user_id, customer_id)
);

CREATE TABLE IF NOT EXISTS customer_software_access (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    software_id uuid NOT NULL REFERENCES security_software(id) ON DELETE CASCADE,
    is_enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customer_software_access_uk UNIQUE (customer_id, software_id)
);

CREATE TABLE IF NOT EXISTS customer_template (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid REFERENCES customer(id) ON DELETE CASCADE,
    software_id uuid NOT NULL REFERENCES security_software(id) ON DELETE RESTRICT,
    name varchar(100) NOT NULL,
    description text,
    file_format varchar(20) NOT NULL DEFAULT 'CSV',
    has_header_row boolean NOT NULL DEFAULT true,
    is_enabled boolean NOT NULL DEFAULT true,
    is_archived boolean NOT NULL DEFAULT false,
    archived_at timestamptz,
    archived_by varchar(100),
    sample_file_path text,
    column_mapping_json text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz,
    CONSTRAINT customer_template_file_format_chk CHECK (file_format IN ('CSV', 'TSV', 'PSV', 'XLS', 'XLSX'))
);

CREATE TABLE IF NOT EXISTS upload_details (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE RESTRICT,
    template_id uuid REFERENCES customer_template(id) ON DELETE SET NULL,
    uploaded_by varchar(100) NOT NULL,
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    file_name varchar(255) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'PROCESSING',
    total_records integer NOT NULL DEFAULT 0,
    failed_records integer NOT NULL DEFAULT 0,
    error_summary text,
    error_log_path text,
    sample_file_path text,
    is_active_snapshot boolean NOT NULL DEFAULT false,
    CONSTRAINT upload_details_status_chk CHECK (status IN ('PROCESSING', 'SUCCESS', 'PARTIAL_FAILURE', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS vulnerability_finding (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id uuid NOT NULL REFERENCES upload_details(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    severity varchar(20) NOT NULL DEFAULT 'MEDIUM',
    cvss_score numeric(3, 1),
    cvss_vector varchar(100),
    cve_id varchar(50),
    oid text,
    issue_title text NOT NULL,
    summary text,
    impact text,
    solution text,
    vulnerability_insight text,
    vulnerability_detection_result text,
    vulnerability_detection_method text,
    affected_devices text,
    number_of_devices integer DEFAULT 0,
    references_info text,
    known_exploited boolean NOT NULL DEFAULT false,
    known_ransomware_campaign boolean NOT NULL DEFAULT false,
    last_detected_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vulnerability_finding_severity_chk CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE IF NOT EXISTS vulnerability_remediation_status (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
    logical_finding_hash varchar(64) NOT NULL,
    workflow_status varchar(50) NOT NULL DEFAULT 'OPEN',
    notes varchar(4000),
    updated_by varchar(100) NOT NULL,
    last_updated timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vulnerability_remediation_status_uk UNIQUE (customer_id, logical_finding_hash)
);

CREATE INDEX IF NOT EXISTS idx_customer_enabled_archived ON customer (is_archived, is_enabled);
CREATE INDEX IF NOT EXISTS idx_security_software_enabled_archived ON security_software (is_archived, is_enabled);
CREATE INDEX IF NOT EXISTS idx_customer_template_customer ON customer_template (customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_template_software ON customer_template (software_id);
CREATE INDEX IF NOT EXISTS idx_customer_template_active ON customer_template (is_archived, is_enabled);
CREATE INDEX IF NOT EXISTS idx_customer_software_access_customer ON customer_software_access (customer_id);
CREATE INDEX IF NOT EXISTS idx_upload_details_customer_uploaded_at ON upload_details (customer_id, uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_vulnerability_finding_customer ON vulnerability_finding (customer_id);
CREATE INDEX IF NOT EXISTS idx_vulnerability_finding_upload ON vulnerability_finding (upload_id);
CREATE INDEX IF NOT EXISTS idx_vulnerability_finding_severity ON vulnerability_finding (severity);

COMMIT;
