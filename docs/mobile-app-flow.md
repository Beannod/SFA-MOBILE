# Mobile App — Software Flow

## Purpose
Provide a clear, actionable software flow for the mobile application used in this workspace: app architecture, major components, user flows, data model and sync, security, CI/CD and testing guidance for further study or implementation.

## Goals
- Reliable offline-first data capture and sync
- Simple discoverable instances (if app talks to local/dev servers)
- Minimal latency for UI interactions
- Secure authentication and data transport

## Platforms
- Android (primary) — the project includes a `mobile/app` Android app.
- Potential iOS parity (design/flow applies across platforms).

## High-level Architecture
- Mobile UI (Views / Activities / Fragments)
- Local persistence (SQLite / Room / Realm) for offline
- Sync layer (HTTP/REST or gRPC) with queued background sync + conflict resolution
- Network layer (retry, backoff, connectivity awareness)
- Native modules for platform-specific features (location, camera, push)
- Background worker (WorkManager/JobScheduler) for scheduled sync

## Core Components
- UI: screens for Login, Home/Dashboard, List, Detail/Edit, Sync status, Settings
- Auth: secure token-based (OAuth2/JWT) with refresh tokens or Windows-auth where appropriate
- Local DB: canonical models, change-tracking table for sync (created/updated/deleted)
- Sync Engine: upload queue, incremental download, schema version handling
- Networking: centralized API client with interceptors (auth, logging, retry)
- Feature flags and remote config for toggling experiments

## Primary User Flows (step-by-step)

1) First-time launch / Onboarding
  - Show welcome and permissions (storage, network, camera if needed)
  - Offer login/guest; after login fetch basic profile & app config
  - Initialize local DB and immediate background sync for seeds

2) Login / Authentication
  - UI collects credentials, posts to `/auth/token`
  - Store tokens in secure storage (Android Keystore / EncryptedSharedPreferences)
  - Start background sync and fetch user-specific data

3) Data browsing and editing
  - List screen loads from local DB; UI marks sync state (synced, pending)
  - Create/Edit item writes to local DB and marks row state = `dirty`
  - Enqueue change for background upload; optimistic UI update

4) Offline flow and conflict handling
  - App fully usable offline via local DB; queued changes persist across restarts
  - Sync attempts apply change-by-change; server conflict returns version or merge strategy
  - Conflict resolution: prefer server, prefer client, or present merge UI per entity

5) Sync lifecycle
  - Background worker runs periodically and on connectivity events
  - Upload: batch pending changes (with limits), apply server responses (IDs/version)
  - Download: fetch server changes since last sync token (cursor/timestamp)
  - Robust error handling: exponential backoff, pause on auth errors, retry later

6) Logout and data wipe
  - Revoke token on server (if supported), clear local sensitive data, return to onboarding

## Data Model & API Contracts (suggested)
- Use stable JSON schemas or small protobufs
- Include `id`, `createdAt`, `updatedAt`, `version` (optimistic concurrency), `deleted` flag
- Sync endpoints:
  - POST /sync/upload { items: [...] }
  - GET /sync/changes?since=<cursor>
  - POST /auth/token, POST /auth/refresh

## Security
- TLS all network traffic; pin certificates if needed for higher trust
- Secure local storage for tokens and PII
- Use least-privilege API tokens; rotate regularly
- Protect debug/release builds differently (do not ship debug logs)

## Reliability & Monitoring
- Local logging with optional crash / analytics (Sentry, Firebase Crashlytics)
- Track sync success/failure metrics, queue length, conflict rates
- Health endpoint tests for back-end and synthetic mobile tests in CI

## CI / Build / Release
- Build pipelines: lint → unit tests → integration tests → artifact signing
- Releases: staged rollout, monitor crash/error metrics and telemetry
- Fastlane or equivalent for app store automation (if iOS included)

## Testing Strategy
- Unit tests: ViewModels, domain logic, sync engine logic
- Integration tests: DB + network using in-memory DB and mocked APIs
- End-to-end: automated UI tests (Espresso / UI Automator for Android)
- Manual testing checklist for offline/online switching, conflict cases, and upgrades

## Developer Tooling & Local Dev Server
- Provide local API host config (app settings) for development
- If using SQL Server locally, ensure `SQL Server Browser` is running and firewall allows UDP 1434 for discovery when needed
- Document seed data loading and developer credentials

## UX Considerations
- Make sync state visible but unobtrusive (icon or small banner)
- Allow users to retry failed syncs and show clear error states when manual action required
- Avoid blocking UI on long syncs — run in background and notify when action required

## Next Steps / Deliverables
- Implement screen-by-screen wireframes for each flow above
- Create a minimal API mock server to exercise the sync engine
- Write the initial sync engine skeleton and local DB schema

---
File created for study and implementation. If you want, I can:
- generate screen wireframes (Markdown + images)
- scaffold the sync engine code template in the Android project
- create a mock API server (Node/Express) for local development

Tell me which next action you want and I will implement it.
