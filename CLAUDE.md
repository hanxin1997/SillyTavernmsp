# Project-specific correction

- Proxy and e-ink feature changes must remain inside `android-eink`; do not edit `SillyTavern-release` unless the user explicitly authorizes server-side changes in a later request.
- Before adding an `android:` framework attribute, verify its introduced API level; attributes newer than `minSdk` must live in the matching `values-vNN` resource override.
