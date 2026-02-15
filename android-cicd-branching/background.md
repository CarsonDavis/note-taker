# Research Background: Android CI/CD Branching Strategies with Google Play Deployment

**Date:** 2026-02-14
**Description:** Research into current best practices for Android app CI/CD branching strategies, automatic version management, multi-track Google Play deployment, and tooling (GitHub Actions, Fastlane, r0adkll/upload-google-play). Focus on solo developer / small team patterns, not enterprise scale.

## Sources

[1]: https://www.runway.team/blog/choosing-the-right-branching-strategy-for-mobile-development "Choosing the right branching strategy for mobile development - Runway"
[2]: https://blog.jetbrains.com/teamcity/2024/07/cicd-for-android/ "CI/CD for Android - JetBrains TeamCity Blog"
[3]: https://firebase.google.com/docs/app-distribution/best-practices-distributing-android-apps-to-qa-testers-with-ci-cd "Firebase App Distribution best practices"
[4]: https://capgo.app/blog/version-control-tips-for-mobile-ci-cd/ "Version Control Tips for Mobile CI/CD"
[5]: https://github.com/r0adkll/upload-google-play "r0adkll/upload-google-play GitHub repo"
[6]: https://github.com/marketplace/actions/upload-android-release-to-play-store "Upload Android Release to Play Store - GitHub Marketplace"
[7]: https://github.com/r0adkll/upload-google-play/blob/master/README.md "upload-google-play README"
[8]: https://github.com/marketplace/actions/promote-google-play-release-track "Promote Google Play Release Track - GitHub Marketplace"
[9]: https://blog.jakelee.co.uk/incrementing-version-automatically-after-release/ "Incrementing version automatically after release - Jake Lee"
[10]: https://github.com/marketplace/actions/increment-the-version-code-name-of-your-android-project "Increment version code/name GitHub Action"
[11]: https://adampaxton.com/how-create-a-version-number-with-github-actions-run-number-adding-an-offset-number/ "Version Number with GitHub Actions Run Number + Offset"
[12]: https://www.runway.team/blog/ci-cd-pipeline-android-app-fastlane-github-actions "CI/CD pipeline for Android with Fastlane and GitHub Actions - Runway"
[13]: https://www.paleblueapps.com/rockandnull/github-actions-android-version/ "Bump version code for Android apps using GitHub Actions"
[14]: https://engineering.empathy.co/android-ci-cd-with-github-actions/ "Applying CI/CD Using GitHub Actions for Android - Empathy"
[15]: https://github.com/r0adkll/upload-google-play/issues/39 "Issue #39: Only releases with status draft may be created on draft app"
[16]: https://github.com/r0adkll/upload-google-play/issues/70 "Issue #70: Only releases with status draft may be created on draft app"
[17]: https://github.com/r0adkll/upload-google-play/issues/82 "Issue #82: changesNotSentForReview error"
[18]: https://github.com/r0adkll/upload-google-play/issues/175 "Issue #175: changesNotSentForReview must not be set error"
[19]: https://github.com/r0adkll/upload-google-play/pull/88 "PR #88: changesNotSentForReview support"
[20]: https://docs.fastlane.tools/best-practices/continuous-integration/github/ "Fastlane GitHub Actions docs"
[21]: https://github.com/fastlane/fastlane "Fastlane GitHub repo"
[22]: https://github.com/PatilShreyas/AndroidFastlaneCICD "AndroidFastlaneCICD sample repo"
[23]: https://developersvoice.com/blog/mobile/mobile-cicd-blueprint/ "Mobile CI/CD in a Day: GitHub Actions + Fastlane + App Center (2025 Guide)"
[24]: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks "Google Play Developer API - edits.tracks"
[25]: https://developers.google.com/android-publisher/tracks "APKs and Tracks - Google Play Developer API"
[26]: https://android-developers.googleblog.com/2018/06/automating-your-app-releases-with.html "Automating your app releases with Google Play - Android Developers Blog"
[27]: https://blog.danskingdom.com/Multiple-ways-to-setup-your-CI-CD-pipelines-in-GitHub-Actions/ "Multiple ways to setup CI/CD pipelines in GitHub Actions"
[28]: https://www.maxivanov.io/github-actions-deploy-to-multiple-environments-from-single-workflow/ "GitHub Actions: deploy to multiple environments from single workflow"
[29]: https://github.com/orgs/community/discussions/25482 "Single workflow vs multiple workflows - GitHub Community"
[30]: https://blog.logrocket.com/android-ci-cd-using-github-actions/ "Android CI/CD using GitHub Actions - LogRocket"
[31]: https://ismailyenigul.medium.com/how-to-run-different-github-actions-for-the-different-git-branches-ede67b6083e3 "Different GitHub Actions for different branches"
[32]: https://alessandromautone.medium.com/automated-release-notes-for-android-8e3a22d00156 "Automated release notes for Android"
[33]: https://www.droidunplugged.com/2025/11/managing-app-versioning-and-changelogs-6.html "Managing App Versioning and Changelogs"
[34]: https://andresand.medium.com/publish-android-google-release-notes-a08d0acabb51 "Automate publishing release notes to Google Play Store"
[35]: https://www.tramline.app/blog/git-branching-patterns-for-mobile-app-development "Git branching patterns for mobile app development - Tramline"
[36]: https://jdam.cd/trunk-based-mobile/ "Trunk-based mobile - jdam.cd"
[37]: https://www.atlassian.com/continuous-delivery/continuous-integration/trunk-based-development "Trunk-based Development - Atlassian"
[38]: https://trunkbaseddevelopment.com/ "Trunk Based Development"
[39]: https://medium.com/@mirella4real/trunk-based-development-for-mobile-apps-138af49e11ea "Trunk-Based Development for Mobile Apps - Mirella Batista"
[40]: https://support.google.com/googleplay/android-developer/answer/9859152?hl=en "Create and set up your app - Play Console Help"
[41]: https://support.staffbase.com/hc/en-us/articles/360018569579-Preparing-Automated-Publishing-for-Google-Play-Store "Preparing Automated Publishing for Google Play Store - Staffbase"
[42]: https://support.google.com/googleplay/android-developer/answer/9859348?hl=en "Prepare and roll out a release - Play Console Help"
[43]: https://docs.fastlane.tools/actions/upload_to_play_store/ "upload_to_play_store - fastlane docs"
[44]: https://docs.fastlane.tools/actions/supply/ "supply - fastlane docs"
[45]: https://docs.fastlane.tools/getting-started/android/release-deployment/ "Release Deployment - fastlane docs"
[46]: https://github.com/codemagic-ci-cd/android-versioning-example "android-versioning-example - Codemagic"
[47]: https://github.com/ReactiveCircus/app-versioning "app-versioning Gradle Plugin - ReactiveCircus"
[48]: https://www.w3tutorials.net/blog/how-do-i-set-an-environment-variable-in-android-studio-before-it-runs-my-gradle-build/ "Set BUILD_NUMBER Environment Variable in Android Studio"
[49]: https://github.com/gladed/gradle-android-git-version "gradle-android-git-version plugin"
[50]: https://medium.com/dipien/versioning-android-apps-d6ec171cfd82 "Versioning Android Apps - Semantic Versioning - Gradle"
[51]: https://ychescale9.medium.com/git-based-android-app-versioning-with-agp-4-0-3f3a0fd134d5 "Git-based Android App Versioning with AGP 4.0"
[52]: https://support.google.com/googleplay/android-developer/answer/14151465?hl=en "App testing requirements for new personal developer accounts"
[53]: https://support.google.com/googleplay/android-developer/community-guide/255621488/everything-about-the-12-testers-requirement?hl=en "Everything about the 12 testers requirement"
[54]: https://www.revenuecat.com/blog/engineering/google-play-14-day/ "Navigating Google Play's 14-Day testing rule - RevenueCat"

