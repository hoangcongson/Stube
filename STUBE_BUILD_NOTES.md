# Stube build notes

This checkout has been branded as **Stube**.

- Application ID: `com.siro.stube`
- App name: `Stube`
- Version: `1.15.0` / versionCode `11500`
- Launcher icon: supplied `stube.png`, installed for mdpi through xxxhdpi
- In-app updater: disabled until a Stube-owned update manifest/repository is configured
- Existing upstream/internal Java package names are intentionally retained to minimize runtime risk.
- SmartTube receiver labels used by casting are intentionally retained because they refer to the external TV receiver target, not the app's own identity.

Build release APK:

```bash
./gradlew :smarttubetv:assembleStmobileRelease
```

The project also has the APK-copy task which emits a file named like `Stube_1.15.0_universal.apk`.
