# Play Store Publishing Checklist

Step-by-step guide to publishing Note Taker on Google Play.

## Phase 1: Prerequisites

- [x] Google Play Developer account ($25 one-time fee)
- [ ] Privacy policy published (see [privacy-policy.md](privacy-policy.md))
- [ ] Store listing content finalized (see [store-listing.md](store-listing.md))
- [ ] Data safety answers prepared (see [data-safety-declaration.md](data-safety-declaration.md))

## Phase 2: Prepare Materials

- [ ] App icon — 512×512 PNG (see [store-listing.md](store-listing.md) for specs)
- [ ] Feature graphic — 1024×500 PNG
- [ ] Screenshots — at least 2, 1080×1920 (phone), ideally 4-8 showing key screens
- [ ] Short description (~80 chars)
- [ ] Full description (~2000 chars)

All text content is drafted in [store-listing.md](store-listing.md).

## Phase 3: Build the App

- [ ] Generate an upload keystore:
  ```bash
  keytool -genkeypair -v -keystore upload-keystore.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias upload-key \
    -dname "CN=Carson Davis, O=Personal, L=City, ST=State, C=US"
  ```
- [ ] Store the keystore password securely (you'll need it for GitHub Secrets)
- [ ] Build a signed AAB:
  ```bash
  KEYSTORE_FILE=path/to/upload-keystore.jks \
  KEYSTORE_PASSWORD=your-password \
  KEY_ALIAS=upload-key \
  KEY_PASSWORD=your-password \
  VERSION_CODE=1 \
  VERSION_NAME=1.0.0 \
  ./gradlew bundleRelease
  ```
- [ ] Output AAB: `app/build/outputs/bundle/release/app-release.aab`

> **Important:** Never commit the keystore file to git. It's already in `.gitignore`.

## Phase 4: Play Console Setup

### Create the App
- [ ] Go to [Play Console](https://play.google.com/console) → **Create app**
- [ ] App name: `Note Taker`
- [ ] Default language: English (United States)
- [ ] App or game: **App**
- [ ] Free or paid: **Free**
- [ ] Accept declarations

### Store Listing
- [ ] Enter short description and full description from [store-listing.md](store-listing.md)
- [ ] Upload app icon, feature graphic, and screenshots
- [ ] Set category: **Productivity**
- [ ] Add contact email

### Content Declarations
- [ ] **Content rating:** Complete the IARC questionnaire (see [store-listing.md](store-listing.md) for answers — expected rating: Everyone)
- [ ] **Target audience:** 18+ (not designed for children)
- [ ] **Data safety:** Complete using answers from [data-safety-declaration.md](data-safety-declaration.md)
- [ ] **Privacy policy:** Link to the GitHub-hosted privacy policy:
  ```
  https://github.com/CarsonDavis/note-taker/blob/master/docs/playstore/privacy-policy.md
  ```
- [ ] **Ads declaration:** Does not contain ads
- [ ] **Government apps:** Not a government app

### Permissions Declaration Form
- [ ] **RECORD_AUDIO** requires a Permissions Declaration Form
- [ ] Justification: On-device speech-to-text for note input. No audio is recorded, stored, or transmitted. Android's built-in `SpeechRecognizer` processes speech locally.
- [ ] May need to provide a short video demo showing the voice input feature
- [ ] This can add 1-2 weeks to the review process

## Phase 5: Testing Track

> **Note:** Personal developer accounts created after November 2023 must complete a 14-day closed testing period with at least 12 testers before gaining production access.

- [ ] Create a **Closed testing** track in Play Console
- [ ] Create a testers list and add at least 12 email addresses
- [ ] Upload the AAB from Phase 3 to the closed testing track
- [ ] Share the opt-in link with testers
- [ ] Wait 14 days with testers actively opted in
- [ ] After 14 days, production access unlocks

If your account predates November 2023, you can skip directly to Phase 6.

## Phase 6: First Production Release

- [ ] Go to **Production** → **Create new release**
- [ ] Upload the signed AAB (first upload must be manual via Play Console)
- [ ] Opt in to **Google Play App Signing** (required — Google re-signs with their key)
- [ ] Add release notes (e.g., "Initial release — capture voice and text notes, push to GitHub")
- [ ] **Review and roll out** → Start with a staged rollout (e.g., 20%)
- [ ] Monitor the Android vitals dashboard for crashes

> The first AAB must be uploaded manually. After that, CI/CD can handle subsequent releases.

## Phase 7: CI/CD Setup

After the first manual release, set up automated releases via GitHub Actions.

### Create a Google Play Service Account
- [ ] Go to **Google Cloud Console** → Create a new project (or use existing)
- [ ] Enable the **Google Play Android Developer API**
- [ ] Create a **Service Account** with no special roles
- [ ] Generate a JSON key for the service account
- [ ] In **Play Console** → Settings → API access → Link the Google Cloud project
- [ ] Grant the service account **Release manager** permissions for the app

### Configure GitHub Secrets
Add these 6 secrets to the repository (Settings → Secrets and variables → Actions):

| Secret | Contents |
|--------|----------|
| `KEYSTORE_BASE64` | Base64-encoded upload keystore (`base64 -i upload-keystore.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g., `upload-key`) |
| `KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full contents of the service account JSON key file |
| `PLAY_PACKAGE_NAME` | `com.carsondavis.notetaker` |

### Trigger a Release
```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow (`.github/workflows/release.yml`) will:
1. Build a signed AAB
2. Upload it to the Play Store **internal** track
3. Attach the AAB to the GitHub release

You then promote from internal → production manually in Play Console.
