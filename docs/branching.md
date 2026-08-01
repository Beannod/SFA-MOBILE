# Branching & Pull Request Policy — One Page

Purpose
- Provide a simple, predictable branching model and PR rules so local work, CI, and deployments remain consistent.

Branches
- `main` — production-ready. Always deployable. Protected: require PR, CI green, reviews.
- `dev` — integration branch for the next release. Merges from feature branches after review and CI.
- `feature/*` — short-lived feature branches named `feature/brief-description` or `feat/<ticket-number>-short`.
- `hotfix/*` — branch for urgent fixes to `main`: `hotfix/<short-desc>`.
- `release/*` (optional) — used when preparing a release from `dev` to `main`.

Workflow
1. Create `feature/*` branch from `dev` (or `main` for urgent hotfixes).
2. Work locally; commit often with clear messages. Follow atomic change principle.
3. Push branch and open a Pull Request into `dev` (or `main` for hotfixes).

Pull Request Requirements
- Title: concise summary. Include ticket/issue id if available (e.g., `JIRA-123: Add customer import`).
- Description: short intent, list of changed files/modules, how to test locally, and any migration or config changes.
- Labels: add `feature`, `bug`, `chore`, or `docs` as appropriate.
- Reviewers: at least one code reviewer (two for major changes). Include backend and frontend owners when both areas are affected.
- CI: All GitHub Actions checks must pass (build, tests, lint). Fix CI failures before merging.
- Approvals: require at least one approving review (two for release merges).
- Merge method: use `squash and merge` for feature branches to keep history compact; use `merge` for Release/hotfix if preserving history is desired.

Protection Rules (recommended GitHub settings)
- Protect `main` and `dev`: require PRs, require status checks to pass, require review approvals, disallow force pushes, require up-to-date branch before merge.

Testing & Quality
- Unit and integration tests should run in CI. PRs that touch backend or database changes must include or update tests where practical.
- For frontend changes, include a short QA checklist in the PR description (browser/cross-device checks if applicable).

Release Flow (high-level)
1. Merge feature branches into `dev` as features complete.
2. When ready for a release, create `release/x.y` from `dev` or open PR `dev -> main` and perform release validations (smoke tests, migration review).
3. Merge to `main` and tag the release (e.g., `v1.2.0`). Render and Cloudflare auto-deploy from `main`.
4. If issues found in production, create `hotfix/*` from `main`, fix, test, then merge back to `main` (and `dev` if needed).

Conventions & Best Practices
- Keep PRs small and focused; prefer multiple small PRs to one large change.
- Include migration and docs updates in the same PR when schema or public contract changes occur.
- Use meaningful commit messages and reference issues/tickets.
- Do not commit secrets or environment-specific config — use `appsettings.*.json` or environment variables and update `deploy/env.server.production.example`.

Exceptions
- For emergency changes (critical security fixes), follow the hotfix flow and note the reason in the PR and release notes.

Questions
- If you're unsure which branch to use or need an exception, ask a repository maintainer in the PR or team chat before merging.
