# Memento Car

Android app in Kotlin with Jetpack Compose, Room and Hilt. Read README.md and CONTRIBUTING.md before coding; release checks are in docs/release.md. `domain/` holds pure logic with no Android types and is tested first; `data/` is Room and backups, `platform/` is Bluetooth, alarms and notifications, `ui/` is Compose. Run Gradle with `--max-workers=1`.

No accounts, ads, analytics or tracking, and data stays on the device. Do not add network access without explicit approval. Every user-visible text goes in Spanish, English and Catalan. A Room schema change needs a migration, the exported schema and a migration test. A new permission updates the README table and the privacy policies. The debug copy of `shortcuts.xml` changes with the main one.

Check UI changes in the running app; passing tests do not prove the UI. Docs describe the app as it is now, not session notes or histories. Ask before any push, history rewrite, tag change or publication.
