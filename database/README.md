# SecVulManager PostgreSQL Bootstrap

This folder contains the PostgreSQL baseline for the latest SecVulManager schema.

For a fresh database, `01_schema.sql` plus `02_seed_minimal.sql` is the source of truth. Together they create all application tables, constraints, indexes, the default admin login, and the standard security software registry rows expected by the application.

The backend is configured with `spring.jpa.hibernate.ddl-auto=validate`, so application startup validates this baseline instead of creating or migrating tables. If validation fails on a local database, reset and reapply the scripts below.

Default seeded login:

- Username: `admin`
- Password: `admin_pass`
- Role: `SUPER_ADMIN`

## Create A Fresh Local Database

From the repository root:

```bash
createdb secvulmanager
psql -d secvulmanager -f database/01_schema.sql
psql -d secvulmanager -f database/02_seed_minimal.sql
```

`03_seed_standard_software.sql` is retained only for older local workflows that seeded software separately. It is idempotent, but it is not required after `02_seed_minimal.sql`.

```bash
psql -d secvulmanager -f database/03_seed_standard_software.sql
```

## Reset An Existing Local Database

This drops all application tables and data, then recreates the empty baseline:

```bash
psql -d secvulmanager -f database/00_reset.sql
psql -d secvulmanager -f database/01_schema.sql
psql -d secvulmanager -f database/02_seed_minimal.sql
```

## Notes

- The schema uses UUID primary keys generated with PostgreSQL `pgcrypto`.
- Java enums are stored as strings and protected with `CHECK` constraints.
- The bootstrap contract is covered by `backend/src/test/java/com/secvulmanager/api/config/DatabaseBootstrapSqlTest.java`.
- No customers, templates, uploads, findings, remediation rows, or customer assignments are seeded.
- `02_seed_minimal.sql` seeds the default admin user plus `Kaseya`, `Rapidfire`, and `Nessus`.
- The backend `DatabaseSeeder` also creates the same minimum rows when missing, as a local development safety net.
- Upload history is stored in `upload_details`; normalized vulnerabilities are stored in `vulnerability_finding`.
- Active vulnerabilities are not stored in a separate table. They are selected through `upload_details.is_active_snapshot = true`.
- Per-user vulnerability dashboard views are stored in `user_saved_view`; the saved filter and sort state lives in JSONB columns.
- The latest remediation state is stored in `vulnerability_remediation_status`; status/comment changes are appended to `vulnerability_remediation_event` for the state journey timeline.
- The active snapshot scope is `customer_id + software_id`, so each customer can have one current active upload per vendor/software.
- Upload statistics include `total_records`, `processed_records`, `successful_records`, `failed_records`, and `warning_records`.
- Upload queue/progress fields live in `upload_details`: `processing_stage`, `queue_mode`, `queue_comment`, `queued_at`, `started_at`, `finished_at`, and `replace_active_when_done`.
- Original upload downloads use `uploaded_file_path`; `sample_file_path` remains for backward compatibility with older local databases.
- Failed uploads never replace an active snapshot. Partial uploads replace active snapshots only when at least 50% of rows succeed; lower-success partial uploads stay historical until a user manually activates them.
- Template activation is limited to one active template per software scope:
  - one active global template per software
  - one active customer template per customer + software
  - replacing an active template is an explicit application action that disables the previous active template.
- New ready templates can be saved active or inactive. Inactive template creation must persist `is_enabled=false` immediately and must not replace an existing active template.
- `/api/software` returns calculated assignment summary fields for list views:
  - `assignedCustomerCount`
  - `enabledAssignedCustomerCount`
  These are API-only/transient values and are not physical columns in `security_software`.
- Runtime files under `backend/uploads/`, `backend/scan-uploads/`, and `backend/failed-uploads/` are ignored by git. Future product exports should be tracked as export-job metadata in the database, not committed as source files.