## Research Log

<!-- Each search gets its own ### Search: entry, separated by --- dividers -->

---

### Search: "Android app CI/CD branching strategy best practices 2025 2026"

**Three main branching models emerge for mobile apps:**

- **Git Flow** -- Uses long-lived `develop` and `main` branches with supporting feature/release/hotfix branches. Ideal for scheduled release cycles. Structured but can be complex for continuous delivery. ([Runway][1])
- **GitHub Flow** -- Simpler model where all development starts from `main` in descriptive feature branches; PRs merge back to `main`. Good for teams that release frequently. Used by GitHub, Netflix. ([Runway][1])
- **Trunk-Based Development (TBD)** -- Developers merge small, frequent changes directly into a single main branch. Practiced by Google and Facebook. Requires robust CI to protect main. ([Runway][1])

**Key best practices noted:**
- Use separate branches for development, testing, and production ([Capgo][4])
- Implement consistent branching strategy with customized pipelines per branch to avoid running unnecessary steps on WIP code ([Capgo][4])
- Use semantic versioning (MAJOR.MINOR.PATCH) for clarity ([Capgo][4])
- Protect main branch with pull request rules ([JetBrains][2])
- Firebase recommends using CI/CD with App Distribution for QA testing separate from production ([Firebase][3])

---

