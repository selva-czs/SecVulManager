# SecVulManager Agent Context

This file is a quick architecture and product context note for future coding-agent sessions working in this folder.

## Project Purpose

SecVulManager is a vulnerability ingestion, normalization, remediation tracking, and administration application.

The product lets an operator upload vulnerability export files from security tools, map each vendor file format into a standard PostgreSQL-backed vulnerability schema, ingest the records for a customer, review active findings, track remediation workflow status, and administer customers, users, security software vendors, and import templates.

## Repository Layout

- `frontend/`: React + Vite single-page application.
- `backend/`: Spring Boot REST API and ETL service.
- `backend/src/main/java/com/secvulmanager/api/model/`: JPA entities and enums.
- `backend/src/main/java/com/secvulmanager/api/controller/`: REST API controllers.
- `backend/src/main/java/com/secvulmanager/api/service/`: ingestion, authorization, and business logic.
- `backend/src/main/resources/application.properties`: local PostgreSQL and server configuration.

Generated folders such as `frontend/node_modules/`, `frontend/dist/`, and `backend/target/` are not architecture sources.

## Technologies Used

Frontend:

- React 19 with functional components and hooks.
- Vite for local development and production builds.
- JavaScript modules, not TypeScript.
- Tailwind CSS utility classes plus local CSS in `frontend/src/index.css`.
- `lucide-react` for UI icons.
- Browser `fetch` API wrapped by `frontend/src/api.js`.

Backend:

- Java 17.
- Spring Boot 3.3.
- Spring Web REST controllers.
- Spring Data JPA and Hibernate.
- Spring Security with BCrypt password hashes.
- PostgreSQL database.
- Apache Commons CSV for delimited file parsing.
- Apache POI for Excel parsing.
- Maven build.

Local default services:

- Frontend dev server: `http://127.0.0.1:5173/` or `http://localhost:5173/`.
- Backend API: `http://localhost:8080/api`.
- PostgreSQL database: `secvulmanager` on `localhost:5432`.

## Useful Commands

