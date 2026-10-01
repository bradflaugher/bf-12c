# Security

bf-12c is distributed as a signed APK from this repository's GitHub Releases
and on Google Play. The Play build is the `bf-12c.aab` from the same CI run.

## Reporting a vulnerability

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/bradflaugher/bf-12c/security/advisories/new)
rather than opening a public issue.

## Verifying a release

Each release APK is built by GitHub Actions from the `main` branch and
published alongside a `bf-12c.apk.sha256` checksum. Verify a download with:

```sh
sha256sum -c bf-12c.apk.sha256
```

Each APK and bundle also carries a signed build provenance attestation,
which proves it was built by this repository's CI from a specific commit:

```sh
gh attestation verify bf-12c.apk --repo bradflaugher/bf-12c
```

## Design notes

- The app requests no permissions at all. There is no `INTERNET`
  permission, so nothing is sent off the device; there are no ads,
  analytics or accounts.
- Backups are off: `android:allowBackup="false"`, and the data-extraction
  rules exclude the app's preferences (the saved calculator state and settings)
  from both cloud backup and device-to-device transfer, which ignores
  `allowBackup`.
- Paste only accepts text that parses as a number; anything else shows NOT
  A NUMBER and leaves the stack alone.
- The only exported component is the launcher activity.
- CI actions are pinned to commit SHAs, the Gradle distribution is checksum
  pinned, signing secrets only reach builds of `main`, Dependabot keeps
  dependencies and action pins current, and CodeQL scans the Kotlin
  sources, the workflows and the CI scripts.