### Search: "r0adkll upload-google-play GitHub Action documentation multi-track deployment"

**The `r0adkll/upload-google-play` action** is the most popular GitHub Action for uploading Android apps to Google Play. ([r0adkll GitHub][5], [GitHub Marketplace][6])

- **Track parameter**: Specifies which track to assign the uploaded app to. Defaults to `production`. ([r0adkll README][7])
- **Staged rollouts**: Can control the percentage of users who get the staged version ([r0adkll README][7])
- **Multiple files**: Accepts multiple .apk or .aab files separated by comma ([r0adkll README][7])
- **Track progression**: Before targeting production, may need to push at least one release through an earlier track like `internal`, `alpha`, or `beta` ([r0adkll README][7])
- **Companion action**: There's a separate "Promote Google Play Release Track" action for promoting releases between tracks ([GitHub Marketplace][8])

---

### Search: "Android versionCode automatic increment CI/CD GitHub Actions best practices"

**Five main approaches for automatic versionCode management:**

1. **`github.run_number`** -- Unique number for each run of a workflow. Can be passed directly as versionCode. Simple and reliable. ([Adam Paxton][11])
2. **`github.run_number` with offset** -- If app already has existing version codes, add an offset to start numbering higher than current run number. ([Adam Paxton][11])
3. **GitHub Marketplace Actions** -- Community actions like `chkfung/android-version-actions` and `Devofure/advance-android-version-actions` that modify build.gradle directly. ([GitHub Marketplace][10])
4. **Fastlane approach** -- Fetch latest versionCode from Play Console and increment locally. ([Runway][12])
5. **Manual script** -- Use grep/sed in bash to increment versionCode in build.gradle, expose via `GITHUB_ENV`. ([Pale Blue Apps][13])

**Best practices noted:**
- Use a PAT when checking out code so actions can make changes; check out specific branch to avoid detached HEAD ([Jake Lee][9])
- Consider manual triggers (`workflow_dispatch`) to avoid accidentally uploading broken builds ([Empathy][14])

---

### Search: "r0adkll upload-google-play action status draft changesNotSentForReview parameters"

**Draft status handling is a major pain point** for new apps that haven't been published yet:

