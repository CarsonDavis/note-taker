# App Access Instructions — Google Play Review

Credentials for the Play Console "App access" form so reviewers can test the app.

## Instruction Name

`GitHub token setup for review`

## Username and Password

Not applicable — the app uses a GitHub Personal Access Token, not username/password.

## Any Other Information Required for Access

```
This app uses a GitHub Personal Access Token (not a password). On the setup screen:

1. Paste this token: <TOKEN — see Play Console credentials>
2. Enter repository: CarsonDavis/notes-playstore-review
3. Tap Continue — the app will confirm the token and show the main screen.
```

## Review Repo

https://github.com/CarsonDavis/notes-playstore-review

Dedicated empty repo for Play Store review. The fine-grained PAT above has Contents read/write access scoped to this repo only.

## Maintenance

- If the PAT expires, generate a new one and update both this file and the Play Console form.
- Keep the review repo clean — delete test notes periodically.
- Update the Play Console credentials before every app update submission.
