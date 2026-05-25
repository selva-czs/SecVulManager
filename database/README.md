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