- **Issue #39 and #70**: Multiple users report the error "Only releases with status draft may be created on draft app" -- this occurs when the app has never been manually published through the Play Console. The solution is to set `status: draft` in the action configuration. ([Issue #39][15], [Issue #70][16])
- **`changesNotSentForReview` parameter**: Indicates changes won't be reviewed until explicitly sent for review from the Play Console. Default is `false`. ([PR #88][19])
- **Conflicting behavior**: Issue #175 reports that `changesNotSentForReview` must NOT be set in certain situations, causing errors when the parameter is used inappropriately. ([Issue #175][18])
- **Issue #82**: Users encounter "Changes cannot be sent for review automatically. Please set the query parameter changesNotSentForReview to true" -- this typically happens when app metadata is incomplete or the app hasn't completed initial setup in Play Console. ([Issue #82][17])

**Key takeaway for new apps:** You must set `status: draft` until the first manual publish through the Play Console is complete. After that, you can switch to `status: completed` for automatic publishing.

---

### Search: "Fastlane Android 2025 2026 still relevant solo developer GitHub Actions comparison"

**Fastlane remains actively maintained and relevant** in 2025-2026 for Android CI/CD. ([Fastlane GitHub][21])

- **Fastlane + GitHub Actions** is a common combination. ([Runway][12], [Fastlane docs][20])
- **Setup time**: A complete build & upload pipeline can be set up in ~30 minutes using Fastlane + GitHub Actions. ([Runway][12])
- GitHub Actions has "native experience and direct integration within GitHub" which provides a productivity boost. ([Runway][12])

**For solo developers:** Fastlane adds value when you need cross-platform (iOS + Android) or complex multi-step workflows. For a single Android app, the `r0adkll/upload-google-play` action is simpler.

---

### Search: "Google Play Android Publisher API release status values draft completed halted inProgress"

**Official Google Play Developer API release status values** (from the REST API reference):

- **`draft`** -- Upload APKs/AABs that can later be deployed via the Play Console manually. ([Google API][24])
- **`inProgress`** -- Staged rollout; live to a percentage of users. ([Google API][24], [APKs and Tracks][25])
- **`halted`** -- Halted release; no longer available to new users. ([Google API][24])
- **`completed`** -- Fully released version available to all users. ([Google API][24])

---

### Search: "GitHub Actions workflow android multi-branch conditional track deployment single workflow vs separate"

**Two main patterns:**

**Pattern 1: Single workflow with conditionals**
- Use `if` conditions to deploy based on branch name. ([Max Ivanov][28])
- Benefits: DRY, all logic in one place. ([GitHub Community][29])

**Pattern 2: Separate workflow files**
- Benefit: Cleaner trigger conditions, simpler files. ([Ismail Yenigul][31])
- Trade-off: May copy YAML between workflows. ([GitHub Community][29])

**For Android:** LogRocket recommends a single workflow with conditional deployment steps. ([LogRocket][30])

---

### Search: "Android app release notes automation conventional commits Play Store changelog"

- **Conventional Commits** for structured commit messages. ([Droid Unplugged][33])
- **Release Drafter** maintains a draft release with merged PR titles. ([Automated Release Notes for Android][32])
- **`whatsNewDirectory`** in r0adkll action points to locale-specific release notes files. ([r0adkll README][7])

---

### Search: "trunk-based development mobile app Android solo developer feature flags 2025"

**Trunk-based development is gaining traction for mobile**, even for solo developers:

- Solo developers can do direct-to-trunk commits with short-lived feature branches. ([Mirella Batista][39])
- Feature flags enable keeping main branch always releasable. ([Atlassian][37])
- **"Almost Trunk" approach** -- mostly trunk-based but with short-lived release branches for stabilization. ([Tramline][35])
- Mobile-specific constraint: app store reviews mean "continuous deployment" isn't as immediate as web. Trunk-based + release branches is common. ([Tramline][35])

---

### Search: "github.run_number versionCode multiple branches conflict unique version code across workflows"

- `github.run_number` is per-workflow. Single workflow triggered by multiple branches shares the counter. ([Adam Paxton][11])
- Separate workflows risk collisions; use offset approach to avoid. ([Adam Paxton][11])
- **Key insight:** Single workflow with branch conditionals is cleanest for a solo developer.

