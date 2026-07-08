# SFA Mobile — Copilot Instructions

## Project Overview

Sales Force Automation system for tile/marble distributors.
- **Backend:** ASP.NET Core 7 API — `server/`
- **Mobile:** Android Jetpack Compose — `mobile/`
- **Docs:** `docs/` (software-documentation.md, user-guide.md, feature-plan.md, mobile-app-flow.md)

## Documentation Rule — ALWAYS APPLY

**After every change to server or mobile code, check if any documentation is affected and update it.**

Specifically:

| If you change... | Check and update... |
|---|---|
| A controller endpoint (add/remove/rename, new params, new response fields) | `docs/software-documentation.md` — the relevant module section + API Reference table (Section 15) |
| A new feature or module | `docs/software-documentation.md` — add a new section; `docs/user-guide.md` — add the user-facing steps; `README.md` — add to the ✅ Implemented Features list |
| User roles, permissions, or designation hierarchy | `docs/software-documentation.md` Section 3; `docs/user-guide.md` Section 1 |
| A DB model / new table / new field | `docs/software-documentation.md` — update the Database Tables list (Section 2) and the relevant field table |
| Order status lifecycle or business rules | `docs/software-documentation.md` Section 5; `docs/user-guide.md` Section 4 |
| Attendance, Location, Notifications logic | The matching section in both `software-documentation.md` and `user-guide.md` |
| Setup steps, config, or scripts | `docs/software-documentation.md` Sections 16–17; `README.md` Setup section |
| A feature is completed (was ⬜, now ✅) | `docs/feature-plan.md` — mark ✅; `README.md` — add to Implemented Features |

**Do not skip documentation updates** even for small changes like renaming a field or adding a filter parameter.

## Architecture Conventions

- All timestamps use `NepalTime.Now` from `server/Services/NepalTime.cs` (NPT, UTC+5:45). Never use `DateTime.Now` or `DateTime.UtcNow` directly in controllers.
- DB table names use `_sfa` suffix (e.g. `user_sfa`, `order_sfa`).
- Customer `ApprovalStatus` defaults to `"Pending"` on creation — orders require an Approved customer.
- Password hashing: BCrypt via BCrypt.Net-Next. Plain-text fallback for legacy passwords only.
- `ReportsToId` FK on users enforces designation hierarchy (manager must have lower level number = higher authority).

## Docs Location

- `docs/software-documentation.md` — technical reference for developers and admins
- `docs/user-guide.md` — non-technical guide for end users (salespersons, supervisors)
- `docs/feature-plan.md` — feature tracking with ✅/⬜ status and sprint roadmap
- `docs/mobile-app-flow.md` — mobile architecture and sync patterns
- `README.md` — project overview with completed feature list

## Test Suite

- `scripts/test-api.ps1` — full API test suite, must pass 100% (currently 51 PASS / 0 FAIL)
- After any controller change, run the test suite and fix any failures before finishing.
