BEGIN;

DROP TABLE IF EXISTS vulnerability_remediation_status CASCADE;
DROP TABLE IF EXISTS vulnerability_remediation_event CASCADE;
DROP TABLE IF EXISTS user_saved_view CASCADE;
DROP TABLE IF EXISTS vulnerability_finding CASCADE;
DROP TABLE IF EXISTS upload_details CASCADE;
DROP TABLE IF EXISTS customer_template CASCADE;
DROP TABLE IF EXISTS customer_software_access CASCADE;
DROP TABLE IF EXISTS user_customer_access CASCADE;
DROP TABLE IF EXISTS template_definition CASCADE;
DROP TABLE IF EXISTS security_software CASCADE;
DROP TABLE IF EXISTS customer CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;

COMMIT;
