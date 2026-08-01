# Pull Request Template

## Summary
Provide a short description of the change and why it is needed.

## Related Issue
- Fixes # (link issue number)

## How to Test Locally
- Pull the branch
- Start backend: `dotnet run --project backend/server/SfaApi.csproj`
- Start frontend: `powershell .\scripts\dev.ps1` (or serve `frontend/web-ui`)
- Reproduce the change and verify expected behavior

## Checklist
- [ ] CI passes (GitHub Actions)
- [ ] I ran tests locally: `dotnet test tests/SfaApi.IntegrationTests/SfaApi.IntegrationTests.csproj`
- [ ] I updated `docs/` if the change affects behavior or configuration
- [ ] No secrets or production config are included in the PR
- [ ] Added/updated tests for new behavior (backend or integration)

## Notes for Reviewers
- Any migration steps, required env vars, or platform notes
- Any manual steps to validate in staging or preview
<!-- Pull Request Template: remind authors to update docs when APIs change -->
# Pull Request

## Summary
Describe the change and why it was made.

## Changes
- Describe what files/logic changed.

## Checklist — REQUIRED for PRs that add or modify APIs/controllers
- [ ] I updated `docs/software-documentation.md` with any changed/new endpoints or request/response shapes.
- [ ] I updated `.github/copilot-instructions.md` if I added/renamed controllers, major pages, or mobile screens.
- [ ] I updated any client code examples or front-end pages that consume the API.
- [ ] I ran the API tests: `scripts/test-api.ps1` and fixed failures.

## Other Checks
- [ ] I ran the mobile build locally if I changed mobile code: `cd mobile && .\gradlew.bat assembleDebug`.
- [ ] I added/update integration/unit tests where appropriate.

If you changed database migrations or models, include migration instructions in the PR description.

Thanks — reviewers will verify docs and tests as part of review.
