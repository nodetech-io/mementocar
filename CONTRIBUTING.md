# Contributing

Bug reports, translations and focused pull requests are welcome. For anything larger than a small fix, open an issue first.

Memento Car has no accounts, ads or tracking, and data stays on the device by default. Network features are only considered if they are optional and do not depend on a server run by the project; discuss them in an issue first.

## Pull requests

- One topic per pull request, with tests for behaviour changes.
- User-visible text in Spanish (`values`), English (`values-en`) and Catalan (`values-ca`). If you cannot translate a string, say so.
- Database schema changes need a Room migration and the exported schema in `app/schemas/`.
- New files start with the SPDX header used in existing files.
- Add user-visible changes under `[Unreleased]` in CHANGELOG.md.

Contributions are accepted under the project license, GPL-3.0-or-later.

Before opening a pull request, run these checks (setup in the [README](README.md#build)):

```sh
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Changes to the UI, database or reminders should also pass `connectedDebugAndroidTest` on an emulator.
