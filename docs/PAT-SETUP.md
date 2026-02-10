# Creating a GitHub Personal Access Token

The app uses a fine-grained Personal Access Token (PAT) to write notes to your GitHub repository. This is a one-time setup that takes about 2 minutes.

## Steps

1. **Go to** [github.com/settings/personal-access-tokens/new](https://github.com/settings/personal-access-tokens/new)

2. **Token name**: `note-taker` (or anything you'll recognize)

3. **Expiration**: No expiration (or set a reminder to rotate)

4. **Repository access**: Select "Only select repositories", then pick your notes repo

5. **Permissions** → Repository permissions → **Contents**: Read and write

6. **Generate token** and copy it

7. **In the app**: paste the token and enter your repo as `owner/repo` (e.g., `CarsonDavis/notes`)

## Changing Repository or Rotating Token

1. Open the app → Settings → Sign Out
2. Create a new token (or edit the existing one at [github.com/settings/tokens](https://github.com/settings/tokens))
3. Re-enter the token and repo in the app's setup screen

## Security Notes

- The token is stored locally on your device via Android's Preferences DataStore
- It is only sent to `api.github.com` over HTTPS
- The token can only access the specific repository you selected -- not your other repos
- If your device is lost or compromised, revoke the token at [github.com/settings/tokens](https://github.com/settings/tokens)