---

### Search: "r0adkll upload-google-play whatsNewDirectory file naming convention locale structure"

- Files MUST use pattern `whatsnew-<LOCALE>` with BCP 47 format. ([r0adkll README][7])
- Example: `whatsnew/whatsnew-en-US`, `whatsnew/whatsnew-de-DE`

---

### Search: "Google Play Console first app publish requirements before API automated deployment"

**Requirements before automated deployment:**

- Store listing must be complete (metadata, screenshots, contact info, content rating). ([Play Console Help][40])
- Must configure Play App Signing. ([Play Console Help][42])
- New developer accounts (after Nov 2023) must meet closed testing requirements. ([Play Console Help][40])
- Enable Google Play Developer API, create service account, grant publishing permissions. ([Staffbase][41])
- **First release MUST be manual** through Play Console. ([Issue #39][15], [Issue #70][16])
- versionCode max: 2,100,000,000. ([Play Console Help][42])

---

### Search: "fastlane supply vs r0adkll upload-google-play comparison features Android"

**Fastlane `supply`:** Full Play Store listing management (metadata, screenshots, changelogs, binaries). Ruby-based. ([fastlane docs][43], [fastlane docs][44])

**`r0adkll/upload-google-play`:** Focused on binary upload and release management. GitHub Actions native. Supports in-app update priority, user fraction, mapping files, debug symbols. ([r0adkll README][7])

**Key difference:** Fastlane manages the full listing; r0adkll focuses on binary upload. For a solo developer managing metadata via Play Console UI, r0adkll is sufficient.

---

### Search: "android gradle versionCode from environment variable CI build github actions"

**How to inject versionCode from CI into Gradle:**

**Approach 1: Gradle project property** -- `./gradlew assembleRelease -PBUILD_NUMBER=${{ github.run_number }}` ([Codemagic example][46])

**Approach 2: System env var** -- `System.getenv("BUILD_NUMBER")` in build.gradle ([w3tutorials][48])

**Approach 3: Gradle plugin** -- `ReactiveCircus/app-versioning` generates from Git tags. ([ReactiveCircus][47])

Best for simplicity: Approach 1 or 2.

---

### Search: "android versionName management CI/CD semantic versioning git tag gradle best practice"

**versionName approaches:**

- **Gradle plugins from Git tags**: `gladed/gradle-android-git-version` and `ReactiveCircus/app-versioning` both derive versionName from Git tags (e.g., tag `1.2.3` produces versionName `1.2.3`). ([gladed][49], [ReactiveCircus][47])
- **Semantic versioning convention**: MAJOR.MINOR.PATCH format. versionCode should have a derivable relationship to versionName. ([Dipien][50])
- **Best practice**: Keep version in Git tags, not in files. Avoids "version bump" commits. ([Git-based versioning][51])
- **CI/CD consideration**: In CI, you may need `git fetch --depth=100 --tags` to ensure shallow clones can find recent tags. ([ReactiveCircus][47])
- **versionName for display, versionCode for Play Store ordering**. Users see versionName; Play Store uses versionCode for update logic. ([Dipien][50])

---

### Search: "Google Play new developer account closed testing requirement 20 testers 14 days before production 2024 2025"

**New developer account testing requirements:**

- **Original requirement (Nov 2023)**: 20 testers for 14 consecutive days. ([Play Console Help][52])
- **Revised (Dec 2024)**: Reduced to **12 testers** for 14 consecutive days. ([Play Console Community][53])
- **Applies to**: Personal developer accounts created after November 13, 2023. Organization accounts and older accounts are exempt. ([Play Console Help][52])
- The testers must be opted-in to a closed test track and actively testing. ([RevenueCat][54])
- After satisfying this requirement, the developer can request production access. ([Play Console Help][52])
