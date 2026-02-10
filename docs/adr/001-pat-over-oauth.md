# ADR 001: Fine-Grained PAT Over OAuth for Authentication

## Status

Accepted

## Context

The note-taker app needs to authenticate with the GitHub API to create files in a single repository. The app is a single-user personal tool running on one device.

The initial implementation used GitHub OAuth App device flow. This worked, but the `repo` scope grants access to ALL user repositories -- not just the target notes repo. A migration to GitHub App was attempted to get fine-grained repo permissions, but this introduced significant complexity (installation management, callback URLs, installation-scoped tokens).

## Decision

Use a **GitHub fine-grained Personal Access Token (PAT)** instead of any OAuth flow.

The user creates a token on github.com scoped to a single repository with Contents read/write permission, then pastes it into the app along with the `owner/repo` name.

## Alternatives Considered

### OAuth App + Device Flow (original implementation)
- **Pro:** Standard OAuth flow, no manual token management
- **Con:** `repo` scope grants access to ALL repositories. No way to scope to a single repo.
- **Con:** Requires registering and maintaining an OAuth App on GitHub

### GitHub App + Device Flow
- **Pro:** Fine-grained permissions per repository
- **Con:** Requires GitHub App registration, callback URL (even if unused), installation management
- **Con:** Token discovery requires extra API calls (`/user/installations`, `/user/installations/{id}/repositories`)
- **Con:** Significantly more complex for a single-user app

### GitHub App + PKCE
- **Pro:** Modern OAuth standard
- **Con:** Requires redirect URI handling (custom scheme or localhost)
- **Con:** All the same GitHub App complexity as above
- **Con:** Overkill for a personal tool

## Consequences

### Positive
- Zero OAuth infrastructure -- no app registration, no client IDs, no callback URLs
- User controls exact repo scope natively via GitHub's PAT UI
- Simpler code: no polling, no device codes, no installation discovery
- Token works identically with the existing `Bearer` auth in API calls
- AuthManager.saveAuth/saveRepo/signOut unchanged

### Negative
- User must manually create a token on github.com (one-time setup, ~2 minutes)
- User must manually enter `owner/repo` (no repo list to pick from)
- Token rotation requires sign out + re-setup (acceptable for single-user)
- No automatic token refresh (fine-grained PATs can be set to never expire)
