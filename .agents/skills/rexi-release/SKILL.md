---
name: rexi-release
description: Releases a new version of Rexi. First it bumps the version through a draft pull request. Once that's merged, it builds and checks the signed APK, tags the release and drafts the GitHub release notes. Use only when someone asks to release a version.
disable-model-invocation: true
argument-hint: "[version, such as 0.1.0]"
---

# Release Rexi

The version comes with the request, such as `0.1.0`. If none was given, ask for it. It's called X.Y.Z below.

A release has two phases, because the version bump reaches `main` through a pull request:

- **Prepare**, when `versionName` in `app/build.gradle.kts` isn't X.Y.Z yet.
- **Publish**, when `main` already has `versionName = "X.Y.Z"` and there's no `vX.Y.Z` tag yet.

If both the version and the tag already exist, X.Y.Z has been released. Say so and stop.

The repository on GitHub is called OWNER/REPO below. Get it with `gh repo view --json nameWithOwner --jq .nameWithOwner`. If there's no GitHub remote yet, say that a repository is needed first, and stop.

Never read `keystore.properties` or any keystore file. Only check that `keystore.properties` exists.

Don't push, open a pull request, push a tag or create a release until the person has seen what will go out and said yes.

Never attach the APK, or any other build, to a GitHub release, and never link to one from the README. Rexi is going to be sold on Google Play, and an APK given away for free keeps working forever.

## Prepare

1. Check that:
   - you're on `main`, the working tree is clean, and `main` matches `origin/main` after `git fetch origin`;
   - X.Y.Z is higher than the current `versionName`. The very first release, when there are no `v*` tags yet, can use any version, because nothing has shipped;
   - there's no `vX.Y.Z` tag, locally or in `git ls-remote --tags origin`.

   If anything fails, say what, and stop.
2. Create the branch `chore/release-X.Y.Z`.
3. In `app/build.gradle.kts`, set `versionName = "X.Y.Z"`, and add 1 to `versionCode` unless this is the first release.
4. Run `./gradlew check`. If it fails, stop and report the failure.
5. Commit with the message `chore(release): X.Y.Z`. The body says: "Bump the version to X.Y.Z (versionCode N)."
6. Show the diff. Once the person agrees, push the branch and open a **draft** pull request titled `chore(release): X.Y.Z`. In its body, list what changed since the last tag (`git log v<previous>..HEAD --oneline`), or say it's the first release.
7. If this is the first release, tell the person that from now on the database schema is fixed: any change to an entity needs a version bump and a migration, as "Database changes" in `AGENTS.md` describes.
8. Stop. Tell the person to merge the pull request, then run this skill again with the same version.

## Publish

1. Check that:
   - you're on `main`, the working tree is clean, and `main` matches `origin/main` after `git fetch origin`;
   - `versionName` is X.Y.Z;
   - `keystore.properties` exists (`test -f keystore.properties`).

   If anything fails, say what, and stop.
2. Run `./gradlew check`, then `./gradlew :app:assembleRelease`.
3. The signed APK is `app/build/outputs/apk/release/app-release.apk`. If only `app-release-unsigned.apk` is there, the build wasn't signed: stop. Confirm the signature with `apksigner verify`, from the newest folder in the Android SDK's `build-tools`.
4. Check that the APK still can't reach the internet: `aapt2 dump permissions` (from the same `build-tools` folder) must not list `android.permission.INTERNET`. If it does, stop: something brought it back, and `ManifestTest` should have caught it.
5. Copy the APK to `app/build/outputs/apk/release/Rexi-X.Y.Z.apk`. It's for the person's own phone and their testers only.
6. Draft the release notes in `app/build/release-notes-X.Y.Z.md`. First read the previous release for format and tone (`gh release view v<previous> --json body --jq .body`), and the commits since it (`git log v<previous>..HEAD`). For the first release, write from the README's "What it does" instead. Write for the people who use the app, not for developers, in short, plain sentences. Never promise medical outcomes. Use these parts, in this order, and leave out any that would be empty:
   - One opening sentence on what this version brings. If the previous version can be upgraded in place, say so, and that medicines and history stay.
   - If any commit since the previous release has a `BREAKING CHANGE:` footer, follow the opening sentence with `## Before you install`, saying plainly what people must do first and what they will lose. Suggest backing up to a file from Settings first.
   - `## Fixed`, `## New` and `## Improved`, each item with a bold lead-in where that helps.
   - `## Known limits`: carry over the previous list, or start from the README's "Limits", and update anything this release changes. Keep the line that links to the disclaimer.
7. Show the notes. Once the person agrees:

   ```sh
   git tag -a vX.Y.Z -m "Rexi X.Y.Z"
   git push origin vX.Y.Z
   gh release create vX.Y.Z --verify-tag --draft --title "Rexi X.Y.Z" \
     --notes-file app/build/release-notes-X.Y.Z.md
   ```

8. Give the link to the draft release, which the person publishes, and the path to the APK.
