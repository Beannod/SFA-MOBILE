# Contributing Checklist

Thank you for contributing. Follow this quick checklist to make PRs simple and reviewable.

- Read the branching policy: `docs/branching.md` before creating a branch.
- Start work on a feature branch: `feature/<short-desc>` (branch from `dev`).
- Run the app locally before opening a PR:
  - Backend: `dotnet run --project backend/server/SfaApi.csproj`
  - Frontend (quick): `powershell .\scripts\dev.ps1` or serve `frontend/web-ui`
- Run tests locally: `dotnet test tests/SfaApi.IntegrationTests/SfaApi.IntegrationTests.csproj`
- Update/Add tests for backend changes; include a brief QA checklist for frontend changes.
- Keep PRs small and focused; include:
  - A short description of intent and how to test locally.
  - Any migration steps or config changes.
  - Links to related issues/tickets.
- Do not commit secrets or production config. Use `.env.example` and `deploy/env.server.production.example` as references.
- CI must pass (GitHub Actions). Fix CI failures before merging.
- Request reviewers (backend and frontend owners when both areas are affected).
- Merge method: `Squash and merge` for features; use `merge` for hotfixes/releases per policy.

If you're unsure about anything, open a draft PR and ask a maintainer for guidance.
