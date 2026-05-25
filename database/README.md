# SecVulManager PostgreSQL Bootstrap

This folder contains a git-safe empty PostgreSQL baseline for SecVulManager.

It creates all application tables, constraints, indexes, and the minimum seed records needed to log in.

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

Optional standard software registry seed:

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
- No customers, templates, uploads, findings, remediation rows, or customer assignments are seeded.
- `03_seed_standard_software.sql` is optional. The current backend `DatabaseSeeder` also creates `Kaseya`, `Rapidfire`, and `Nessus` when missing.
- Upload history is stored in `upload_details`; normalized vulnerabilities are stored in `vulnerability_finding`.
- Active vulnerabilities are not stored in a separate table. They are selected through `upload_details.is_active_snapshot = true`.
- The active snapshot scope is `customer_id + software_id`, so each customer can have one current active upload per vendor/software.
- Upload statistics include `total_records`, `successful_records`, `failed_records`, and `warning_records`.
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
