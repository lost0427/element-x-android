# Fork releases

Pushing a new `v*` tag to `lost0427/element-x-android` builds signed FDroid APKs
and publishes a GitHub prerelease with SHA-256 checksums. Pushing `develop`
alone runs the existing branch CI without publishing a release.

The fork uses `io.element.android.x.lost0427`, so it can coexist with the
official app. FDroid builds support UnifiedPush without Firebase push.
The version name follows the tag; version codes increase with the release
workflow run number. Keep the workflow filename and signing key stable so
future builds can update existing installations. Rerunning the same workflow
keeps its version code. Each new release should use a new tag.

## Signing

Configure these repository Actions secrets:

- `FORK_KEYSTORE_BASE64`: Base64-encoded PKCS12 keystore.
- `FORK_KEYSTORE_PASSWORD`: Keystore password.
- `FORK_KEY_ALIAS`: Signing key alias.
- `FORK_KEY_PASSWORD`: Key password.

Keep an offline backup of the keystore and its passwords outside Git.
The workflow restores the key into the runner temporary directory and deletes
it after the build. APK signatures are verified before publication.

## Publish

Commit and push changes to `develop`, then create and push an annotated tag:

```shell
git tag -a v26.09.4-fork.1 -m "First fork release"
git push origin refs/tags/v26.09.4-fork.1
```

Use a new tag for subsequent releases. Do not move published tags.
Build progress appears under Actions, and APK downloads appear under Releases.
This workflow permits the current nightly Matrix SDK and explicitly publishes
a prerelease; it does not use the upstream stable-release workflow.