Frontend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
npm run lint
npm run build
```

Backend:

```bash
cd backend
mvn spring-boot:run
mvn test
```

Default seeded login, if the database is empty:

- Username: `admin`
- Password: `admin_pass`
- Role: `SUPER_ADMIN`

## Main User Flow

1. User logs in through the React app.
2. App loads session status, customers, security software, templates, users, findings, remediation data, and upload history through `frontend/src/api.js`.
3. Operator selects a customer context.
4. Operator manages security software vendors such as Kaseya, Rapidfire, and Nessus.
5. Operator creates or edits a customer-specific or global template for a software vendor.
6. Template setup includes choosing file type, uploading a sample file, extracting source columns, auto-mapping columns to the standard PostgreSQL vulnerability schema, reviewing transformation previews, and saving mappings.
7. Operator uploads a vulnerability export file using a selected customer and active template.
8. Backend ETL parses the file, applies template mappings and transformations, creates upload history, and stores vulnerability findings.
9. Operator reviews active findings in list view, opens individual detail pages, and updates remediation workflow status.
10. Admin users manage customers, users, customer access, software vendor status, and template activation.

## Current UI Concepts

The current frontend is centered in `frontend/src/App.jsx`.

Primary sections:

- Vulnerability Management: findings list, upload history list, finding detail page, upload detail page, remediation workflow updates.
- Security Software Manager: software vendor list with filters/search, active/inactive management, template counts, and software detail page.
- Customer Management: customer list with search, customer detail page, customer-specific template management.
- User Management: user list with search/status filter, user detail page, customer access page.
- Template Mapping Workspace: full-page template create/edit flow, not a small dialog.

Important UI behaviors:

- Main pages use list/table views with filters and search.
- Clicking a row opens an individual detail page or workspace in the main content area.
- Do not use dialogs/modals for normal item interactions, row details, upload forms, template editing, customer access, software details, customer details, or user details.
- Confirmation and summary prompts are the only popup interactions allowed.
- Confirmation and summary popups must be custom in-app components, not `window.confirm`, and must follow the active dark/light theme.
- Destructive actions, unsaved-change exits, and template save summaries should use the themed confirmation popup.
- Manual vulnerability creation and vulnerability edit/delete controls are intentionally hidden from the main user flow for now.
- Sidebar can be collapsed, pinned/unpinned, and expanded on hover when unpinned.
- UI supports dark and light themes.

## UI Interaction Principles

Future UI changes should preserve these product principles:

- Each management area starts with a searchable, filterable list/table page.
- List rows are clickable, but the selected item must render as a full page/workspace, not a right-side panel and not a modal.
- Full interaction pages should have a sticky page header with an icon, title, short context subtitle, and a Back action.
- The left navigation remains visible while interaction pages are open.
- The customer scope selector remains global in the shell header unless a page has a specific reason to lock scope.
- Keep operational UIs dense, scannable, and task-oriented; avoid landing-page, marketing, or card-heavy layouts.
- Use cards only for repeated items, metrics, or contained form sections. Do not nest cards inside cards.
- Use `lucide-react` icons for visible actions where a suitable icon exists.
- Do not use browser-native alerts, confirms, or prompts. Use the app's themed confirmation/summary popup.
- Keep dark and light theme compatibility for every new component by relying on the existing slate/brand utility classes and `theme-light` overrides in `frontend/src/index.css`.
- Text must fit in compact controls and table rows on desktop and mobile; use truncation or wrapping deliberately.
- Prefer direct controls: filters, search inputs, toggles, selects, checkboxes, and explicit save/back actions.

## Template Mapping Concepts

Templates define how a vendor export file maps into the internal vulnerability schema.

Supported file formats:

- `CSV`
- `TSV`
- `PSV`
- `XLS`
- `XLSX`

Template metadata:

- Name
- Description
- Software vendor
- Optional customer
- File format
- Whether the first row has headers
- Active/inactive status
- Mapping JSON

Mapping workspace behavior:

- If the first row has headers, extracted headers become the left-side source columns.
- If the file has no headers, the UI creates placeholder names like `Column_0` and shows the first data row so the user can enter meaningful source names.
- Auto Map uses string matching, aliases, partial matching, and token overlap to match source headers to known destination schema fields.
- Each mapped row shows source field, destination field, simple transformation, required/optional status, source preview, and output preview.
- Mandatory PostgreSQL destination fields that are not mapped are highlighted before save.
- Unmapped source columns and unfilled destination fields are summarized.
- Saving a template shows a confirmation summary before persisting.
- Closing or navigating away with unsaved template changes prompts for confirmation.
- Template save summary and unsaved-change confirmation must use the themed popup, not browser-native confirmation.

Simple transformation options currently exposed in the UI:

- `TRIM`
- `TO_UPPER`
- `TO_LOWER`
- `REMOVESPACES`
- `SET_NULL`

The backend enum contains additional transformation values for compatibility, but the UI intentionally keeps mapper operations simple.

## Standard Vulnerability Destination Schema

The primary destination entity is `VulnerabilityFinding`.

Important destination fields include:

- `issue_title`: required title of the finding.
- `severity`: normalized severity enum.
- `cvss_score`
- `cvss_vector`
- `cve_id`
- `oid`
- `summary`
- `impact`
- `solution`
- `vulnerability_insight`
- `vulnerability_detection_result`
- `vulnerability_detection_method`
- `affected_devices`
- `number_of_devices`
- `references_info`
- `known_exploited`
- `known_ransomware_campaign`
- `last_detected_at`

Findings are associated with:

- `Customer`
- `UploadDetails`

## Backend Domain Model

Core entities:

- `AppUser`: application user with username, password hash, full name, role, active flag.
- `Customer`: managed customer account.
- `UserCustomerAccess`: allowed customer assignments for non-global users.
- `SecuritySoftware`: software vendor registry item with active/inactive status.
- `CustomerTemplate`: template for vendor file ingestion and column mapping.
- `UploadDetails`: ingestion run metadata, status, counts, and error log path.
- `VulnerabilityFinding`: normalized vulnerability record.
- `VulnerabilityRemediationStatus`: workflow status and notes by logical finding/customer.

Key enums:

- `UserRole`: `SUPER_ADMIN`, `GLOBAL_OPERATOR`, `CUSTOMER_OPERATOR`, `SECURITY_OPERATOR`
- `FileFormat`: `CSV`, `TSV`, `PSV`, `XLS`, `XLSX`
- `UploadStatus`: `PROCESSING`, `SUCCESS`, `PARTIAL_FAILURE`, `FAILED`
- `SeverityLevel`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `TransformationType`: mapper transformation names.

## REST API Areas

Frontend API wrapper: `frontend/src/api.js`

Backend controllers:

- `/api/auth`: login, logout, status.
- `/api/customers`: customer CRUD.
- `/api/software`: security software vendor CRUD and active status updates.
- `/api/templates`: list, update, mapping save, sample extraction, delete.
- `/api/customers/{customerId}/templates`: customer-scoped templates.
- `/api/software/{softwareId}/templates`: global software templates.
- `/api/customers/{customerId}/software/{softwareId}/templates`: customer plus software templates.
- `/api/uploads`: file ingestion, upload history, error log download, sample download.
- `/api/vulnerabilities`: active findings and manual CRUD endpoints.
- `/api/vulnerabilities/remediation`: remediation workflow read/update.
- `/api/users`: user creation, access updates, active status updates.

## Security And Access Notes

- The frontend sends credentials with every API request using `credentials: 'include'`.
- CORS allows `http://localhost:5173` and `http://127.0.0.1:5173`.
- Spring Security is configured with a `UserDetailsService` backed by `AppUser`.
- Current `SecurityConfig` permits all requests at the HTTP filter level; access control is mainly handled by application logic and UI gating.
- Passwords are stored with BCrypt hashes.

## Ingestion And ETL Notes

- `ETLService` reads uploaded files, resolves the selected template, applies mapping JSON, transforms values, validates required destination fields, and stores findings.
- Delimited files are parsed with Apache Commons CSV.
- Excel files are parsed with Apache POI.
- Templates with `hasHeaderRow=false` use source column indexes so manually named columns still ingest correctly.
- Upload runs produce `UploadDetails` rows and may write failed-upload logs under `secvulmanager.failed-uploads-dir`.

## Development Guidance For Future Agents

- Read `frontend/src/App.jsx`, `frontend/src/api.js`, and relevant backend controller/model/service files before changing behavior.
- Preserve the list-view-first UI pattern: pages show searchable/filterable lists; details open as full pages or full workspaces only.
- Keep template mapping as a full-page workspace with clear left-source to right-destination mapping.
- Keep confirmation and summary popups themed and in-app. Do not add `window.confirm`, `window.alert`, or `window.prompt`.
- Do not introduce right-hand side detail panels or click-to-select master/detail layouts unless the user explicitly reverses this principle.
- Avoid reintroducing manual vulnerability management into the main UI unless the user explicitly asks for it.
- Reuse existing API wrapper methods instead of calling `fetch` directly from components.
- Use `lucide-react` icons for buttons and controls when icons are needed.
- Validate with `npm run lint`, `npm run build`, and `mvn test` when practical.
- Be careful with existing local changes; this folder may not be a git repository.
