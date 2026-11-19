# Memento Car — Privacy Policy

Last updated: November 19, 2026

## Who provides the app

Memento Car is provided by Prodlabs22, S.L., trading as Nodetech. Privacy contact: [info@nodetech.io](mailto:info@nodetech.io).

## What the app does

Memento Car helps you create local reminders for actions related to your car. The app can show reminders when your phone detects the configured car Bluetooth connection or disconnection.

## Data stored on your device

Memento Car stores the following information locally on your device:

- Cars you configure in the app.
- Reminder text, trigger, recurrence, delay, and critical-mode settings.
- Hashed Bluetooth identifiers for cars associated with Bluetooth devices.
- Local diagnostic status used to explain whether automatic reminders are ready or why a reminder was blocked.

We do not operate a server that receives your stored reminders, car settings, Bluetooth identifiers or local reminder diagnostics. Optional voice features use the services described below.

Memento Car excludes its local app data from Android cloud backup and device-to-device transfer.

If you choose **Export backup**, Memento Car writes a JSON file containing your active cars, hashed Bluetooth identifiers, reminders, and notification-delay setting to the document location you select. The file is written only when you request an export. If you choose a cloud storage provider, that provider may upload and store it under its own terms and privacy policy. You control the destination and access to the exported file. Importing a backup replaces the current car and reminder configuration; diagnostics, alert history, and pending alarms are not included.

## Location

Memento Car does not request location permissions and does not use location data.

## Bluetooth

Memento Car uses Bluetooth permissions to detect configured car Bluetooth connection and disconnection events. To remember which Bluetooth device belongs to your car, Memento Car stores a transformed identifier (a hash) rather than the original Bluetooth address.

## Microphone and voice recognition

If you use dictation, Memento Car uses microphone access to capture your voice command. Speech recognition may be handled by Android or by the speech-recognition service available on the device. Memento Car uses the recognized text to fill or create a reminder.

Memento Car does not save audio recordings or a separate history of voice commands. Recognized text is used in the reminder draft, which may be preserved while you edit it or when Android restores the screen. When you confirm and save, the reminder text is stored locally just like a reminder you type.

Voice recognition is not guaranteed to run offline: the selected speech service may send audio to its servers under its own terms and privacy policy. Spoken confirmations also pass the reminder text and contextual details, such as the selected car and trigger, to the device's text-to-speech service. That service may use network processing depending on its engine and voice. You can create reminders by typing without using voice input.

## Notifications and reboot recovery

Memento Car uses notification permission to show reminders. It also uses permission to run after your device restarts so it can recover pending reminders.

## Links

Settings › About links to the source code, this privacy policy and Nodetech's website. Links open your browser only when tapped and include no reminder content or app user identifier; the Nodetech link adds campaign parameters identifying Memento Car as the source. Each website's own privacy policy applies when you visit it.

## Purchases

Memento Car is free and has no purchases, subscriptions or payment SDK.

## Sharing and sale of data

Memento Car does not sell personal data or display advertising. We do not use your reminders to build advertising profiles.

The voice services described in this policy may communicate with their providers. Local reminder storage does not mean voice features always work offline.

Google Play handles app distribution, and the device's speech recognition and text-to-speech providers may process data to deliver voice features. Their processing and retention are governed by their respective policies. Review [Google's privacy policy](https://policies.google.com/privacy) and the provider settings on your device.

## Data retention and deletion

Cars and reminders you save remain on your device until you delete them, replace them by importing a backup, clear app storage or uninstall the app.

You can delete cars and reminders inside the app. You can also remove app data from Android system settings by clearing storage or uninstalling the app. Backup files you explicitly exported remain in the document location you selected until you delete them with Android's document or file manager. Deleting local app data does not delete data already processed by speech or cloud document providers; consult those providers' policies and controls for retention and deletion.

## Children

Memento Car is not directed to children.

## Contact

For questions about privacy or how Memento Car handles your data, contact [info@nodetech.io](mailto:info@nodetech.io).

## Changes

This policy may be updated when app behavior changes. The updated date at the top of this page will change when the policy is updated.
