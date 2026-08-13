# Project Rules

- Proxy and e-ink feature changes must remain inside `android-eink`; do not edit `SillyTavern-release` unless the user explicitly authorizes server-side changes in a later request.
- Before adding an `android:` framework attribute, verify its introduced API level; attributes newer than `minSdk` must live in the matching `values-vNN` resource override.
- Do not install or download Android, Java, Gradle, ADB, or Docker tooling in this workspace.
- Android compilation and device testing are performed by the user on another machine. Validate Android changes here with static source review, parseable resources, and tests that use already available runtimes only.
- When project work is complete and the available checks have passed, commit and push the changes to the configured Git remote unless the user explicitly asks not to push.
