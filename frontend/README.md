# SecVulManager Frontend

This folder contains the React + Vite single-page application for SecVulManager.

The UI is intentionally operational and task-oriented: list/table pages first, full-page workspaces for item interactions, dense filters, and explicit save/back actions. It is not a marketing site.

## Stack

- React 19 with functional components and hooks.
- Vite for local development and production builds.
- JavaScript modules, not TypeScript.
- Tailwind utility classes plus local theme overrides in `src/index.css`.
- `lucide-react` for visible action icons.
- API calls are centralized in `src/api.js`.

## Main Files

- `src/App.jsx`: main shell, navigation, list pages, workspaces, template mapper, upload flow, and shared UI primitives.
- `src/api.js`: credentialed `fetch` wrapper and REST API methods.
- `src/index.css`: Tailwind setup, dark/light theme overrides, scrollbar, and button theme compatibility.
- `src/App.formIndicators.test.js`: static regression coverage for required/optional/default field indicators and persistent filter labels.

## Local Development

From this folder:

```bash
npm run dev -- --host 127.0.0.1
npm run lint
npm run build
node --test src/App.formIndicators.test.js
```

The backend API defaults to `/api` through the Vite dev proxy/runtime origin. If needed, set `VITE_API_BASE_URL` to point at a different backend.

## UI Principles

- Main management areas start with searchable, filterable tables.
- Row details open as full pages/workspaces inside the shell, not right-side panels.
- Normal item interactions should not use browser-native dialogs. Use themed in-app confirmation/summary components.
- Required, optional, and defaulted fields should be visually indicated through shared field wrappers.
- Dense filter controls should keep persistent labels instead of relying only on placeholders.
- Keep dark and light theme compatibility by using the existing slate/brand classes and `theme-light` overrides.
- Reuse `Button`, `Field`, `FilterField`, `StatusPill`, `StatusToggle`, `InteractionPage`, and related primitives before adding new UI patterns.

## Current Product Areas

- Vulnerability dashboard and active finding review.
- Vulnerability management upload history and active/historical snapshot filters.
- Upload scan file workspace with queue handling when a customer already has an upload running.
- Security Software Manager for vendor lifecycle and template access.
- Customer Management for customer lifecycle and customer/software assignments.
- User Management for user lifecycle and customer access.
- Template Mapping Workspace for sample extraction, auto-map, required field review, value rules, save summaries, draft saves, and activation choice.

## Upload UX Notes

The upload flow supports three backend queue modes:

- `REJECT_IF_BUSY`: default; blocks if another upload is running for the customer.
- `QUEUE`: queues the upload behind the running customer upload.
- `FORCE_ACTIVATE_WHEN_DONE`: queues the upload and marks it to replace the active snapshot when it finishes successfully.

Upload history displays status, processing stage, queue comment, processed/total counts, active snapshot state, failed rows, and original upload download actions.
